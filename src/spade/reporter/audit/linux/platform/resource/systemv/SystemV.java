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
package spade.reporter.audit.linux.platform.resource.systemv;

import java.util.Objects;

import spade.reporter.audit.linux.platform.resource.Type;
import spade.reporter.audit.linux.platform.resource.Resource;
import spade.reporter.audit.linux.type.credential.GID;
import spade.reporter.audit.linux.type.credential.UID;

public class SystemV extends Resource{

	private final String id;
	private final UID ownerUID;
	private final GID ownerGID;

	protected SystemV(
		final Type type,
		final String id,
		final UID ownerUID,
		final GID ownerGID
	){
		super(type);
		if(id == null){
			throw new IllegalArgumentException("id cannot be NULL");
		}
		if(ownerUID == null){
			throw new IllegalArgumentException("ownerUID cannot be NULL");
		}
		if(ownerGID == null){
			throw new IllegalArgumentException("ownerGID cannot be NULL");
		}
		this.id = id;
		this.ownerUID = ownerUID;
		this.ownerGID = ownerGID;
	}

	protected SystemV(final SystemV other){
		this(other.getType(), other.id, new UID(other.ownerUID), new GID(other.ownerGID));
	}

	public String getId(){
		return id;
	}

	public UID getOwnerUID(){
		return ownerUID;
	}

	public GID getOwnerGID(){
		return ownerGID;
	}

	@Override
	public boolean equals(final Object o){
		if(this == o) return true;
		if(!(o instanceof SystemV)) return false;
		final SystemV other = (SystemV)o;
		return Objects.equals(getType(), other.getType())
			&& Objects.equals(id, other.id)
			&& Objects.equals(ownerUID, other.ownerUID)
			&& Objects.equals(ownerGID, other.ownerGID);
	}

	@Override
	public int hashCode(){
		return Objects.hash(getType(), id, ownerUID, ownerGID);
	}

}
