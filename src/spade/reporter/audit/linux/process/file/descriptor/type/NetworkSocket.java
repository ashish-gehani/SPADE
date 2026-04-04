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
package spade.reporter.audit.linux.process.file.descriptor.type;

public class NetworkSocket extends Descriptor{

	public final String localHost;
	public final String localPort;
	public final String remoteHost;
	public final String remotePort;
	public final String protocol;
	public final String netNamespaceId;

	public NetworkSocket(
		final String localHost,
		final String localPort,
		final String remoteHost,
		final String remotePort,
		final String protocol,
		final String netNamespaceId
	){
		super(Type.NETWORK_SOCKET);
		if(localHost == null){
			throw new IllegalArgumentException("localHost cannot be NULL");
		}
		if(localPort == null){
			throw new IllegalArgumentException("localPort cannot be NULL");
		}
		if(remoteHost == null){
			throw new IllegalArgumentException("remoteHost cannot be NULL");
		}
		if(remotePort == null){
			throw new IllegalArgumentException("remotePort cannot be NULL");
		}
		if(protocol == null){
			throw new IllegalArgumentException("protocol cannot be NULL");
		}
		if(netNamespaceId == null){
			throw new IllegalArgumentException("netNamespaceId cannot be NULL");
		}
		this.localHost = localHost;
		this.localPort = localPort;
		this.remoteHost = remoteHost;
		this.remotePort = remotePort;
		this.protocol = protocol;
		this.netNamespaceId = netNamespaceId;
	}

}
