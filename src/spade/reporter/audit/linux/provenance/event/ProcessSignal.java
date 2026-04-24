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
package spade.reporter.audit.linux.provenance.event;

import spade.reporter.audit.core.provenance.event.ID;
import spade.reporter.audit.linux.provenance.PlatformProcess;
import spade.reporter.audit.linux.provenance.SourceEvent;

public class ProcessSignal extends Event{

	private final PlatformProcess sender;
	private final PlatformProcess receiver;

	public ProcessSignal(final ID id, final SourceEvent sourceEvent, final PlatformProcess sender, final PlatformProcess receiver){
		super(Type.PROCESS_SIGNAL, id, sourceEvent);
		if(sender == null){
			throw new IllegalArgumentException("sender cannot be NULL");
		}
		if(receiver == null){
			throw new IllegalArgumentException("receiver cannot be NULL");
		}
		this.sender = sender;
		this.receiver = receiver;
	}

	public PlatformProcess getSender(){
		return sender;
	}

	public PlatformProcess getReceiver(){
		return receiver;
	}
}
