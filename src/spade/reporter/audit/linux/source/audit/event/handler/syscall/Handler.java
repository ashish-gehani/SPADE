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


import java.util.List;

import spade.reporter.audit.core.source.event.handler.EventHandlingException;
import spade.reporter.audit.linux.platform.syscall.Table;
import spade.reporter.audit.linux.source.audit.event.ID;
import spade.reporter.audit.linux.source.audit.event.handler.Context;
import spade.reporter.audit.linux.source.audit.event.record.Syscall;
import spade.reporter.audit.linux.source.audit.event.record.helper.SyscallInfo;
import spade.reporter.audit.linux.source.audit.event.syscall.Event;

public class Handler implements spade.reporter.audit.core.source.event.handler.Handler<ID, Event, Context>{

	private final Registry registry = new Registry();

	@Override
	public List<spade.reporter.audit.core.provenance.event.Event> handle(final Event event, final Context context) throws EventHandlingException{
		try{
			return _handle(event, context);
		}catch(Exception e){
			throw new EventHandlingException(event, e);
		}
	}

	public List<spade.reporter.audit.core.provenance.event.Event> _handle(final Event event, final Context context) throws Exception{
		final Syscall syscallRecord = event.getSyscallRecord();
		final SyscallInfo syscallInfo = syscallRecord.getSyscallInfo();
		final int syscallNum = syscallInfo.getSyscall();

		final Table syscallTable = context.getPlatformContext().getSyscallTable();
		final spade.reporter.audit.linux.platform.syscall.Syscall syscallObj = syscallTable.get(syscallNum);

		final Registry.Entry entry = registry.get(syscallObj.name);
		if(entry == null){
			return null;
		}

		final String error = entry.validator.validate(event, context);
		if(error != null){
			throw new Exception("Validation failed for syscall '" + syscallObj.name + "': " + error);
		}

		return entry.handler.handle(event, context);
	}

}
