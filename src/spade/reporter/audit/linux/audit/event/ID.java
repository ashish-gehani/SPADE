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
 * Concrete event identifier for Linux Audit Subsystem events.
 *
 * Composes a {@link Num} and a {@link Timestamp} and delegates all identity /
 * ordering logic to {@link spade.reporter.audit.core.event.ID}.
 */
public final class ID extends spade.reporter.audit.core.event.ID{

	public ID(final Num num, final Timestamp timestamp){
		super(num, timestamp);
	}

	@Override
	public int compareTo(final spade.reporter.audit.core.event.ID other){
		if(other instanceof ID){
			final ID o = (ID) other;
			final int cmp = ((Num) getNum()).compareTo(o.getNum());
			if(cmp != 0){
				return cmp;
			}
			return ((Timestamp) getTimestamp()).compareTo(o.getTimestamp());
		}
		return super.compareTo(other);
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof ID)) return false;
		final ID other = (ID) obj;
		return getNum().equals(other.getNum()) && getTimestamp().equals(other.getTimestamp());
	}

	@Override
	public int hashCode(){
		int result = getNum().hashCode();
		result = 31 * result + getTimestamp().hashCode();
		return result;
	}

}
