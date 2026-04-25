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
package spade.reporter.audit.linux.platform.resource.fs;

public class VersionedID extends spade.reporter.audit.linux.platform.resource.VersionedID{

	public VersionedID(final Path path, final long version){
		super(path, version);
	}

	public VersionedID(final Path path){
		super(path);
	}

	public VersionedID(final VersionedID other){
		this(other.getPath(), other.getVersion());
	}

	public Path getPath(){
		return (Path) getResource();
	}

	@Override
	public VersionedID nextVersion(){
		return new VersionedID(getPath(), getVersion() + 1);
	}

	@Override
	public int compareTo(final spade.reporter.audit.linux.platform.resource.VersionedID other){
		if(other == null){
			throw new IllegalArgumentException("Cannot compare to NULL");
		}
		if(this == other) return 0;
		if(!(other instanceof VersionedID)){
			return super.compareTo(other);
		}
		final VersionedID o = (VersionedID) other;
		int c = this.getPath().getType().compareTo(o.getPath().getType());
		if(c != 0) return c;
		c = this.getPath().getDevice().getDeviceType().compareTo(o.getPath().getDevice().getDeviceType());
		if(c != 0) return c;
		c = Long.compare(this.getPath().getDevice().getMajor(), o.getPath().getDevice().getMajor());
		if(c != 0) return c;
		c = Long.compare(this.getPath().getDevice().getMinor(), o.getPath().getDevice().getMinor());
		if(c != 0) return c;
		c = Long.compare(this.getPath().getInode().getValue(), o.getPath().getInode().getValue());
		if(c != 0) return c;
		c = this.getPath().getPath().getResolvedPath().compareTo(o.getPath().getPath().getResolvedPath());
		if(c != 0) return c;
		return Long.compare(this.getVersion(), o.getVersion());
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof VersionedID)) return false;
		final VersionedID other = (VersionedID) obj;
		return this.getPath().equals(other.getPath())
			&& this.getVersion() == other.getVersion();
	}

	@Override
	public int hashCode(){
		int result = getPath().hashCode();
		result = 31 * result + Long.hashCode(getVersion());
		return result;
	}

}
