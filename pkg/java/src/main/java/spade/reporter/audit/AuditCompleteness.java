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
package spade.reporter.audit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Settings and messages that tell whether audit records may be lost before they reach the
 * Audit reporter, without anything else saying so.
 */
public final class AuditCompleteness{

	private AuditCompleteness(){}

	/** The separator auditd puts before the names it adds with log_format = ENRICHED. */
	public static final char ENRICHED_SEPARATOR = '\u001d';

	public static final String ENRICHED_WARNING = "auditd writes these records with log_format = ENRICHED,"
			+ " so it looks up the name of every uid and gid in every record, again for each record when"
			+ " the lookup fails. That made auditd too slow for containers running as uids the host does"
			+ " not know, and records were lost. log_format = RAW in /etc/audit/auditd.conf avoids it;"
			+ " the Audit reporter reads only the raw fields.";

	public static final String KERNEL_WARNING = "The kernel was not booted with audit=1, so when auditd falls"
			+ " behind for 0.1 s the kernel drops the audit records it holds without counting them as lost"
			+ " (auditctl -s). The audit bridge's count of missing serial numbers reports such losses."
			+ " Booting with audit=1 makes the kernel keep them up to the backlog limit and count the rest.";

	public static final String BRIDGE_NOT_SETUID_ERROR = "The audit bridge '%s' is not setuid root and auditd's"
			+ " socket is not writable for this user, so the bridge cannot connect and the Audit reporter would"
			+ " record nothing. make replaces the bridge without the setuid bit; run bin/allowAuditAccess after"
			+ " every build.";

	private static final Pattern BRIDGE_FAILURE = Pattern.compile(
			"Unable to connect to the socket|Server closed the connection|Unable to start the socket reader");

	private static final Pattern EVENTS_NOT_RECEIVED = Pattern.compile("^Audit events not received .*: (\\d+)\\s*$");
	private static final Pattern BYTES_DROPPED = Pattern.compile("^Socket queue: .*, (\\d+) bytes dropped because the queue was full\\s*$");

	/**
	 * @param line a record as read from the audit bridge
	 * @return true if auditd added names to it (log_format = ENRICHED)
	 */
	public static boolean isEnrichedRecord(final String line){
		return line != null && line.indexOf(ENRICHED_SEPARATOR) >= 0;
	}

	/**
	 * @param bridgeMessage a line the audit bridge wrote to stderr
	 * @return true if the bridge lost auditd's socket, after which nothing is recorded
	 */
	public static boolean reportsBridgeFailure(final String bridgeMessage){
		return bridgeMessage != null && BRIDGE_FAILURE.matcher(bridgeMessage).find();
	}

	/**
	 * Whether a process of this user can run the bridge as root: it is setuid and owned by root,
	 * or this process is root. Unknown (not a POSIX file system) counts as can.
	 */
	public static boolean bridgeRunsAsRoot(final String bridgePath){
		try{
			final java.nio.file.Path path = Paths.get(bridgePath);
			final int mode = (Integer)Files.getAttribute(path, "unix:mode");
			final int owner = (Integer)Files.getAttribute(path, "unix:uid");
			final boolean setuidRoot = (mode & 04000) != 0 && owner == 0;
			final long self = (Integer)Files.getAttribute(Paths.get("/proc/self"), "unix:uid");
			return setuidRoot || self == 0;
		}catch(Exception e){
			return true;
		}
	}

	/**
	 * @param bridgeMessage a line the audit bridge wrote to stderr
	 * @return true if it reports events or data lost (a non-zero count)
	 */
	public static boolean reportsLoss(final String bridgeMessage){
		if(bridgeMessage == null){
			return false;
		}
		for(final Pattern pattern : new Pattern[]{EVENTS_NOT_RECEIVED, BYTES_DROPPED}){
			final Matcher matcher = pattern.matcher(bridgeMessage);
			if(matcher.matches()){
				return !matcher.group(1).matches("0+");
			}
		}
		return false;
	}

	/**
	 * Whether the kernel keeps audit records it cannot hand to auditd (audit_default in
	 * kernel/audit.c). Its command line's last audit= decides: 0 or off is off, anything else on.
	 * Without audit= it is off.
	 *
	 * @param cmdline the kernel's command line (/proc/cmdline)
	 * @return true if the kernel was booted with audit on
	 */
	public static boolean kernelKeepsUndeliveredRecords(final String cmdline){
		boolean on = false;
		if(cmdline != null){
			for(final String token : cmdline.trim().split("\\s+")){
				if(token.startsWith("audit=")){
					final String value = token.substring("audit=".length());
					on = !(value.equalsIgnoreCase("off") || value.equals("0"));
				}
			}
		}
		return on;
	}

	/** Logs KERNEL_WARNING if /proc/cmdline shows the kernel drops undelivered records. */
	public static void warnAboutKernel(final Logger logger){
		final String cmdline;
		try{
			cmdline = new String(Files.readAllBytes(Paths.get("/proc/cmdline")), StandardCharsets.UTF_8);
		}catch(Exception e){
			return; // not Linux, or not readable: nothing to say
		}
		if(!kernelKeepsUndeliveredRecords(cmdline)){
			logger.log(Level.WARNING, KERNEL_WARNING);
		}
	}
}
