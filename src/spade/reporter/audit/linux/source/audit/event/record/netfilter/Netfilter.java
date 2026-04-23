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
package spade.reporter.audit.linux.source.audit.event.record.netfilter;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Map;

import spade.reporter.audit.linux.source.audit.event.ID;
import spade.reporter.audit.linux.source.audit.event.record.MalformedRecordException;
import spade.reporter.audit.linux.source.audit.event.record.Record;
import spade.reporter.audit.linux.source.audit.event.record.Type;
import spade.reporter.audit.linux.source.audit.event.record.helper.Header;
import spade.reporter.audit.linux.source.audit.event.record.helper.KeyValueParser;
import spade.reporter.audit.linux.type.fs.Inode;
import spade.reporter.audit.linux.type.network.ip.V4;
import spade.reporter.audit.linux.type.network.ip.V6;
import spade.reporter.audit.linux.type.network.transport.Address;

/**
 * Record subclass for USER records with netfilter subtype.
 *
 * Contains network filter hook fields.
 *
 * Raw format:
 *   nf_subtype=nf_netfilter nf_hook=%s nf_priority=%s nf_id=%p
 *   nf_src_ip=%s nf_src_port=%u nf_dst_ip=%s nf_dst_port=%u
 *   nf_protocol=%s nf_ip_version=%s nf_net_ns=%u
 */
public class Netfilter extends Record{

	private static final String NETFILTER_RECORD_KEY = "nf_subtype";
	private static final String MARKER_NF_SUBTYPE = (
		NETFILTER_RECORD_KEY + "=nf_netfilter"
	);

	private final String nfSubtype;
	private final Hook nfHook;
	private final Priority nfPriority;
	private final long nfId;
	private final Address nfSrcAddr;
	private final Address nfDstAddr;
	private final Protocol nfProtocol;
	private final IpVersion nfIpVersion;
	private final Inode nfNetNs;

	public Netfilter(
		final ID id, final String rawRecord
	){
		super(id, Type.NETFILTER, rawRecord);
		final Map<String, String> fields = KeyValueParser.parseKeyValuePairs(rawRecord);
		this.nfSubtype = fields.get(NETFILTER_RECORD_KEY);
		this.nfHook = Hook.parse(fields.get("nf_hook"));
		this.nfPriority = Priority.parse(fields.get("nf_priority"));
		this.nfId = Long.decode(fields.get("nf_id"));
		this.nfIpVersion = IpVersion.parse(fields.get("nf_ip_version"));
		this.nfSrcAddr = parseAddress(nfIpVersion, fields.get("nf_src_ip"), fields.get("nf_src_port"));
		this.nfDstAddr = parseAddress(nfIpVersion, fields.get("nf_dst_ip"), fields.get("nf_dst_port"));
		this.nfProtocol = Protocol.parse(fields.get("nf_protocol"));
		this.nfNetNs = new Inode(Long.parseUnsignedLong(fields.get("nf_net_ns")));
	}

	public String getNfSubtype(){ return nfSubtype; }
	public Hook getNfHook(){ return nfHook; }
	public Priority getNfPriority(){ return nfPriority; }
	public long getNfId(){ return nfId; }
	public Address getNfSrcAddr(){ return nfSrcAddr; }
	public Address getNfDstAddr(){ return nfDstAddr; }
	public Protocol getNfProtocol(){ return nfProtocol; }
	public IpVersion getNfIpVersion(){ return nfIpVersion; }
	public Inode getNfNetNs(){ return nfNetNs; }

	private static Address parseAddress(
			final IpVersion version, final String ipStr, final String portStr){
		if(version == IpVersion.UNKNOWN || ipStr == null || portStr == null){
			return null;
		}
		try{
			final int port = Integer.parseInt(portStr);
			final InetAddress addr = InetAddress.getByName(ipStr);
			if(version == IpVersion.IPV4){
				return new Address(new V4((Inet4Address) addr), port);
			}else{
				return new Address(new V6((Inet6Address) addr), port);
			}
		}catch(final Exception e){
			return null;
		}
	}

	public static class Creator extends Record.Creator{

		@Override
		public String validate(final Header header){
			if(header.getType() != spade.reporter.audit.linux.source.audit.event.record.Type.NETFILTER){
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
