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
package spade.reporter.audit.core.platform.util.datastore;

import spade.reporter.audit.core.util.statetable.Indexable;

public final class Key implements Indexable<Key>{

	public final long id;
	public final String tag;

	public Key(final long id, final String tag){
		if(tag == null){
			throw new IllegalArgumentException("tag cannot be NULL");
		}
		this.id = id;
		this.tag = tag;
	}

	public Key(final Key key) {
		this(key.id, key.tag);
	}

	public long getId(){
		return id;
	}

	public String getTag(){
		return tag;
	}

	@Override
	public String toString(){
		return "Key(id=" + id + ", tag=" + tag + ")";
	}

	@Override
	public int compareTo(final Key other){
		return Long.compare(this.id, other.id);
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof Key)) return false;
		final Key other = (Key) obj;
		return this.id == other.id;
	}

	@Override
	public int hashCode(){
		return Long.hashCode(id);
	}

}
