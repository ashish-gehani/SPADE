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
package spade.reporter.audit.linux.event;

/**
 * Concrete timestamp for Linux Audit Subsystem events.
 *
 * Wraps a {@code double} value in seconds (e.g. {@code 1234567890.123}).
 */
public final class Timestamp implements Comparable<Timestamp>{

	// value is in seconds
	private final double value;

	public Timestamp(final double value){
		this.value = value;
	}

	public double getValue(){
		return value;
	}

	public String getSecondsInAuditFormat(){
		return String.valueOf(value);
	}

	@Override
	public int compareTo(final Timestamp other){
		return Double.compare(this.value, other.value);
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof Timestamp)) return false;
		return Double.compare(this.value, ((Timestamp) obj).value) == 0;
	}

	@Override
	public int hashCode(){
		return Double.hashCode(value);
	}

	@Override
	public String toString(){
		return String.valueOf(value);
	}

}
