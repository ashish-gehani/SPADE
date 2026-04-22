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
package spade.reporter.audit.linux.platform.util.time;

public class Time{

	private final String value;
	private final Type type;

	public Time(final String value, final Type type){
		if(value == null){
			throw new IllegalArgumentException("value cannot be NULL");
		}
		if(type == null){
			throw new IllegalArgumentException("type cannot be NULL");
		}
		this.value = value;
		this.type = type;
	}

	public String getValue(){ return value; }
	public Type getType(){ return type; }

	public Time(final Time other){
		this(other.value, other.type);
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof Time)) return false;
		final Time other = (Time) obj;
		return this.type == other.type && this.value.equals(other.value);
	}

	@Override
	public int hashCode(){
		int result = type.hashCode();
		result = 31 * result + value.hashCode();
		return result;
	}

}
