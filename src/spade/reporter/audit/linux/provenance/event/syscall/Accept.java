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
package spade.reporter.audit.linux.provenance.event.syscall;

import spade.reporter.audit.core.provenance.event.ID;
import spade.reporter.audit.linux.provenance.ModelProcess;
import spade.reporter.audit.linux.provenance.ModelResource;
import spade.reporter.audit.linux.provenance.ModelEvent;
import spade.reporter.audit.linux.platform.syscall.Syscall;
import spade.reporter.audit.linux.provenance.event.ResourceAccess;
import spade.reporter.audit.linux.provenance.type.syscall.Operation;

public class Accept extends ResourceAccess{

	public static final Operation OPERATION = Operation.ACCEPT;

	private final Syscall syscall;

	public Accept(final ID id, final ModelEvent modelEvent, final ModelProcess accessor, final ModelResource resource, final Syscall syscall){
		super(id, modelEvent, accessor, resource);
		if(syscall == null){
			throw new IllegalArgumentException("syscall cannot be NULL");
		}
		this.syscall = syscall;
	}

	public Syscall getSyscall(){
		return syscall;
	}

}
