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
package spade.reporter.audit.linux.platform.process.fd.descriptor;

import spade.reporter.audit.linux.platform.process.fd.Num;
import spade.reporter.audit.linux.platform.resource.network.Network;

public class NetworkSocket extends Descriptor{

	private final Network network;

	public NetworkSocket(
		final Num num,
		final Network network
	){
		super(Type.NETWORK_SOCKET, num);
		if(network == null){
			throw new IllegalArgumentException("network cannot be NULL");
		}
		this.network = network;
	}

	public Network getNetwork(){
		return network;
	}

}
