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
package spade.reporter.audit.las.event.record;

import java.util.HashMap;
import java.util.Map;

/**
 * Enum representing all Linux Audit Subsystem record types handled by the system.
 *
 * Types are added incrementally as record subclasses are implemented.
 */
public enum Type{

	DAEMON_START("DAEMON_START"),
	SYSCALL("SYSCALL"),
	CWD("CWD"),
	PATH("PATH"),
	EXECVE("EXECVE"),
	FD_PAIR("FD_PAIR"),
	SOCKADDR("SOCKADDR"),
	MMAP("MMAP"),
	IPC("IPC"),
	MQ_SENDRECV("MQ_SENDRECV"),
	UBSI_ENTRY("UBSI_ENTRY"),
	UBSI_EXIT("UBSI_EXIT"),
	UBSI_DEP("UBSI_DEP"),
	NETFILTER_HOOK("NETFILTER_HOOK"),
	SOCKETCALL("SOCKETCALL"),
	EOE("EOE"),
	PROCTITLE("PROCTITLE"),
	NETIO("NETIO"),
	UBSI_RAW("UBSI_RAW"),
	NAMESPACE("NAMESPACE"),
	NETFILTER("NETFILTER"),
	UNKNOWN(null);

	private static final String UNKNOWN_PREFIX = "UNKNOWN[";

	private final String auditName;

	private static final Map<String, Type> lookupMap = new HashMap<>();

	static{
		for(final Type type : values()){
			if(type.auditName != null){
				lookupMap.put(type.auditName, type);
			}
		}
	}

	private Type(final String auditName){
		this.auditName = auditName;
	}

	public String getAuditName(){
		return auditName;
	}

	/**
	 * Looks up the Type from the audit record type string.
	 * Handles "UNKNOWN[..." prefix specially by returning UNKNOWN.
	 *
	 * @param auditName the type string from the audit record (e.g., "SYSCALL", "CWD")
	 * @return the matching Type enum value, or UNKNOWN if not recognized
	 */
	public static Type fromAuditName(final String auditName){
		if(auditName == null){
			return UNKNOWN;
		}
		final Type type = lookupMap.get(auditName);
		if(type != null){
			return type;
		}
		if(auditName.startsWith(UNKNOWN_PREFIX)){
			return UNKNOWN;
		}
		return UNKNOWN;
	}
}
