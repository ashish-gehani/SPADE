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
package spade.reporter.audit.core.platform.info;

/**
 * Immutable OS version (major.minor.patch).
 *
 * Parse with {@link #parse(String)} from a dot-separated string such as
 * "22.04.1" or "10.15.7".  The patch component defaults to 0 when absent.
 */
public final class Version implements Comparable<Version>{

	private final int major;
	private final int minor;
	private final int patch;

	public Version(final int major, final int minor, final int patch){
		if(major < 0) throw new IllegalArgumentException("major cannot be negative");
		if(minor < 0) throw new IllegalArgumentException("minor cannot be negative");
		if(patch < 0) throw new IllegalArgumentException("patch cannot be negative");
		this.major = major;
		this.minor = minor;
		this.patch = patch;
	}

	public static Version parse(final String version){
		if(version == null){
			throw new IllegalArgumentException("version cannot be NULL");
		}
		final String[] parts = version.split("\\.", -1);
		if(parts.length < 2 || parts.length > 3){
			throw new IllegalArgumentException(
				"version must be in major.minor or major.minor.patch format: " + version
			);
		}
		try{
			final int major = Integer.parseInt(parts[0]);
			final int minor = Integer.parseInt(parts[1]);
			final int patch = parts.length == 3 ? Integer.parseInt(parts[2]) : 0;
			return new Version(major, minor, patch);
		}catch(final NumberFormatException e){
			throw new IllegalArgumentException("Malformed version: " + version, e);
		}
	}

	public int getMajor(){ return major; }
	public int getMinor(){ return minor; }
	public int getPatch(){ return patch; }

	@Override
	public int compareTo(final Version other){
		if(other == null) throw new IllegalArgumentException("Cannot compare to NULL");
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
		int h = 31;
		h = 31 * h + major;
		h = 31 * h + minor;
		h = 31 * h + patch;
		return h;
	}

	@Override
	public String toString(){
		return major + "." + minor + "." + patch;
	}

}
