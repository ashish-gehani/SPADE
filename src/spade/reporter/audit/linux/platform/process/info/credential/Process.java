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
package spade.reporter.audit.linux.platform.process.info.credential;

import spade.reporter.audit.linux.platform.util.credential.PID;

public class Process{

	private final PID pid;
	private final PID ppid;
	private final PID pgid;
	private final PID sid;

	public Process(final PID pid, final PID ppid, final PID pgid, final PID sid){
		if(pid == null){
			throw new IllegalArgumentException("pid cannot be NULL");
		}
		if(ppid == null){
			throw new IllegalArgumentException("ppid cannot be NULL");
		}
		if(pgid == null){
			throw new IllegalArgumentException("pgid cannot be NULL");
		}
		if(sid == null){
			throw new IllegalArgumentException("sid cannot be NULL");
		}
		this.pid = pid;
		this.ppid = ppid;
		this.pgid = pgid;
		this.sid = sid;
	}

	public PID getPid(){ return pid; }
	public PID getPpid(){ return ppid; }
	public PID getPgid(){ return pgid; }
	public PID getSid(){ return sid; }

	public Process(final Process other){
		this(
			new PID(other.pid),
			new PID(other.ppid),
			new PID(other.pgid),
			new PID(other.sid)
		);
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof Process)) return false;
		final Process other = (Process) obj;
		return this.pid.getValue() == other.pid.getValue()
				&& this.ppid.getValue() == other.ppid.getValue()
				&& this.pgid.getValue() == other.pgid.getValue()
				&& this.sid.getValue() == other.sid.getValue();
	}

	@Override
	public int hashCode(){
		int result = Long.hashCode(pid.getValue());
		result = 31 * result + Long.hashCode(ppid.getValue());
		result = 31 * result + Long.hashCode(pgid.getValue());
		result = 31 * result + Long.hashCode(sid.getValue());
		return result;
	}

}
