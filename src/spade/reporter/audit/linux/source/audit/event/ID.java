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
package spade.reporter.audit.linux.source.audit.event;

import spade.reporter.audit.core.source.event.IDable;

/**
 * Concrete event identifier for Linux Audit Subsystem events.
 *
 * Composes a {@link Num} and a {@link Timestamp} and delegates all identity /
 * ordering logic to them directly.
 */
public final class ID implements IDable{

	private final Num num;
	private final Timestamp timestamp;

	public ID(final Num num, final Timestamp timestamp){
		if(num == null){
			throw new IllegalArgumentException("num cannot be NULL");
		}
		if(timestamp == null){
			throw new IllegalArgumentException("timestamp cannot be NULL");
		}
		this.num = num;
		this.timestamp = timestamp;
	}

	public Num getNum(){
		return num;
	}

	public Timestamp getTimestamp(){
		return timestamp;
	}

	@Override
	public int compareTo(final IDable other){
		if(other instanceof ID){
			final ID o = (ID) other;
			final int cmp = num.compareTo(o.num);
			if(cmp != 0){
				return cmp;
			}
			return timestamp.compareTo(o.timestamp);
		}
		throw new IllegalArgumentException("Cannot compare ID to " + other.getClass().getName());
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof ID)) return false;
		final ID other = (ID) obj;
		return num.equals(other.num) && timestamp.equals(other.timestamp);
	}

	@Override
	public int hashCode(){
		int result = num.hashCode();
		result = 31 * result + timestamp.hashCode();
		return result;
	}

	@Override
	public String toString(){
		return ID.class.getName() + "(" + timestamp + ":" + num + ")";
	}

}
