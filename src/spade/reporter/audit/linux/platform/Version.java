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
package spade.reporter.audit.linux.platform;

public final class Version implements Comparable<Version>{

	public final int major;
	public final int minor;
	public final int patch;

	public Version(final int major, final int minor, final int patch){
		this.major = major;
		this.minor = minor;
		this.patch = patch;
	}

	@Override
	public int compareTo(final Version other){
		int cmp = Integer.compare(this.major, other.major);
		if(cmp != 0) return cmp;
		cmp = Integer.compare(this.minor, other.minor);
		if(cmp != 0) return cmp;
		return Integer.compare(this.patch, other.patch);
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof Version)) return false;
		final Version other = (Version) obj;
		return this.major == other.major
			&& this.minor == other.minor
			&& this.patch == other.patch;
	}

	@Override
	public int hashCode(){
		int result = Integer.hashCode(major);
		result = 31 * result + Integer.hashCode(minor);
		result = 31 * result + Integer.hashCode(patch);
		return result;
	}

	@Override
	public String toString(){
		return major + "." + minor + "." + patch;
	}

}
