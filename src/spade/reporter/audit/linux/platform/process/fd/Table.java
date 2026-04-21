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
package spade.reporter.audit.linux.platform.process.fd;

import spade.reporter.audit.linux.platform.type.fd.Num;

public class Table extends spade.reporter.audit.core.util.statetable.Table<Num, Descriptor>{

	private final spade.reporter.audit.linux.platform.type.credential.PID sharedWith;

	public Table(){
		this.sharedWith = null;
	}

	public Table(final Table other){
		this.sharedWith = other.sharedWith;
		for(final Num id : other.ids()){
			put(new Num(id), new Descriptor(other.get(id)));
		}
	}

	public Table(final spade.reporter.audit.linux.platform.type.credential.PID sharedWith){
		if(sharedWith == null){
			throw new IllegalArgumentException("sharedWith cannot be NULL");
		}
		this.sharedWith = sharedWith;
	}

	public boolean isSharedWith(){
		return sharedWith != null;
	}

	public spade.reporter.audit.linux.platform.type.credential.PID getSharedWith(){
		return sharedWith;
	}

}
