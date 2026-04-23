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
package spade.reporter.audit.linux.platform.process.info;

import spade.reporter.audit.linux.type.credential.PID;

public class Memory{

	private final PID sharedWith;

	public Memory(){
		this.sharedWith = null;
	}

	public Memory(final Memory other){
		this.sharedWith = other.sharedWith == null ? null : new PID(other.sharedWith);
	}

	public Memory(final PID sharedWith){
		if(sharedWith == null){
			throw new IllegalArgumentException("sharedWith cannot be NULL");
		}
		this.sharedWith = sharedWith;
	}

	public boolean isSharedWith(){
		return sharedWith != null;
	}

	public PID getSharedWith(){
		return sharedWith;
	}


}
