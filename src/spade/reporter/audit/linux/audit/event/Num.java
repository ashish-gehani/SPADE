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
package spade.reporter.audit.linux.audit.event;

/**
 * Concrete event number for Linux Audit Subsystem events.
 */
public final class Num implements Comparable<Num>{

	private final long value;

	public Num(final long value){
		this.value = value;
	}

	public long getValue(){
		return value;
	}

	@Override
	public int compareTo(final Num other){
		return Long.compare(this.value, other.value);
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof Num)) return false;
		return this.value == ((Num) obj).value;
	}

	@Override
	public int hashCode(){
		return Long.hashCode(value);
	}

	@Override
	public String toString(){
		return String.valueOf(value);
	}

}
