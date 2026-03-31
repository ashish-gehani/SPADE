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
package spade.reporter.audit.core.event;

public abstract class ID implements Comparable<ID>{

	private final long id;

	protected ID(final long id){
		this.id = id;
	}

	public final long getId(){
		return id;
	}

	@Override
	public final int compareTo(final ID other){
		return Long.compare(this.id, other.id);
	}

	@Override
	public final boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof ID)) return false;
		return this.id == ((ID) obj).id;
	}

	@Override
	public final int hashCode(){
		return Long.hashCode(id);
	}

	@Override
	public final String toString(){
		return "ID[id=" + id + "]";
	}

}
