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
package spade.reporter.audit.linux.source.audit.event.record;

import spade.reporter.audit.linux.source.audit.event.ID;
import spade.reporter.audit.linux.source.audit.event.record.helper.Header;
import spade.reporter.audit.linux.source.audit.event.record.helper.SyscallInfo;

/**
 * Record subclass for SYSCALL audit records.
 *
 * Contains process and syscall fields parsed from the audit data.
 *
 * Example raw data:
 *   arch=c000003e syscall=0 success=yes exit=30 a0=6 a1=7fff06b61700 a2=1000 a3=0 items=0
 *   ppid=26414 pid=26415 auid=1000 uid=1002 gid=1002 euid=1002 suid=1002 fsuid=1002
 *   egid=1002 sgid=1002 fsgid=1002 tty=(none) ses=3 comm="screen" exe="/usr/bin/screen" key=(null)
 */
public class Syscall extends Record{

	private final SyscallInfo syscallInfo;

	public Syscall(
		final ID id, final String rawRecord
	) throws MalformedRecordException{
		super(id, Type.SYSCALL, rawRecord);
		this.syscallInfo = SyscallInfo.parse(rawRecord);
	}

	public SyscallInfo getSyscallInfo(){ return syscallInfo; }

	public static class Creator extends Record.Creator{

		@Override
		public String validate(final Header header){
			final Type type = header.getType();
			return type == Type.SYSCALL ? null : "Expected SYSCALL, got: " + type;
		}

		@Override
		public Record create(final Header header) throws MalformedRecordException{
			final String error = validate(header);
			if(error != null) throw new MalformedRecordException(error, header.getRawLine());
			return new Syscall(header.getId(), header.getRawLine());
		}
	}
}
