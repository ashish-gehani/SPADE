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
package spade.reporter.audit.core.provenance;

import java.util.Collections;
import java.util.List;

import spade.reporter.audit.core.provenance.event.Event;
import spade.reporter.audit.core.provenance.type.AbstractContext;
import spade.reporter.audit.core.util.channel.Channel;
import spade.reporter.audit.core.util.channel.ReadTimeoutExpired;

public final class Manager{

	private final ManagerContext managerContext;
	private final Channel<Event> channel;

	private volatile boolean running = false;
	private Thread pumpThread;

	public Manager(
		final VertexGenerator vertexGenerator,
		final EdgeGenerator edgeGenerator,
		final Channel<Event> channel
	){
		if(vertexGenerator == null){
			throw new IllegalArgumentException("vertexGenerator cannot be NULL");
		}
		if(edgeGenerator == null){
			throw new IllegalArgumentException("edgeGenerator cannot be NULL");
		}
		if(channel == null){
			throw new IllegalArgumentException("channel cannot be NULL");
		}
		this.managerContext = new ManagerContext(vertexGenerator, edgeGenerator);
		this.channel = channel;
	}

	public synchronized void start(final AbstractContext context){
		if(running){
			throw new IllegalStateException("Already running");
		}
		if(context == null){
			throw new IllegalArgumentException("context cannot be NULL");
		}
		running = true;
		pumpThread = new Thread(() -> pump(context), "manager-pump");
		pumpThread.setDaemon(true);
		pumpThread.start();
	}

	public synchronized void stop(){
		running = false;
		if(pumpThread != null){
			pumpThread.interrupt();
			pumpThread = null;
		}
	}

	public boolean isRunning(){
		return running;
	}

	private void pump(final AbstractContext context){
		try{
			while(running){
				try{
					handle(context);
					if(channel.isClosed()){
						break;
					}
				}catch(final ReadTimeoutExpired e){
					// channel not yet closed, continue waiting
				}catch(final InterruptedException e){
					Thread.currentThread().interrupt();
					break;
				}
			}
		}finally{
			channel.close();
			running = false;
		}
	}

	public List<ProvenanceElement> handle(final AbstractContext context) throws InterruptedException, ReadTimeoutExpired {
		if(context == null){
			throw new IllegalArgumentException("context cannot be NULL");
		}
		final Event event = channel.read();
		if(event == null){
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(event.handle(context, managerContext));
	}

}
