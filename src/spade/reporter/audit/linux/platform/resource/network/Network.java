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
package spade.reporter.audit.linux.platform.resource.network;

import spade.reporter.audit.linux.platform.resource.Resource;
import spade.reporter.audit.linux.platform.type.network.transport.Address;
import spade.reporter.audit.linux.platform.type.network.transport.Protocol;

public class Network extends Resource {

	private final Address src;
	private final Address dst;
	private final Protocol protocol;

	public Network(
			final Protocol protocol,
			final Address src,
			final Address dst) {
		super(
			spade.reporter.audit.linux.platform.resource.Type.NETWORK
		);
		if (protocol == null) {
			throw new IllegalArgumentException("protocol cannot be NULL");
		}
		if (src == null) {
			throw new IllegalArgumentException("src cannot be NULL");
		}
		if (dst == null) {
			throw new IllegalArgumentException("dst cannot be NULL");
		}
		this.protocol = protocol;
		this.src = src;
		this.dst = dst;
	}

	public Protocol getProtocol() {
		return protocol;
	}

	public Address getSrc() {
		return src;
	}

	public Address getDst() {
		return dst;
	}

}
