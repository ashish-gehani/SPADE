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
package spade.reporter.audit.core.platform.runtime;

import spade.reporter.audit.core.util.statetable.Indexable;

public abstract class State<
    PTI extends Indexable<PTI>,                                                                      // PT=ProcessTable, I=Indexable (the indexable key type of the process table)
    PTS extends spade.reporter.audit.core.util.statetable.State<PTI>,                                // PT=ProcessTable, S=State (the statetable entry state for the process table)
    PT extends ProcessTable<PTI, PTS>,                                                               // Process table
    RTI extends Indexable<RTI>,                                                                      // RT=ResourceTable, I=Indexable (the indexable key type of the resource table)
    RTS extends spade.reporter.audit.core.util.statetable.State<RTI>,                                // RT=ResourceTable, S=State (the statetable entry state for the resource table)
    RT extends ResourceTable<RTI, RTS>                                                               // Resource table
> {

	private final PT processTable;
	private final RT resourceTable;

	public State(final PT processTable, final RT resourceTable){
		if(processTable == null){
			throw new IllegalArgumentException("processTable cannot be NULL");
		}
		if(resourceTable == null){
			throw new IllegalArgumentException("resourceTable cannot be NULL");
		}
		this.processTable = processTable;
		this.resourceTable = resourceTable;
	}

	public PT getProcessTable(){
		return processTable;
	}

	public RT getResourceTable(){
		return resourceTable;
	}

}
