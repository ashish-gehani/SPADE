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

import java.util.List;
import java.util.Map;

import spade.reporter.audit.AuditEventReader;
import spade.reporter.audit.Input;
import spade.reporter.audit.SYSCALL;
import spade.utility.ArgumentFunctions;
import spade.utility.HelperFunctions;

/**
 * Temporary home for arch-specific code until it is properly refactored (see README.md).
 *
 * Created with getInstanceForInput, getInstanceForLiveMode, or getInstanceForPlaybackMode. Each call creates a new
 * instance.
 */
public class Stopgap{

	// Audit reporter config key. Playback only: true to scan the audit logs until a SYSCALL record is found, false to
	// scan only the first.
	public static final String keyArchScanAllLogs = "archScanAllLogs";

	// Arch of the system the records are from
	private final Type arch;

	/**
	 * Creates an instance for the Audit reporter's input mode:
	 * <ul>
	 * <li>LIVE: see {@link #getInstanceForLiveMode()}</li>
	 * <li>FILE: the input logs, newest first (the input log list is oldest first)</li>
	 * <li>DIRECTORY: the files in the input directory, oldest first (see {@link Helper#listAuditLogsInDir(String)})</li>
	 * </ul>
	 *
	 * @param map Audit reporter config. Playback reads {@link #keyArchScanAllLogs} from it.
	 * @param input Audit reporter input
	 * @throws Exception if the input mode is unexpected, the config is invalid, or the arch could not be determined
	 */
	public static Stopgap getInstanceForInput(final Map<String, String> map, final Input input) throws Exception{
		switch(input.getMode()){
			case LIVE:		return getInstanceForLiveMode();
			case FILE:		return getInstanceForPlaybackMode(input.getInputLogList().reversed(), mustParseArchScanAllLogs(map));
			case DIRECTORY:	return getInstanceForPlaybackMode(Helper.listAuditLogsInDir(input.getInputDir()), mustParseArchScanAllLogs(map));
			default:		throw new Exception("Unexpected input mode: " + input.getMode());
		}
	}

	private static boolean mustParseArchScanAllLogs(final Map<String, String> map) throws Exception{
		return ArgumentFunctions.mustParseBoolean(keyArchScanAllLogs, map);
	}

	/**
	 * Creates an instance using the arch of this system (from 'uname -m').
	 *
	 * @throws Exception if the arch could not be determined or is not supported, or is ARM64 (live audit not supported
	 * on ARM64 yet)
	 */
	public static Stopgap getInstanceForLiveMode() throws Exception{
		final Type arch = Helper.getSystemArch();
		if(arch == Type.ARM64){
			// Audit rules, kernel modules, and the SPADE audit bridge are x86_64-only for now (see README.md)
			throw new Exception("Live audit not supported on arch: " + arch);
		}
		return new Stopgap(arch);
	}

	/**
	 * Creates an instance using the 'arch' field of the first audit SYSCALL record in the first audit log.
	 *
	 * @param auditLogPaths paths of the audit logs, in the order they are processed
	 * @throws Exception see {@link Helper#getAuditLogsArch(List, boolean)}
	 */
	public static Stopgap getInstanceForPlaybackMode(final List<String> auditLogPaths) throws Exception{
		return getInstanceForPlaybackMode(auditLogPaths, false);
	}

	/**
	 * Creates an instance using the 'arch' field of the first audit SYSCALL record in the audit logs.
	 *
	 * @param auditLogPaths paths of the audit logs, in the order they are processed
	 * @param all true to scan the audit logs in order until a SYSCALL record is found, false to scan only the first
	 * @throws Exception see {@link Helper#getAuditLogsArch(List, boolean)}
	 */
	public static Stopgap getInstanceForPlaybackMode(final List<String> auditLogPaths, final boolean all) throws Exception{
		return new Stopgap(Helper.getAuditLogsArch(auditLogPaths, all));
	}

	private Stopgap(final Type arch){
		this.arch = arch;
	}

	public Type getArchType(){
		return arch;
	}

	/**
	 * Returns the system call in a linux audit record (any record with a 'syscall' field), using the arch the instance
	 * was created with.
	 *
	 * @param eventData key-values of the record (see AuditEventReader)
	 * @return UNSUPPORTED if the system call number is missing or invalid, or the number is unknown
	 */
	public SYSCALL getSyscallFromLinuxAuditRecord(Map<String, String> eventData){
		final int syscallNumber = HelperFunctions.parseInt(eventData.get(AuditEventReader.SYSCALL), -1);
		return SYSCALL.getSyscall(arch, syscallNumber);
	}

	/**
	 * Returns the system call in a linux audit namespace record (from the kernel module, with an 'ns_syscall' field),
	 * using the arch the instance was created with.
	 *
	 * @param eventData key-values of the record (see AuditEventReader)
	 * @return UNSUPPORTED if the system call number is missing or invalid, or the number is unknown
	 */
	public SYSCALL getSyscallFromLinuxAuditNamespaceRecord(Map<String, String> eventData){
		final int syscallNumber = HelperFunctions.parseInt(eventData.get(AuditEventReader.NS_SYSCALL_KEY), -1);
		return SYSCALL.getSyscall(arch, syscallNumber);
	}
}
