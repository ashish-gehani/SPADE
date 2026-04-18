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
package spade.reporter.audit.core.source;

import spade.reporter.audit.core.util.statetable.Indexable;

public abstract class Num implements Indexable<Num>{

	private final String valueStr;

	protected Num(final String valueStr){
		if(valueStr == null){
			throw new IllegalArgumentException("valueStr cannot be NULL");
		}
		this.valueStr = valueStr;
	}

	public final String getValueStr(){
		return valueStr;
	}

	@Override
	public int compareTo(final Num other){
		return this.valueStr.compareTo(other.valueStr);
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof Num)) return false;
		return this.valueStr.equals(((Num) obj).valueStr);
	}

	@Override
	public int hashCode(){
		return valueStr.hashCode();
	}

	@Override
	public final String toString(){
		return "Num[valueStr=" + valueStr + "]";
	}

}
