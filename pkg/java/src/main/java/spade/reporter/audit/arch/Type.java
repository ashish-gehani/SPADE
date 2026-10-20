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
package spade.reporter.audit.arch;

/**
 * Supported system architectures.
 */
public enum Type{

	X86_64("c000003e", "x86_64"),
	ARM64("c00000b7", "aarch64");

	// Value of the 'arch' field in audit SYSCALL records
	private final String auditValue;
	// Output of 'uname -m'
	private final String unameMachine;

	private Type(final String auditValue, final String unameMachine){
		this.auditValue = auditValue;
		this.unameMachine = unameMachine;
	}

	public String getAuditValue(){
		return auditValue;
	}

	public String getUnameMachine(){
		return unameMachine;
	}

	/**
	 * @param auditValue value of the 'arch' field in an audit SYSCALL record (hex, case-insensitive)
	 * @return null if not supported
	 */
	public static Type fromAuditValue(final String auditValue){
		for(final Type type : values()){
			if(type.auditValue.equalsIgnoreCase(auditValue)){
				return type;
			}
		}
		return null;
	}

	/**
	 * @param unameMachine output of 'uname -m'
	 * @return null if not supported
	 */
	public static Type fromUnameMachine(final String unameMachine){
		for(final Type type : values()){
			if(type.unameMachine.equals(unameMachine)){
				return type;
			}
		}
		return null;
	}
}
