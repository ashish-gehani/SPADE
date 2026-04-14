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

import spade.reporter.audit.core.util.statetable.Indexable;

public abstract class ID implements Indexable<ID>{

	private final Num num;
	private final Timestamp timestamp;

	protected ID(final Num num, final Timestamp timestamp){
		if(num == null){
			throw new IllegalArgumentException("num cannot be NULL");
		}
		if(timestamp == null){
			throw new IllegalArgumentException("timestamp cannot be NULL");
		}
		this.num = num;
		this.timestamp = timestamp;
	}

	public final Num getNum(){
		return num;
	}

	public final Timestamp getTimestamp(){
		return timestamp;
	}

	@Override
	public int compareTo(final ID other){
		final int cmp = this.num.compareTo(other.num);
		if(cmp != 0){
			return cmp;
		}
		return this.timestamp.compareTo(other.timestamp);
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof ID)) return false;
		final ID other = (ID) obj;
		return this.num.equals(other.num) && this.timestamp.equals(other.timestamp);
	}

	@Override
	public int hashCode(){
		int result = num.hashCode();
		result = 31 * result + timestamp.hashCode();
		return result;
	}

	@Override
	public final String toString(){
		return "ID[num=" + num + ", timestamp=" + timestamp + "]";
	}

}
