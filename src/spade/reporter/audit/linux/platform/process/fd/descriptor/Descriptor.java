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
package spade.reporter.audit.linux.platform.process.fd.descriptor;

import spade.reporter.audit.linux.platform.process.fd.Num;

public abstract class Descriptor{

	public final Type type;
	private final Num num;

	protected Descriptor(final Type type, final Num num){
		if(type == null){
			throw new IllegalArgumentException("type cannot be NULL");
		}
		if(num == null){
			throw new IllegalArgumentException("num cannot be NULL");
		}
		this.type = type;
		this.num = num;
	}

	public Num getNum(){
		return num;
	}

}
