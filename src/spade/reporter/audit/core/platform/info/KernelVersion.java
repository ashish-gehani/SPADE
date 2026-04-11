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
 * Immutable kernel version (major.minor.patch[-extra]).
 *
 * Parse with {@link #parse(String)} from a string such as "5.15.0-generic"
 * or "6.1.72".  The extra suffix (everything after the first {@code -}) is
 * preserved as-is and treated as opaque; it is excluded from ordering.
 */
public final class KernelVersion implements Comparable<KernelVersion>{

	private final int major;
	private final int minor;
	private final int patch;
	/** Vendor/build suffix that follows the first {@code -}, e.g. "generic", "aws". May be empty. */
	private final String extra;

	public KernelVersion(
		final int major,
		final int minor,
		final int patch,
		final String extra
	){
		if(major < 0) throw new IllegalArgumentException("major cannot be negative");
		if(minor < 0) throw new IllegalArgumentException("minor cannot be negative");
		if(patch < 0) throw new IllegalArgumentException("patch cannot be negative");
		if(extra == null) throw new IllegalArgumentException("extra cannot be NULL");
		this.major = major;
		this.minor = minor;
		this.patch = patch;
		this.extra = extra;
	}

	/**
	 * Parses a kernel version string of the form {@code major.minor.patch[-extra]}
	 * or {@code major.minor[-extra]}.
	 *
	 * Examples: {@code "5.15.0-generic"}, {@code "6.8.0-101-generic"}, {@code "4.19"}.
	 */
	public static KernelVersion parse(final String version){
		if(version == null){
			throw new IllegalArgumentException("version cannot be NULL");
		}
		final int dashIdx = version.indexOf('-');
		final String numeric = dashIdx < 0 ? version : version.substring(0, dashIdx);
		final String extra   = dashIdx < 0 ? ""      : version.substring(dashIdx + 1);

		final String[] parts = numeric.split("\\.", -1);
		if(parts.length < 2 || parts.length > 3){
			throw new IllegalArgumentException(
				"kernel version must be major.minor or major.minor.patch: " + version
			);
		}
		try{
			final int major = Integer.parseInt(parts[0]);
			final int minor = Integer.parseInt(parts[1]);
			final int patch = parts.length == 3 ? Integer.parseInt(parts[2]) : 0;
			return new KernelVersion(major, minor, patch, extra);
		}catch(final NumberFormatException e){
			throw new IllegalArgumentException("Malformed kernel version: " + version, e);
		}
	}

	public int getMajor(){ return major; }
	public int getMinor(){ return minor; }
	public int getPatch(){ return patch; }
	/** Returns the vendor/build suffix, e.g. {@code "generic"}.  Empty string when absent. */
	public String getExtra(){ return extra; }

	/** Ordering considers only major.minor.patch; extra is ignored. */
	@Override
	public int compareTo(final KernelVersion other){
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
		if(!(obj instanceof KernelVersion)) return false;
		final KernelVersion other = (KernelVersion) obj;
		return this.major == other.major
			&& this.minor == other.minor
			&& this.patch == other.patch
			&& this.extra.equals(other.extra);
	}

	@Override
	public int hashCode(){
		int h = 31;
		h = 31 * h + major;
		h = 31 * h + minor;
		h = 31 * h + patch;
		h = 31 * h + extra.hashCode();
		return h;
	}

	@Override
	public String toString(){
		return extra.isEmpty()
			? major + "." + minor + "." + patch
			: major + "." + minor + "." + patch + "-" + extra;
	}

}
