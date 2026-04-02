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
package spade.reporter.audit.linux.audit.event.record;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.linux.audit.event.record.Record.Creator;
import spade.reporter.audit.linux.audit.event.record.helper.Header;
import spade.reporter.audit.linux.audit.event.record.path.Path;
import spade.reporter.audit.linux.audit.event.record.ubsi.UbsiDep;
import spade.reporter.audit.linux.audit.event.record.ubsi.UbsiEntry;
import spade.reporter.audit.linux.audit.event.record.ubsi.UbsiExit;
import spade.reporter.audit.linux.audit.event.record.ubsi.UbsiRaw;

/**
 * Factory class for creating Record subclass instances from raw audit log lines.
 *
 * Parses the header to determine the record {@link Type}, looks up the
 * registered {@link Record.Creator} for that type in a HashMap, then
 * delegates to {@link Record.Creator#create}.
 *
 * Returns null when no creator is registered for the type (e.g. EOE, PROCTITLE,
 * UNKNOWN) or when the creator's {@link Record.Creator#matches} check fails.
 */
public final class Factory{

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private final boolean verbose;

	private final Map<Type, Record.Creator> creators = Collections.unmodifiableMap(
		new HashMap<Type, Record.Creator>(){
			{
				put(Type.DAEMON_START, new DaemonStart.Creator());
				put(Type.SYSCALL, new Syscall.Creator());
				put(Type.CWD, new Cwd.Creator());
				put(Type.PATH, new Path.Creator());
				put(Type.EXECVE, new Execve.Creator());
				put(Type.FD_PAIR, new FdPair.Creator());
				put(Type.SOCKADDR, new Sockaddr.Creator());
				put(Type.MMAP, new Mmap.Creator());
				put(Type.IPC, new Ipc.Creator());
				put(Type.MQ_SENDRECV, new MqSendRecv.Creator());
				put(Type.UBSI_ENTRY, new UbsiEntry.Creator());
				put(Type.UBSI_EXIT, new UbsiExit.Creator());
				put(Type.UBSI_DEP, new UbsiDep.Creator());
				put(Type.UBSI_RAW, new UbsiRaw.Creator());
				put(Type.NETIO, new Netio.Creator());
				put(Type.NETFILTER, new Netfilter.Creator());
				put(Type.NAMESPACE, new Namespace.Creator());
			}
		}
	);

	public Factory(final boolean verbose){
		this.verbose = verbose;
	}

	public boolean isVerbose(){
		return verbose;
	}

	/**
	 * Creates the appropriate Record subclass from a raw audit log line.
	 *
	 * @param rawLine the complete raw audit log line
	 * @return the appropriate Record subclass, or null if the record should be skipped
	 * @throws MalformedRecordException if the line is malformed
	 */
	public Record create(final String rawLine) throws MalformedRecordException{
		final Header header = Header.parse(rawLine);
		final Type type = header.getType();

		final Creator creator = creators.get(type);
		if (creator == null) {
			if(verbose){
				logger.log(Level.INFO, "No creator matched for type={0}, eventId={1}",
						new Object[]{type, header.getEventId()});
			}
		}

		if(!creator.matches(header)){
			if(verbose){
				logger.log(Level.INFO, "Creator with type={0} did not match record with eventId={1}",
						new Object[]{type, header.getEventId()});
			}
			return null;
		}
		return creator.create(header);
	}
}
