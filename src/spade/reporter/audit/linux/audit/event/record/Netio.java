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

import java.util.Map;

import spade.reporter.audit.linux.audit.event.record.helper.AuditStringParser;
import spade.reporter.audit.linux.audit.event.ID;
import spade.reporter.audit.linux.audit.event.Timestamp;
import spade.reporter.audit.linux.audit.event.record.helper.Header;
import spade.reporter.audit.linux.audit.event.record.helper.KeyValueParser;
import spade.reporter.audit.linux.audit.event.record.helper.ProcessInfo;
import spade.reporter.audit.linux.audit.event.record.helper.StringHelper;

/**
 * Record subclass for USER records with netio_intercepted subtype.
 *
 * Contains network I/O fields intercepted by the kernel module.
 *
 * Raw format:
 *   netio_intercepted="syscall=%d exit=%ld success=%d fd=%d pid=%d ppid=%d
 *   uid=%u euid=%u suid=%u fsuid=%u gid=%u egid=%u sgid=%u fsgid=%u
 *   comm=%s sock_type=%d local_saddr=%s remote_saddr=%s remote_saddr_size=%d net_ns_inum=%ld"
 */
public class Netio extends Record{

	private static final String NETIO_RECORD_KEY = "netio_intercepted";
	private static final String MARKER_NETIO_RECORD = (
		NETIO_RECORD_KEY + "="
	);

	private final String syscall;
	private final String exit;
	private final String success;
	private final String fd;
	private final ProcessInfo processInfo;
	private final String sockType;
	private final String localSaddr;
	private final String remoteSaddr;
	private final String remoteSaddrSize;
	private final String netNsInum;

	public Netio(
		final ID eventId, final Timestamp time, final String rawRecord
	) throws MalformedRecordException{
		super(eventId, time, Type.NETIO, rawRecord);

		final String subRecord = StringHelper.substringBetween(rawRecord,
				NETIO_RECORD_KEY + "=\"", "\"");
		if(subRecord == null){
			throw new MalformedRecordException(
					"Missing '" + NETIO_RECORD_KEY + "' sub-record in USER record", rawRecord);
		}

		final Map<String, String> fields = KeyValueParser.parseKeyValuePairs(subRecord);
		this.syscall = fields.get("syscall");
		this.exit = fields.get("exit");
		this.success = fields.get("success");
		this.fd = fields.get("fd");
		this.processInfo = new ProcessInfo(
				fields.get("pid"), fields.get("ppid"),
				fields.get("uid"), fields.get("euid"), fields.get("suid"), fields.get("fsuid"),
				fields.get("gid"), fields.get("egid"), fields.get("sgid"), fields.get("fsgid"),
				AuditStringParser.mustParse(subRecord, "comm"));
		this.sockType = fields.get("sock_type");
		this.localSaddr = fields.get("local_saddr");
		this.remoteSaddr = fields.get("remote_saddr");
		this.remoteSaddrSize = fields.get("remote_saddr_size");
		this.netNsInum = fields.get("net_ns_inum");
	}

	public String getSyscall(){ return syscall; }
	public String getExit(){ return exit; }
	public String getSuccess(){ return success; }
	public String getFd(){ return fd; }
	public ProcessInfo getProcessInfo(){ return processInfo; }
	public String getSockType(){ return sockType; }
	public String getLocalSaddr(){ return localSaddr; }
	public String getRemoteSaddr(){ return remoteSaddr; }
	public String getRemoteSaddrSize(){ return remoteSaddrSize; }
	public String getNetNsInum(){ return netNsInum; }

	public static class Creator extends Record.Creator{

		@Override
		public String validate(final Header header){
			if(header.getType() != spade.reporter.audit.linux.audit.event.record.Type.NETIO){
				return "Expected NETIO, got: " + header.getType();
			}
			if(!(header.getRawLine().indexOf(MARKER_NETIO_RECORD) >= 0)){
				return "Expected substr '" + MARKER_NETIO_RECORD + "', got: " + header.getRawLine();
			}
			return null;
		}

		@Override
		public Record create(final Header header) throws MalformedRecordException{
			final String error = validate(header);
			if(error != null) throw new MalformedRecordException(error, header.getRawLine());
			return new Netio(header.getEventId(), header.getTime(), header.getRawLine());
		}
	}
}
