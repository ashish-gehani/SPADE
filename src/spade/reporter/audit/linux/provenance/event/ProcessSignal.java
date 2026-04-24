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
import spade.reporter.audit.linux.provenance.ModelProcess;
import spade.reporter.audit.linux.provenance.ModelEvent;

public class ProcessSignal extends Event{

	private final ModelProcess sender;
	private final ModelProcess receiver;

	public ProcessSignal(final ID id, final ModelEvent modelEvent, final ModelProcess sender, final ModelProcess receiver){
		super(Type.PROCESS_SIGNAL, id, modelEvent);
		if(sender == null){
			throw new IllegalArgumentException("sender cannot be NULL");
		}
		if(receiver == null){
			throw new IllegalArgumentException("receiver cannot be NULL");
		}
		this.sender = sender;
		this.receiver = receiver;
	}

	public ModelProcess getSender(){
		return sender;
	}

	public ModelProcess getReceiver(){
		return receiver;
	}
}
