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
package spade.reporter.audit.linux.event.handler;

import spade.reporter.audit.core.platform.runtime.State;
import spade.reporter.audit.linux.event.Event;
import spade.reporter.audit.linux.platform.process.ID;

public abstract class Context<
    E extends Event,
    S extends State<ID, spade.reporter.audit.linux.platform.process.State, spade.reporter.audit.linux.platform.runtime.ProcessTable>
> extends spade.reporter.audit.core.source.handler.Context<
    E,
    ID,
    spade.reporter.audit.linux.platform.process.State,
    spade.reporter.audit.linux.platform.runtime.ProcessTable,
    S
>{

	public Context(
		final E event,
		final spade.reporter.audit.core.platform.Context<ID, spade.reporter.audit.linux.platform.process.State, spade.reporter.audit.linux.platform.runtime.ProcessTable, S> platformContext
	){
		super(event, platformContext);
	}

}
