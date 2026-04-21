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
import spade.reporter.audit.core.util.channel.WriteTimeoutExpired;

public final class Manager<C extends AbstractContext>{

	private final Context managerContext;
	private final Channel<Event<C>> inChannel;
	private final Channel<ProvenanceElement> outChannel;

	private volatile boolean running = false;
	private Thread pumpThread;

	public Manager(
		final VertexGenerator vertexGenerator,
		final EdgeGenerator edgeGenerator,
		final Channel<Event<C>> inChannel,
		final Channel<ProvenanceElement> outChannel
	){
		if(vertexGenerator == null){
			throw new IllegalArgumentException("vertexGenerator cannot be NULL");
		}
		if(edgeGenerator == null){
			throw new IllegalArgumentException("edgeGenerator cannot be NULL");
		}
		if(inChannel == null){
			throw new IllegalArgumentException("inChannel cannot be NULL");
		}
		if(outChannel == null){
			throw new IllegalArgumentException("outChannel cannot be NULL");
		}
		this.managerContext = new Context(vertexGenerator, edgeGenerator);
		this.inChannel = inChannel;
		this.outChannel = outChannel;
	}

	public synchronized void start(final C context){
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

	private void pump(final C context){
		try{
			while(running){
				try{
					handle(context);
					if(inChannel.isClosed()){
						break;
					}
				}catch(final ReadTimeoutExpired e){
					// channel not yet closed, continue waiting
				}catch(final WriteTimeoutExpired e){
					// outChannel full, continue
				}catch(final InterruptedException e){
					Thread.currentThread().interrupt();
					break;
				}
			}
		}finally{
			inChannel.close();
			running = false;
		}
	}

	public List<ProvenanceElement> handle(final C provContext) throws InterruptedException, ReadTimeoutExpired, WriteTimeoutExpired {
		if(provContext == null){
			throw new IllegalArgumentException("provContext cannot be NULL");
		}
		final Event<C> event = inChannel.read();
		if(event == null){
			return Collections.emptyList();
		}
		final List<ProvenanceElement> elements = Collections.unmodifiableList(event.handle(provContext, managerContext));
		for(final ProvenanceElement element : elements){
			outChannel.write(element);
		}
		return elements;
	}

}
