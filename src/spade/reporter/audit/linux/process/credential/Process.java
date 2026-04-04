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
package spade.reporter.audit.linux.process.credential;

public class Process{

	private final String pid;
	private final String ppid;
	private final String pgid;
	private final String sid;

	public Process(final String pid, final String ppid, final String pgid, final String sid){
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

	public String getPid(){ return pid; }
	public String getPpid(){ return ppid; }
	public String getPgid(){ return pgid; }
	public String getSid(){ return sid; }

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof Process)) return false;
		final Process other = (Process) obj;
		return this.pid.equals(other.pid)
				&& this.ppid.equals(other.ppid)
				&& this.pgid.equals(other.pgid)
				&& this.sid.equals(other.sid);
	}

	@Override
	public int hashCode(){
		int result = pid.hashCode();
		result = 31 * result + ppid.hashCode();
		result = 31 * result + pgid.hashCode();
		result = 31 * result + sid.hashCode();
		return result;
	}

}
