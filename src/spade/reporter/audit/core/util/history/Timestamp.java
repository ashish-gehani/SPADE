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
package spade.reporter.audit.core.util.history;

public class Timestamp implements Comparable<Timestamp>{

	private final double value;

	public Timestamp(final double value){
		this.value = value;
	}

	public double getValue(){ return value; }

	public static Timestamp fromAuditFormat(final String value){
		if(value == null){
			throw new IllegalArgumentException("value cannot be NULL");
		}
		return new Timestamp(Double.parseDouble(value));
	}

	@Override
	public int compareTo(final Timestamp other){
		return Double.compare(this.value, other.value);
	}

}
