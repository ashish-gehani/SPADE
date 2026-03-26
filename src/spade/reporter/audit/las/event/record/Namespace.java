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

import java.util.Map;

import spade.reporter.audit.las.event.record.helper.Header;
import spade.reporter.audit.las.event.record.helper.KeyValueParser;

/**
 * Record subclass for USER records with namespace subtype.
 *
 * Contains Linux namespace fields.
 *
 * Raw format:
 *   ns_syscall=%d ns_subtype=ns_namespaces ns_operation=ns_%s ns_ns_pid=%ld ns_host_pid=%ld
 *   ns_inum_mnt=%ld ns_inum_net=%ld ns_inum_pid=%ld ns_inum_pid_children=%ld
 *   ns_inum_usr=%ld ns_inum_ipc=%ld
 */
public class Namespace extends Record {

	private static final String NAMESPACE_RECORD_KEY = "ns_subtype";
	private static final String MARKER_NS_SUBTYPE = (
		NAMESPACE_RECORD_KEY + "=ns_namespaces"
	);

	private final String nsSyscall;
	private final String nsSubtype;
	private final String nsOperation;
	private final String nsNsPid;
	private final String nsHostPid;
	private final String nsInumMnt;
	private final String nsInumNet;
	private final String nsInumPid;
	private final String nsInumPidChildren;
	private final String nsInumUsr;
	private final String nsInumIpc;

	public Namespace(
		final String eventId, final String time, final String rawRecord
	){
		super(eventId, time, Type.NAMESPACE, rawRecord);
		final Map<String, String> fields = KeyValueParser.parseKeyValuePairs(rawRecord);
		this.nsSyscall = fields.get("ns_syscall");
		this.nsSubtype = fields.get(NAMESPACE_RECORD_KEY);
		this.nsOperation = fields.get("ns_operation");
		this.nsNsPid = fields.get("ns_ns_pid");
		this.nsHostPid = fields.get("ns_host_pid");
		this.nsInumMnt = fields.get("ns_inum_mnt");
		this.nsInumNet = fields.get("ns_inum_net");
		this.nsInumPid = fields.get("ns_inum_pid");
		this.nsInumPidChildren = fields.get("ns_inum_pid_children");
		this.nsInumUsr = fields.get("ns_inum_usr");
		this.nsInumIpc = fields.get("ns_inum_ipc");
	}

	public String getNsSyscall(){ return nsSyscall; }
	public String getNsSubtype(){ return nsSubtype; }
	public String getNsOperation(){ return nsOperation; }
	public String getNsNsPid(){ return nsNsPid; }
	public String getNsHostPid(){ return nsHostPid; }
	public String getNsInumMnt(){ return nsInumMnt; }
	public String getNsInumNet(){ return nsInumNet; }
	public String getNsInumPid(){ return nsInumPid; }
	public String getNsInumPidChildren(){ return nsInumPidChildren; }
	public String getNsInumUsr(){ return nsInumUsr; }
	public String getNsInumIpc(){ return nsInumIpc; }

	public static class Creator extends Record.Creator{

		@Override
		public String validate(final Header header){
			if(header.getType() != spade.reporter.audit.las.event.record.Type.NAMESPACE){
				return "Expected NAMESPACE, got: " + header.getType();
			}
			if(!(header.getData().indexOf(MARKER_NS_SUBTYPE) >= 0)){
				return "Expected substr '" + MARKER_NS_SUBTYPE + "', got: " + header.getData();
			}
			return null;
		}

		@Override
		public Record create(final Header header) throws MalformedRecordException{
			final String error = validate(header);
			if(error != null) throw new MalformedRecordException(error, header.getRawLine());
			return new Namespace(header.getEventId(), header.getTime(), header.getRawLine());
		}
	}
}
