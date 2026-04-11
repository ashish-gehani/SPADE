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
package spade.reporter.audit.linux.audit.event.syscall;

/**
 * Parsing context for syscall events.
 *
 * Extends {@link spade.reporter.audit.linux.audit.event.Context} with the
 * syscall number extracted from the SYSCALL record, making it available
 * for dispatch without requiring a full event object to be constructed first.
 */
public class Context extends spade.reporter.audit.linux.audit.event.Context{

	private final String syscallNumber;

	public Context(final String syscallNumber){
		super();
		if(syscallNumber == null){
			throw new IllegalArgumentException("syscallNumber cannot be NULL");
		}
		this.syscallNumber = syscallNumber;
	}

	public String getSyscallNumber(){
		return syscallNumber;
	}

}
