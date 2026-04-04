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

public class Group{

	private final String gid;
	private final String egid;
	private final String sgid;
	private final String fsgid;

	public Group(final String gid, final String egid, final String sgid, final String fsgid){
		if(gid == null){
			throw new IllegalArgumentException("gid cannot be NULL");
		}
		if(egid == null){
			throw new IllegalArgumentException("egid cannot be NULL");
		}
		if(sgid == null){
			throw new IllegalArgumentException("sgid cannot be NULL");
		}
		if(fsgid == null){
			throw new IllegalArgumentException("fsgid cannot be NULL");
		}
		this.gid = gid;
		this.egid = egid;
		this.sgid = sgid;
		this.fsgid = fsgid;
	}

	public String getGid(){ return gid; }
	public String getEgid(){ return egid; }
	public String getSgid(){ return sgid; }
	public String getFsgid(){ return fsgid; }

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof Group)) return false;
		final Group other = (Group) obj;
		return this.gid.equals(other.gid)
				&& this.egid.equals(other.egid)
				&& this.sgid.equals(other.sgid)
				&& this.fsgid.equals(other.fsgid);
	}

	@Override
	public int hashCode(){
		int result = gid.hashCode();
		result = 31 * result + egid.hashCode();
		result = 31 * result + sgid.hashCode();
		result = 31 * result + fsgid.hashCode();
		return result;
	}

}
