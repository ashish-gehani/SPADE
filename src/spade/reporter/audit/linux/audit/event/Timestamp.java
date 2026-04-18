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
 * Concrete timestamp for Linux Audit Subsystem events.
 *
 * Wraps a {@code double} value value and delegates identity / ordering to
 * {@link spade.reporter.audit.core.source.Timestamp} via the audit-format
 * string representation (e.g. {@code "1234567890.123"}).
 */
public final class Timestamp extends spade.reporter.audit.core.source.Timestamp{

	// value is in seconds
	private final double value;

	public Timestamp(final String valueStr){
		super(valueStr);
		this.value = Double.parseDouble(valueStr);
	}

	public double getValue(){
		return value;
	}

	public String getSecondsInAuditFormat(){
		return getValueStr();
	}

	@Override
	public int compareTo(final spade.reporter.audit.core.source.Timestamp other){
		if(other instanceof Timestamp){
			return Double.compare(this.value, ((Timestamp) other).value);
		}
		return super.compareTo(other);
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

}
