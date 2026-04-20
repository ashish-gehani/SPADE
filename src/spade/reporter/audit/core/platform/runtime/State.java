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

public abstract class State<
    PTI extends spade.reporter.audit.core.platform.process.ID<PTI>,
    PTS extends spade.reporter.audit.core.platform.process.State<PTI>,
    PT extends ProcessTable<PTI, PTS>,
    RTI extends spade.reporter.audit.core.platform.resource.ID<RTI>,
    RTS extends spade.reporter.audit.core.platform.resource.State<RTI>,
    RT extends ResourceTable<RTI, RTS>
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
