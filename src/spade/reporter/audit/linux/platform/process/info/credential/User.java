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

import spade.reporter.audit.linux.platform.type.credential.UID;

public class User{

	private final UID uid;
	private final UID euid;
	private final UID suid;
	private final UID fsuid;

	public User(final UID uid, final UID euid, final UID suid, final UID fsuid){
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

	public UID getUid(){ return uid; }
	public UID getEuid(){ return euid; }
	public UID getSuid(){ return suid; }
	public UID getFsuid(){ return fsuid; }

	public User(final User other){
		this(
			new UID(other.uid),
			new UID(other.euid),
			new UID(other.suid),
			new UID(other.fsuid)
		);
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof User)) return false;
		final User other = (User) obj;
		return this.uid.getValue() == other.uid.getValue()
				&& this.euid.getValue() == other.euid.getValue()
				&& this.suid.getValue() == other.suid.getValue()
				&& this.fsuid.getValue() == other.fsuid.getValue();
	}

	@Override
	public int hashCode(){
		int result = Long.hashCode(uid.getValue());
		result = 31 * result + Long.hashCode(euid.getValue());
		result = 31 * result + Long.hashCode(suid.getValue());
		result = 31 * result + Long.hashCode(fsuid.getValue());
		return result;
	}

}
