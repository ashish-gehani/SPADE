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
package spade.reporter.audit.core.source.handler;

import spade.reporter.audit.core.platform.runtime.ResourceTable;
import spade.reporter.audit.core.platform.runtime.State;
import spade.reporter.audit.core.source.Event;
import spade.reporter.audit.core.util.statetable.Indexable;

public abstract class Context<
    E extends Event,

    PTI extends Indexable<PTI>,
    PTS extends spade.reporter.audit.core.util.statetable.State<PTI>,
    PT extends spade.reporter.audit.core.platform.runtime.ProcessTable<PTI, PTS>,

    RTI extends Indexable<RTI>,
    RTS extends spade.reporter.audit.core.util.statetable.State<RTI>,
    RT extends ResourceTable<RTI, RTS>,

	PRS extends State<PTI, PTS, PT, RTI, RTS, RT>
>{

	private final E event;
	private final spade.reporter.audit.core.platform.Context<PTI, PTS, PT, RTI, RTS, RT, PRS> platformContext;

	public Context(final E event, final spade.reporter.audit.core.platform.Context<PTI, PTS, PT, RS> platformContext){
		this.event = event;
		if(platformContext == null){
			throw new IllegalArgumentException("platformContext cannot be NULL");
		}
		this.platformContext = platformContext;
	}

	public E getEvent(){
		return event;
	}

	public spade.reporter.audit.core.platform.Context<PTI, PTS, PT, RS> getPlatformContext(){
		return platformContext;
	}

}
