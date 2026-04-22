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
package spade.reporter.audit.linux.audit.event.record.helper;

/**
 * Holds process identity fields common across multiple audit record types.
 */
public final class ProcessInfo{

	public final String pid;
	public final String ppid;
	public final String uid;
	public final String euid;
	public final String suid;
	public final String fsuid;
	public final String gid;
	public final String egid;
	public final String sgid;
	public final String fsgid;
	public final String comm;

	public ProcessInfo(final String pid, final String ppid,
			final String uid, final String euid, final String suid, final String fsuid,
			final String gid, final String egid, final String sgid, final String fsgid,
			final String comm){
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
	}
}
