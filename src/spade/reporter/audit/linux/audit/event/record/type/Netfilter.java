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
package spade.reporter.audit.linux.audit.event.record.type;

import java.util.Map;

import spade.reporter.audit.linux.audit.event.ID;
import spade.reporter.audit.linux.audit.event.record.MalformedRecordException;
import spade.reporter.audit.linux.audit.event.record.Record;
import spade.reporter.audit.linux.audit.event.record.Type;
import spade.reporter.audit.linux.audit.event.record.helper.Header;
import spade.reporter.audit.linux.audit.event.record.helper.KeyValueParser;

/**
 * Record subclass for USER records with netfilter subtype.
 *
 * Contains network filter hook fields.
 *
 * Raw format:
 *   version=%s nf_subtype=nf_netfilter nf_hook=%s nf_priority=%s nf_id=%p
 *   nf_src_ip=%s nf_src_port=%d nf_dst_ip=%s nf_dst_port=%d
 *   nf_protocol=%s nf_ip_version=%s nf_net_ns=%u
 */
public class Netfilter extends Record {

	private static final String NETFILTER_RECORD_KEY = "nf_subtype";
	private static final String MARKER_NF_SUBTYPE = (
		NETFILTER_RECORD_KEY + "=nf_netfilter"
	);

	private final String version;
	private final String nfSubtype;
	private final String nfHook;
	private final String nfPriority;
	private final String nfId;
	private final String nfSrcIp;
	private final String nfSrcPort;
	private final String nfDstIp;
	private final String nfDstPort;
	private final String nfProtocol;
	private final String nfIpVersion;
	private final String nfNetNs;

	public Netfilter(
		final ID id, final String rawRecord
	){
		super(id, Type.NETFILTER, rawRecord);
		final Map<String, String> fields = KeyValueParser.parseKeyValuePairs(rawRecord);
		this.version = fields.get("version");
		this.nfSubtype = fields.get(NETFILTER_RECORD_KEY);
		this.nfHook = fields.get("nf_hook");
		this.nfPriority = fields.get("nf_priority");
		this.nfId = fields.get("nf_id");
		this.nfSrcIp = fields.get("nf_src_ip");
		this.nfSrcPort = fields.get("nf_src_port");
		this.nfDstIp = fields.get("nf_dst_ip");
		this.nfDstPort = fields.get("nf_dst_port");
		this.nfProtocol = fields.get("nf_protocol");
		this.nfIpVersion = fields.get("nf_ip_version");
		this.nfNetNs = fields.get("nf_net_ns");
	}

	public String getVersion(){ return version; }
	public String getNfSubtype(){ return nfSubtype; }
	public String getNfHook(){ return nfHook; }
	public String getNfPriority(){ return nfPriority; }
	public String getNfId(){ return nfId; }
	public String getNfSrcIp(){ return nfSrcIp; }
	public String getNfSrcPort(){ return nfSrcPort; }
	public String getNfDstIp(){ return nfDstIp; }
	public String getNfDstPort(){ return nfDstPort; }
	public String getNfProtocol(){ return nfProtocol; }
	public String getNfIpVersion(){ return nfIpVersion; }
	public String getNfNetNs(){ return nfNetNs; }

	public static class Creator extends Record.Creator{

		@Override
		public String validate(final Header header){
			if(header.getType() != spade.reporter.audit.linux.audit.event.record.Type.NETFILTER){
				return "Expected NETFILTER, got: " + header.getType();
			}
			if(!(header.getRawLine().indexOf(MARKER_NF_SUBTYPE) >= 0)){
				return "Expected substr '" + MARKER_NF_SUBTYPE + "', got: " + header.getRawLine();
			}
			return null;
		}

		@Override
		public Record create(final Header header) throws MalformedRecordException{
			final String error = validate(header);
			if(error != null) throw new MalformedRecordException(error, header.getRawLine());
			return new Netfilter(header.getId(), header.getRawLine());
		}
	}
}
