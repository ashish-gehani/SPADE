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
package spade.reporter.audit.linux.source.audit.event.record.helper;

import java.util.Map;

import spade.reporter.audit.linux.source.audit.event.record.MalformedRecordException;
import spade.reporter.audit.linux.type.credential.GID;
import spade.reporter.audit.linux.type.credential.PID;
import spade.reporter.audit.linux.type.credential.UID;

/**
 * Holds process identity fields common across multiple audit record types.
 */
public final class ProcessInfo{

	private final PID pid;
	private final PID ppid;
	private final UID uid;
	private final UID euid;
	private final UID suid;
	private final UID fsuid;
	private final GID gid;
	private final GID egid;
	private final GID sgid;
	private final GID fsgid;
	private final String comm;
	private final String exe;

	public ProcessInfo(final PID pid, final PID ppid,
			final UID uid, final UID euid, final UID suid, final UID fsuid,
			final GID gid, final GID egid, final GID sgid, final GID fsgid,
			final String comm, final String exe){
		this.pid = pid;
		this.ppid = ppid;
		this.uid = uid;
		this.euid = euid;
		this.suid = suid;
		this.fsuid = fsuid;
		this.gid = gid;
		this.egid = egid;
		this.sgid = sgid;
		this.fsgid = fsgid;
		this.comm = comm;
		this.exe = exe;
	}

	public static ProcessInfo parse(
			final String pid, final String ppid,
			final String uid, final String euid, final String suid, final String fsuid,
			final String gid, final String egid, final String sgid, final String fsgid,
			final String comm, final String exe){
		return new ProcessInfo(
			new PID(Long.parseLong(pid)),
			new PID(Long.parseLong(ppid)),
			new UID(Long.parseLong(uid)),
			new UID(Long.parseLong(euid)),
			new UID(Long.parseLong(suid)),
			new UID(Long.parseLong(fsuid)),
			new GID(Long.parseLong(gid)),
			new GID(Long.parseLong(egid)),
			new GID(Long.parseLong(sgid)),
			new GID(Long.parseLong(fsgid)),
			comm,
			exe
		);
	}

	public static ProcessInfo parse(final String processData) throws MalformedRecordException{
		final Map<String, String> map = KeyValueParser.parseKeyValuePairs(processData);
		return parse(
			map.get("pid"), map.get("ppid"),
			map.get("uid"), map.get("euid"), map.get("suid"), map.get("fsuid"),
			map.get("gid"), map.get("egid"), map.get("sgid"), map.get("fsgid"),
			AuditStringParser.mustParse(processData, "comm"),
			map.get("exe")
		);
	}

	public PID getPid(){ return pid; }
	public PID getPpid(){ return ppid; }
	public UID getUid(){ return uid; }
	public UID getEuid(){ return euid; }
	public UID getSuid(){ return suid; }
	public UID getFsuid(){ return fsuid; }
	public GID getGid(){ return gid; }
	public GID getEgid(){ return egid; }
	public GID getSgid(){ return sgid; }
	public GID getFsgid(){ return fsgid; }
	public String getComm(){ return comm; }
	public String getExe() { return exe; }
}
