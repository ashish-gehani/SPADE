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
package spade.reporter.audit.linux.source.audit.event.record.namespace;

import java.util.Map;

import spade.reporter.audit.linux.source.audit.event.ID;
import spade.reporter.audit.linux.source.audit.event.record.MalformedRecordException;
import spade.reporter.audit.linux.source.audit.event.record.Record;
import spade.reporter.audit.linux.source.audit.event.record.Type;
import spade.reporter.audit.linux.source.audit.event.record.helper.Header;
import spade.reporter.audit.linux.source.audit.event.record.helper.KeyValueParser;
import spade.reporter.audit.linux.type.credential.PID;
import spade.reporter.audit.linux.type.fs.Inode;
import spade.reporter.audit.linux.type.namespace.Tuple;

/**
 * Record subclass for USER records with namespace subtype.
 *
 * Contains Linux namespace fields.
 *
 * Raw format:
 *   ns_syscall=%d ns_subtype=ns_namespaces ns_operation=ns_%s ns_ns_pid=%ld ns_host_pid=%ld
 *   ns_inum_mnt=%u ns_inum_net=%u ns_inum_pid=%u ns_inum_pid_children=%u
 *   ns_inum_usr=%u ns_inum_ipc=%u ns_inum_cgroup=%u
 */
public class Namespace extends Record{

	private static final String NAMESPACE_RECORD_KEY = "ns_subtype";
	private static final String MARKER_NS_SUBTYPE = (
		NAMESPACE_RECORD_KEY + "=ns_namespaces"
	);

	private final int nsSyscall;
	private final String nsSubtype;
	private final Operation nsOperation;
	private final PID nsNsPid;
	private final PID nsHostPid;
	private final Tuple namespaces;

	public Namespace(
		final ID id, final String rawRecord
	){
		super(id, Type.NAMESPACE, rawRecord);
		final Map<String, String> fields = KeyValueParser.parseKeyValuePairs(rawRecord);
		this.nsSyscall = Integer.parseInt(fields.get("ns_syscall"));
		this.nsSubtype = fields.get(NAMESPACE_RECORD_KEY);
		this.nsOperation = Operation.parse(fields.get("ns_operation"));
		this.nsNsPid = new PID(Long.parseLong(fields.get("ns_ns_pid")));
		this.nsHostPid = new PID(Long.parseLong(fields.get("ns_host_pid")));
		this.namespaces = new Tuple(
			nsId(spade.reporter.audit.linux.type.namespace.Type.MOUNT,        fields.get("ns_inum_mnt")),
			nsId(spade.reporter.audit.linux.type.namespace.Type.USER,         fields.get("ns_inum_usr")),
			nsId(spade.reporter.audit.linux.type.namespace.Type.NET,          fields.get("ns_inum_net")),
			nsId(spade.reporter.audit.linux.type.namespace.Type.PID,          fields.get("ns_inum_pid")),
			nsId(spade.reporter.audit.linux.type.namespace.Type.PID_CHILDREN, fields.get("ns_inum_pid_children")),
			nsId(spade.reporter.audit.linux.type.namespace.Type.IPC,          fields.get("ns_inum_ipc")),
			nsId(spade.reporter.audit.linux.type.namespace.Type.CGROUP,       fields.get("ns_inum_cgroup"))
		);
	}

	public int getNsSyscall(){ return nsSyscall; }
	public String getNsSubtype(){ return nsSubtype; }
	public Operation getNsOperation(){ return nsOperation; }
	public PID getNsNsPid(){ return nsNsPid; }
	public PID getNsHostPid(){ return nsHostPid; }
	public Tuple getNamespaces(){ return namespaces; }

	private static spade.reporter.audit.linux.type.namespace.ID nsId(
			final spade.reporter.audit.linux.type.namespace.Type type, final String inumStr){
		return new spade.reporter.audit.linux.type.namespace.ID(
			type, new Inode(inumStr == null ? -1L : Long.parseLong(inumStr))
		);
	}

	public static class Creator extends Record.Creator{

		@Override
		public String validate(final Header header){
			if(header.getType() != spade.reporter.audit.linux.source.audit.event.record.Type.NAMESPACE){
				return "Expected NAMESPACE, got: " + header.getType();
			}
			if(!(header.getRawLine().indexOf(MARKER_NS_SUBTYPE) >= 0)){
				return "Expected substr '" + MARKER_NS_SUBTYPE + "', got: " + header.getRawLine();
			}
			return null;
		}

		@Override
		public Record create(final Header header) throws MalformedRecordException{
			final String error = validate(header);
			if(error != null) throw new MalformedRecordException(error, header.getRawLine());
			return new Namespace(header.getId(), header.getRawLine());
		}
	}
}
