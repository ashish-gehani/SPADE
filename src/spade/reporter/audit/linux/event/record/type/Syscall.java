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
package spade.reporter.audit.linux.event.record.type;

import java.util.Map;

import spade.reporter.audit.linux.event.ID;
import spade.reporter.audit.linux.event.record.MalformedRecordException;
import spade.reporter.audit.linux.event.record.Record;
import spade.reporter.audit.linux.event.record.Type;
import spade.reporter.audit.linux.event.record.helper.AuditStringParser;
import spade.reporter.audit.linux.event.record.helper.Header;
import spade.reporter.audit.linux.event.record.helper.KeyValueParser;
import spade.reporter.audit.linux.event.record.helper.ProcessInfo;

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

	/** Syscall number. */
	public final String syscall;
	/** Whether the syscall succeeded. */
	public final String success;
	/** Return value / exit code of the syscall. */
	public final String exit;
	/** Syscall argument 0. */
	public final String arg0;
	/** Syscall argument 1. */
	public final String arg1;
	/** Syscall argument 2. */
	public final String arg2;
	/** Syscall argument 3. */
	public final String arg3;
	/** Number of PATH records associated with this syscall. */
	public final String items;
	/** Path of the executable. */
	public final String exe;
	/** Process identity fields. */
	public final ProcessInfo processInfo;

	public Syscall(
		final ID id, final String rawRecord
	) throws MalformedRecordException{
		super(id, Type.SYSCALL, rawRecord);
		final Map<String, String> parsedFields = KeyValueParser.parseKeyValuePairs(rawRecord);
		this.syscall = parsedFields.get("syscall");
		this.success = parsedFields.get("success");
		this.exit = parsedFields.get("exit");
		this.arg0 = parsedFields.get("a0");
		this.arg1 = parsedFields.get("a1");
		this.arg2 = parsedFields.get("a2");
		this.arg3 = parsedFields.get("a3");
		this.items = parsedFields.get("items");
		this.exe = parsedFields.get("exe");
		this.processInfo = new ProcessInfo(
			parsedFields.get("pid"),
			parsedFields.get("ppid"),
			parsedFields.get("uid"),
			parsedFields.get("euid"),
			parsedFields.get("suid"),
			parsedFields.get("fsuid"),
			parsedFields.get("gid"),
			parsedFields.get("egid"),
			parsedFields.get("sgid"),
			parsedFields.get("fsgid"),
			AuditStringParser.mustParse(rawRecord, "comm")
		);
	}

	public String getSyscall(){
		return syscall;
	}

	public String getSuccess(){
		return success;
	}

	public String getExit(){
		return exit;
	}

	public String getArg0(){
		return arg0;
	}

	public String getArg1(){
		return arg1;
	}

	public String getArg2(){
		return arg2;
	}

	public String getArg3(){
		return arg3;
	}

	public String getPid(){
		return processInfo.pid;
	}

	public String getPpid(){
		return processInfo.ppid;
	}

	public String getUid(){
		return processInfo.uid;
	}

	public String getEuid(){
		return processInfo.euid;
	}

	public String getSuid(){
		return processInfo.suid;
	}

	public String getFsuid(){
		return processInfo.fsuid;
	}

	public String getGid(){
		return processInfo.gid;
	}

	public String getEgid(){
		return processInfo.egid;
	}

	public String getSgid(){
		return processInfo.sgid;
	}

	public String getFsgid(){
		return processInfo.fsgid;
	}

	public String getComm(){
		return processInfo.comm;
	}

	public String getExe(){
		return exe;
	}

	public String getItems(){
		return items;
	}

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
