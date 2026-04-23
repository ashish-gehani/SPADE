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
package spade.reporter.audit.linux.source.audit.event.record;

import spade.reporter.audit.linux.source.audit.event.ID;
import spade.reporter.audit.linux.source.audit.event.record.helper.Header;
import spade.reporter.audit.linux.source.audit.event.record.helper.KeyValueParser;
import spade.reporter.audit.linux.source.audit.event.record.helper.ProcessInfo;
import spade.reporter.audit.linux.source.audit.event.record.helper.StringHelper;
import spade.reporter.audit.linux.source.audit.event.record.type.saddr.Saddr;
import spade.reporter.audit.linux.type.fd.Num;
import spade.reporter.audit.linux.type.fs.Inode;

import java.util.Map;

/**
 * Record subclass for USER records with netio_intercepted subtype.
 *
 * Contains network I/O fields intercepted by the kernel module.
 *
 * Raw format:
 *   netio_intercepted="syscall=%d exit=%ld success=%d fd=%d pid=%d ppid=%d
 *   uid=%u euid=%u suid=%u fsuid=%u gid=%u egid=%u sgid=%u fsgid=%u
 *   comm=%s sock_type=%d local_saddr=%s remote_saddr=%s remote_saddr_size=%d net_ns_inum=%u"
 */
public class Netio extends Record{

	private static final String NETIO_RECORD_KEY = "netio_intercepted";
	private static final String MARKER_NETIO_RECORD = (
		NETIO_RECORD_KEY + "="
	);

	private final int syscall;
	private final long exit;
	private final boolean success;
	private final Num fd;
	private final ProcessInfo processInfo;
	private final int sockType;
	private final Saddr localSaddr;
	private final Saddr remoteSaddr;
	private final int remoteSaddrSize;
	private final Inode netNsInum;

	public Netio(
		final ID id, final String rawRecord
	) throws MalformedRecordException{
		super(id, Type.NETIO, rawRecord);

		final String subRecord = StringHelper.substringBetween(rawRecord,
				NETIO_RECORD_KEY + "=\"", "\"");
		if(subRecord == null){
			throw new MalformedRecordException(
					"Missing '" + NETIO_RECORD_KEY + "' sub-record in USER record", rawRecord);
		}

		final Map<String, String> fields = KeyValueParser.parseKeyValuePairs(subRecord);
		this.syscall = Integer.parseInt(fields.get("syscall"));
		this.exit = Long.parseLong(fields.get("exit"));
		this.success = Integer.parseInt(fields.get("success")) != 0;
		this.fd = new Num(Integer.parseInt(fields.get("fd")));
		this.processInfo = ProcessInfo.parse(subRecord);
		this.sockType = Integer.parseInt(fields.get("sock_type"));
		this.localSaddr = Saddr.parse(fields.get("local_saddr"));
		this.remoteSaddr = Saddr.parse(fields.get("remote_saddr"));
		this.remoteSaddrSize = Integer.parseInt(fields.get("remote_saddr_size"));
		this.netNsInum = new Inode(Long.parseUnsignedLong(fields.get("net_ns_inum")));
	}

	public int getSyscall(){ return syscall; }
	public long getExit(){ return exit; }
	public boolean getSuccess(){ return success; }
	public Num getFd(){ return fd; }
	public ProcessInfo getProcessInfo(){ return processInfo; }
	public int getSockType(){ return sockType; }
	public Saddr getLocalSaddr(){ return localSaddr; }
	public Saddr getRemoteSaddr(){ return remoteSaddr; }
	public int getRemoteSaddrSize(){ return remoteSaddrSize; }
	public Inode getNetNsInum(){ return netNsInum; }

	public static class Creator extends Record.Creator{

		@Override
		public String validate(final Header header){
			if(header.getType() != spade.reporter.audit.linux.source.audit.event.record.Type.NETIO){
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
			return new Netio(header.getId(), header.getRawLine());
		}
	}
}
