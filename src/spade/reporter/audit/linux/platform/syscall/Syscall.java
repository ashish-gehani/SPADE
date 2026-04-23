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
package spade.reporter.audit.linux.platform.syscall;


public final class Syscall {

	public final int num;
	public final String name;

	public Syscall(final int num, final String name){
		if(name == null){
			throw new IllegalArgumentException("name cannot be NULL");
		}
		this.num = num;
		this.name = name;
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof Syscall)) return false;
		final Syscall other = (Syscall) obj;
		return this.num == other.num && this.name.equals(other.name);
	}

	@Override
	public int hashCode(){
		int result = Integer.hashCode(num);
		result = 31 * result + name.hashCode();
		return result;
	}

}
