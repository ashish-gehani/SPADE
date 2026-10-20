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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import spade.utility.Execute;

/**
 * Helpers to determine the arch.
 */
public class Helper{

	private static final Pattern PATTERN_SYSCALL_RECORD_ARCH = Pattern.compile("type=SYSCALL .*? arch=([0-9a-fA-F]+)");

	/**
	 * Returns the arch of this system (from 'uname -m').
	 *
	 * @throws Exception if the arch could not be determined or is not supported
	 */
	public static Type getSystemArch() throws Exception{
		final String command = "uname -m";
		final Execute.Output output = Execute.getOutput(command);
		if(output.hasError() || output.getStdOut().isEmpty()){
			throw new Exception("Failed to get system arch using command '" + command + "': " + output.getStdErr());
		}
		final String machine = output.getStdOut().get(0).trim();
		final Type arch = Type.fromUnameMachine(machine);
		if(arch == null){
			throw new IllegalArgumentException("Unsupported system arch: '" + machine + "'");
		}
		return arch;
	}

	/**
	 * Returns the arch in the 'arch' field of the first audit SYSCALL record in the first audit log.
	 *
	 * @param auditLogPaths paths of the audit logs, in the order they are processed
	 * @throws Exception see {@link #getAuditLogsArch(List, boolean)}
	 */
	public static Type getAuditLogsArch(final List<String> auditLogPaths) throws Exception{
		return getAuditLogsArch(auditLogPaths, false);
	}

	/**
	 * Returns the arch in the 'arch' field of the first audit SYSCALL record in the audit logs.
	 *
	 * @param auditLogPaths paths of the audit logs, in the order they are processed
	 * @param all true to scan the audit logs in order until a SYSCALL record is found, false to scan only the first
	 * @throws Exception if there are no audit logs, if an audit log could not be read, if no SYSCALL record is found,
	 * or if the arch is not supported
	 */
	public static Type getAuditLogsArch(final List<String> auditLogPaths, final boolean all) throws Exception{
		if(auditLogPaths == null || auditLogPaths.isEmpty()){
			throw new Exception("No audit logs to get the arch from");
		}
		final List<String> pathsToScan = all ? auditLogPaths : auditLogPaths.subList(0, 1);
		for(final String auditLogPath : pathsToScan){
			final Type arch = findAuditLogArch(auditLogPath);
			if(arch != null){
				return arch;
			}
		}
		throw new Exception("No SYSCALL record in audit logs: " + pathsToScan);
	}

	/**
	 * Returns null if the audit log has no SYSCALL record.
	 */
	private static Type findAuditLogArch(final String auditLogPath) throws Exception{
		try(final BufferedReader reader = new BufferedReader(new FileReader(auditLogPath))){
			String line;
			while((line = reader.readLine()) != null){
				final Matcher matcher = PATTERN_SYSCALL_RECORD_ARCH.matcher(line);
				if(matcher.find()){
					final String auditValue = matcher.group(1);
					final Type arch = Type.fromAuditValue(auditValue);
					if(arch == null){
						throw new IllegalArgumentException("Unsupported arch '" + auditValue + "' in audit log '" + auditLogPath + "'");
					}
					return arch;
				}
			}
		}
		return null;
	}

	/**
	 * Returns the paths of the regular files in the directory, ordered by last modified time (oldest first), which is
	 * the order the SPADE audit bridge processes them in.
	 *
	 * @param dirPath path of the directory
	 * @throws Exception if the directory could not be listed
	 */
	public static List<String> listAuditLogsInDir(final String dirPath) throws Exception{
		final File[] files = new File(dirPath).listFiles(File::isFile);
		if(files == null){
			throw new Exception("Failed to list files in directory '" + dirPath + "'");
		}
		Arrays.sort(files, Comparator.comparingLong(File::lastModified));
		final List<String> paths = new ArrayList<String>();
		for(final File file : files){
			paths.add(file.getPath());
		}
		return paths;
	}
}
