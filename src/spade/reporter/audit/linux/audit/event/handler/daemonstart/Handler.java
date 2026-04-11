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
package spade.reporter.audit.linux.audit.event.handler.daemonstart;

import spade.reporter.audit.core.platform.runtime.State;
import spade.reporter.audit.linux.audit.event.DaemonStart;
import spade.reporter.audit.linux.platform.process.ID;

public class Handler<S extends State<ID, spade.reporter.audit.linux.platform.process.State>>
    implements spade.reporter.audit.core.event.handler.Handler<
        DaemonStart,
        ID,
        spade.reporter.audit.linux.platform.process.State,
        S,
        Context<S>
    >{

	@Override
	public void handle(Context<S> context) {
		if(context == null){
			throw new IllegalArgumentException("Context cannot be NULL");
		}
		if(context.getEvent() == null){
			throw new IllegalArgumentException("Event in context cannot be NULL");
		}
	}

}
