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

public class User{

	private final String uid;
	private final String euid;
	private final String suid;
	private final String fsuid;

	public User(final String uid, final String euid, final String suid, final String fsuid){
		if(uid == null){
			throw new IllegalArgumentException("uid cannot be NULL");
		}
		if(euid == null){
			throw new IllegalArgumentException("euid cannot be NULL");
		}
		if(suid == null){
			throw new IllegalArgumentException("suid cannot be NULL");
		}
		if(fsuid == null){
			throw new IllegalArgumentException("fsuid cannot be NULL");
		}
		this.uid = uid;
		this.euid = euid;
		this.suid = suid;
		this.fsuid = fsuid;
	}

	public String getUid(){ return uid; }
	public String getEuid(){ return euid; }
	public String getSuid(){ return suid; }
	public String getFsuid(){ return fsuid; }

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof User)) return false;
		final User other = (User) obj;
		return this.uid.equals(other.uid)
				&& this.euid.equals(other.euid)
				&& this.suid.equals(other.suid)
				&& this.fsuid.equals(other.fsuid);
	}

	@Override
	public int hashCode(){
		int result = uid.hashCode();
		result = 31 * result + euid.hashCode();
		result = 31 * result + suid.hashCode();
		result = 31 * result + fsuid.hashCode();
		return result;
	}

}
