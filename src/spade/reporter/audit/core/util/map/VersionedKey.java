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
package spade.reporter.audit.core.util.map;


/**
 * A versioned key for use in a state table.
 * Pairs a key with a strictly-incrementing version number so that
 * two keys with the same value but different versions are distinct.
 *
 * @param <T> the key type; must implement {@link Object#equals} and
 *            {@link Object#hashCode} based on value, not identity
 */
public class VersionedKey<T>{

	private final T key;
	private final long version;

	public VersionedKey(final T key, final long version){
		if(key == null){
			throw new IllegalArgumentException("key cannot be NULL");
		}
		this.key = key;
		this.version = version;
	}

	public T getKey(){
		return key;
	}

	public long getVersion(){
		return version;
	}

	private VersionedKey<T> create(final T value, final long version) {
		return new VersionedKey<T>(value, version);
	}

	public VersionedKey<T> nextVersion(){
		return create(key, version + 1);
	}

	@Override
	public final boolean equals(final Object obj){
		if(this == obj){
			return true;
		}
		if(obj == null || getClass() != obj.getClass()){
			return false;
		}
		final VersionedKey<?> other = (VersionedKey<?>)obj;
		return version == other.version && key.equals(other.key);
	}

	@Override
	public final int hashCode(){
		return 31 * key.hashCode() + Long.hashCode(version);
	}

}
