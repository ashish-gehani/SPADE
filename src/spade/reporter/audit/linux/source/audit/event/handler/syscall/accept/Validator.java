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
package spade.reporter.audit.linux.source.audit.event.handler.syscall.accept;

import spade.reporter.audit.core.source.event.handler.EventHandlingException;
import spade.reporter.audit.linux.platform.syscall.NoSuchSyscall;
import spade.reporter.audit.linux.source.audit.event.handler.Context;
import spade.reporter.audit.linux.source.audit.event.record.Record;
import spade.reporter.audit.linux.source.audit.event.record.Sockaddr;
import spade.reporter.audit.linux.source.audit.event.syscall.Event;

public class Validator implements spade.reporter.audit.linux.source.audit.event.handler.syscall.Validator{

	@Override
	public String validate(final Event event, final Context context) throws EventHandlingException{
		try{
			final int syscallNum = event.getSyscallRecord().getSyscallInfo().getSyscall();
			final spade.reporter.audit.linux.platform.syscall.Name name =
				context.getPlatformContext().getSyscallTable().get(syscallNum).name;
			if(name != spade.reporter.audit.linux.platform.syscall.arch.x86_64.Name.ACCEPT
					&& name != spade.reporter.audit.linux.platform.syscall.arch.x86_64.Name.ACCEPT4){
				return "Expected accept or accept4, got: " + name.value();
			}
			int sockaddrCount = 0;
			for(final Record r : event.getRecords()){
				if(r instanceof Sockaddr) sockaddrCount++;
			}
			if(sockaddrCount == 0) return "Missing SOCKADDR record";
			if(sockaddrCount > 1) return "Expected exactly one SOCKADDR record, got: " + sockaddrCount;
			return null;
		}catch(final NoSuchSyscall e){
			return e.getMessage();
		}
	}

}
