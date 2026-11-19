/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */
package spade.reporter.audit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Puts the creation of a vfork child before the child's own events.
 *
 * vfork, and clone with CLONE_VFORK, suspend the parent until the child calls execve or exits,
 * and the parent's syscall is logged when it returns, with the kernel module's record of the
 * child: so the child's execve, and what it did before, reach SPADE first, from a process the
 * reporter does not know yet. Handled in that order, the child is first seen in its execve, with
 * unknown namespaces (-1), and the vfork then creates a second vertex for it, apart from the
 * first. The container methods leave out versions with unknown namespaces, so such a process drops
 * out of its container, and a container init created so is a start that never reaches execve.
 *
 * Events of a process the reporter does not know, whose parent it knows and saw start in the
 * trace, are therefore held, as are that process's later events, until the event that creates it
 * has been handled; then they follow, in their order. Events held longer than the window (in audit
 * time) or beyond the limit follow in their order without it. All other events pass at once.
 */
public class VforkOrdering{

	private final Predicate<String> isKnown;
	private final Predicate<String> isStartedInTrace;
	/** The pid of the process an event creates, or null if it creates none. */
	private final Function<Map<String, String>, String> createdPid;
	private final long windowMillis;
	private final int maxHeld;

	private static final class Held{
		final double firstTime;
		final List<Map<String, String>> events = new ArrayList<Map<String, String>>();
		Held(final double firstTime){
			this.firstTime = firstTime;
		}
	}

	/** pid -> its held events, in the order the first of each arrived */
	private final LinkedHashMap<String, Held> held = new LinkedHashMap<String, Held>();
	/** pids given up on: their events pass, so as not to hold them again before they are known */
	private final Set<String> released = new HashSet<String>();
	private int heldCount = 0;
	private long heldTotal = 0, releasedByCreation = 0, releasedByWindow = 0;

	public VforkOrdering(final Predicate<String> isKnown, final Predicate<String> isStartedInTrace,
			final Function<Map<String, String>, String> createdPid,
			final long windowMillis, final int maxHeld){
		this.isKnown = isKnown;
		this.isStartedInTrace = isStartedInTrace;
		this.createdPid = createdPid;
		this.windowMillis = windowMillis;
		this.maxHeld = maxHeld;
	}

	/** Passes the event to the sink, now or once the process it belongs to is created. */
	public void accept(final Map<String, String> event, final Consumer<Map<String, String>> sink){
		releaseExpired(time(event), sink);

		final String pid = event.get(AuditEventReader.PID);
		if(pid != null){
			final Held h = held.get(pid);
			if(h != null){
				hold(h, event);
				return;
			}
			if(shouldHold(event, pid)){
				final Held n = new Held(time(event));
				held.put(pid, n);
				hold(n, event);
				return;
			}
		}

		sink.accept(event);
		if(pid != null && isKnown.test(pid)){
			released.remove(pid);
		}

		final String child = createdPid.apply(event);
		if(child != null){
			final Held h = held.remove(child);
			if(h != null){
				releasedByCreation++;
				heldCount -= h.events.size();
				for(final Map<String, String> e : h.events){
					accept(e, sink); // a held event may create a held process in turn
				}
			}
		}
	}

	/** Passes every held event, in the order the processes' first events arrived. */
	public void flush(final Consumer<Map<String, String>> sink){
		while(!held.isEmpty()){
			releaseFirst(sink);
		}
	}

	public long getHeldTotal(){ return heldTotal; }
	public long getReleasedByCreation(){ return releasedByCreation; }
	public long getReleasedByWindow(){ return releasedByWindow; }

	private boolean shouldHold(final Map<String, String> event, final String pid){
		if(released.contains(pid) || isKnown.test(pid)){
			return false;
		}
		if(event.get(AuditEventReader.SYSCALL) == null){ // only a syscall starts holding
			return false;
		}
		final String ppid = event.get(AuditEventReader.PPID);
		if(ppid == null){
			return false;
		}
		// a child of a held process waits for its parent's creation, and then for its own
		return held.containsKey(ppid) || (isKnown.test(ppid) && isStartedInTrace.test(ppid));
	}

	private void hold(final Held h, final Map<String, String> event){
		h.events.add(event);
		heldCount++;
		heldTotal++;
	}

	private void releaseExpired(final double now, final Consumer<Map<String, String>> sink){
		while(!held.isEmpty()){
			final Held first = held.values().iterator().next();
			final boolean expired = !Double.isNaN(now) && !Double.isNaN(first.firstTime)
					&& (now - first.firstTime) * 1000.0 > windowMillis;
			if(expired || heldCount > maxHeld){
				releaseFirst(sink);
			}else{
				break;
			}
		}
	}

	private void releaseFirst(final Consumer<Map<String, String>> sink){
		final Iterator<Map.Entry<String, Held>> it = held.entrySet().iterator();
		final Map.Entry<String, Held> first = it.next();
		it.remove();
		releasedByWindow++;
		heldCount -= first.getValue().events.size();
		released.add(first.getKey());
		for(final Map<String, String> e : first.getValue().events){
			accept(e, sink);
		}
	}

	private static double time(final Map<String, String> event){
		try{
			final String t = event.get(AuditEventReader.TIME);
			return t == null ? Double.NaN : Double.parseDouble(t);
		}catch(NumberFormatException e){
			return Double.NaN;
		}
	}
}
