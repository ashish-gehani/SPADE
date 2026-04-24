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
package spade.reporter.audit.linux.source.audit.event.handler.syscall;

import java.util.HashMap;
import java.util.Map;

import spade.reporter.audit.linux.source.audit.event.ID;
import spade.reporter.audit.linux.source.audit.event.handler.Context;
import spade.reporter.audit.linux.source.audit.event.syscall.Event;

public class Registry{

	public static final class Entry{
		public final Validator validator;
		public final spade.reporter.audit.core.source.event.handler.Handler<ID, Event, Context> handler;

		public Entry(
			final Validator validator,
			final spade.reporter.audit.core.source.event.handler.Handler<ID, Event, Context> handler
		){
			this.validator = validator;
			this.handler = handler;
		}
	}

	private final Map<String, Entry> syscallHandlers = new HashMap<>();

	public Registry(){
		final spade.reporter.audit.linux.source.audit.event.handler.syscall.accept.Validator acceptValidator =
			new spade.reporter.audit.linux.source.audit.event.handler.syscall.accept.Validator();
		final spade.reporter.audit.linux.source.audit.event.handler.syscall.accept.Handler acceptHandler =
			new spade.reporter.audit.linux.source.audit.event.handler.syscall.accept.Handler();
		final Entry acceptEntry = new Entry(acceptValidator, acceptHandler);
		syscallHandlers.put("accept",  acceptEntry);
		syscallHandlers.put("accept4", acceptEntry);
	}

	public Entry get(final String syscallName){
		return syscallHandlers.get(syscallName);
	}

}
