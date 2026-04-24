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
package spade.reporter.audit.linux.platform.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import spade.reporter.audit.linux.platform.process.State;
import spade.reporter.audit.linux.platform.process.VersionedID;
import spade.reporter.audit.linux.type.credential.PID;

/**
 * Tracks versioned process states, maintaining the highest-seen version per PID.
 */
public class ProcessTable extends spade.reporter.audit.core.platform.runtime.ProcessTable<VersionedID, State>{

	/** All versions stored per PID, sorted ascending; last() is the max. */
	private final Map<PID, TreeSet<Long>> versions = new HashMap<>();

	@Override
	public void put(final VersionedID id, final State state){
		super.put(id, state);
		versions.computeIfAbsent(id.getPid(), k -> new TreeSet<>()).add(id.getVersion());
	}

	@Override
	public State remove(final VersionedID id){
		final State removed = super.remove(id);
		if(removed != null){
			final TreeSet<Long> vset = versions.get(id.getPid());
			if(vset != null){
				vset.remove(id.getVersion());
				if(vset.isEmpty()){
					versions.remove(id.getPid());
				}
			}
		}
		return removed;
	}

	@Override
	public void clear(){
		super.clear();
		versions.clear();
	}

	/**
	 * Returns the state for pid at its highest version, or null if pid is not tracked.
	 */
	public State getLatest(final PID pid){
		final Long maxVersion = getMaxVersion(pid);
		if(maxVersion == null){
			return null;
		}
		return get(new VersionedID(pid, maxVersion));
	}

	/**
	 * Returns the highest version currently stored for pid, or null if none.
	 */
	public Long getMaxVersion(final PID pid){
		final TreeSet<Long> vset = versions.get(pid);
		return vset != null ? vset.last() : null;
	}

	/**
	 * Removes all versions of pid except the highest, freeing stale state.
	 */
	public void garbageCollect(final PID pid){
		if(pid == null){
			throw new IllegalArgumentException("pid cannot be NULL");
		}
		final TreeSet<Long> vset = versions.get(pid);
		if(vset == null || vset.isEmpty()){
			return;
		}
		final List<Long> stale = new ArrayList<>(vset.headSet(vset.last()));
		for(final long version : stale){
			remove(new VersionedID(pid, version));
		}
	}

}
