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
package spade.reporter.audit.linux.platform;

import spade.reporter.audit.core.platform.ID;
import spade.reporter.audit.core.platform.info.Info;
import spade.reporter.audit.linux.platform.syscall.arch.x86_64.Table;

public class Context
        extends spade.reporter.audit.core.platform.Context<
            spade.reporter.audit.linux.platform.process.VersionedID,
            spade.reporter.audit.linux.platform.process.State,
            spade.reporter.audit.linux.platform.runtime.ProcessTable,
            spade.reporter.audit.linux.platform.resource.ID,
            spade.reporter.audit.linux.platform.resource.State,
            spade.reporter.audit.linux.platform.runtime.ResourceTable,
            spade.reporter.audit.linux.platform.runtime.State
        >
{

    private final Table syscallTable;

	public Context(
        final ID id,
        final Info info,
        final Table syscallTable,
        final spade.reporter.audit.linux.platform.runtime.State runtimeState
    ){
		super(id, info, runtimeState);
        if(syscallTable == null){
            throw new IllegalArgumentException("syscallTable cannot be null");
        }
        this.syscallTable = syscallTable;
	}

	public Table getSyscallTable(){
		return syscallTable;
	}

}
