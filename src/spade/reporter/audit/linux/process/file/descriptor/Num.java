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
package spade.reporter.audit.linux.process.file.descriptor;

import spade.reporter.audit.core.util.statetable.Indexable;

public class Num implements Indexable<Num>{

	private final int val;

	public Num(final int val){
		this.val = val;
	}

	public int getVal(){ return val; }

	@Override
	public int compareTo(final Num other){
		return Integer.compare(this.val, other.val);
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof Num)) return false;
		return this.val == ((Num) obj).val;
	}

	@Override
	public int hashCode(){
		return Integer.hashCode(val);
	}

}
