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
package spade.reporter.audit.linux.platform.resource;

public abstract class VersionedID extends spade.reporter.audit.core.platform.resource.ID<VersionedID>{

	private static final long STARTING_VERSION = 0L;

	private final Resource resource;
	private final long version;

	public VersionedID(final Resource resource, final long version){
		if(resource == null){
			throw new IllegalArgumentException("resource cannot be NULL");
		}
		this.resource = resource;
		this.version = version;
	}

	public VersionedID(final Resource resource){
		this(resource, STARTING_VERSION);
	}

	public Resource getResource(){
		return resource;
	}

	public long getVersion(){
		return version;
	}

	public abstract VersionedID nextVersion();

	@Override
	public int compareTo(final VersionedID other){
		if(other == null){
			throw new IllegalArgumentException("Cannot compare to NULL");
		}
		final int typeCmp = Integer.compare(
			this.resource.getType().ordinal(),
			other.resource.getType().ordinal()
		);
		if(typeCmp != 0){
			return typeCmp;
		}
		return Long.compare(this.version, other.version);
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof VersionedID)) return false;
		final VersionedID other = (VersionedID) obj;
		return this.resource == other.resource
			&& this.version == other.version;
	}

	@Override
	public int hashCode(){
		return 31 * System.identityHashCode(resource) + Long.hashCode(version);
	}

}
