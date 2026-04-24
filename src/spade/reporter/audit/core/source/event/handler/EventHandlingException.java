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
package spade.reporter.audit.core.source.event.handler;

import spade.reporter.audit.core.source.event.Event;
import spade.reporter.audit.core.source.event.IDable;

public class EventHandlingException extends Exception{

	public EventHandlingException(final Event<? extends IDable> event){
		super("Failed to handle event: " + String.valueOf(event.getId()));
	}

	public EventHandlingException(final Event<? extends IDable> event, final Throwable cause){
		super("Failed to handle event: " + String.valueOf(event.getId()), cause);
	}

}
