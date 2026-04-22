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
package spade.reporter.audit.linux.provenance.event.type.process.signal;

import spade.reporter.audit.core.provenance.event.ID;
import spade.reporter.audit.linux.provenance.ProvEvent;
import spade.reporter.audit.linux.provenance.ProvProcess;
import spade.reporter.audit.linux.provenance.event.type.ProcessType;

public class Event extends spade.reporter.audit.linux.provenance.event.Event{

	private final ProvProcess sender;
	private final ProvProcess receiver;

	public Event(final ID id, final ProvEvent provEvent, final ProvProcess sender, final ProvProcess receiver){
		super(ProcessType.SIGNAL, id, provEvent);
		if(sender == null){
			throw new IllegalArgumentException("sender cannot be NULL");
		}
		if(receiver == null){
			throw new IllegalArgumentException("receiver cannot be NULL");
		}
		this.sender = sender;
		this.receiver = receiver;
	}

	public ProvProcess getSender(){
		return sender;
	}

	public ProvProcess getReceiver(){
		return receiver;
	}
}
