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
package spade.reporter.audit.linux.type.fs;

public class Permission{

	private final int value;

	public Permission(final int value){
		if(value < 0 || value > 07777){
			throw new IllegalArgumentException("value must be in octal range [0, 07777]: " + value);
		}
		this.value = value;
	}

	public int getValue(){
		return value;
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof Permission)) return false;
		return this.value == ((Permission) obj).value;
	}

	@Override
	public int hashCode(){
		return Integer.hashCode(value);
	}

}
