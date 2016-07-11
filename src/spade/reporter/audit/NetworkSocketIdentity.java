/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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

package spade.reporter.audit;

import java.util.HashMap;
import java.util.Map;

public class NetworkSocketIdentity implements ArtifactIdentity {

	private String sourceHost, sourcePort,
					destinationHost, destinationPort,
					protocol;

	public NetworkSocketIdentity(String sourceHost, String sourcePort, String destinationHost, String destinationPort, String protocol){
		this.sourceHost = sourceHost;
		this.sourcePort = sourcePort;
		this.destinationHost = destinationHost;
		this.destinationPort = destinationPort;
		this.protocol = protocol;
	}

	public String getSourceHost() {
		return sourceHost;
	}

	public String getSourcePort() {
		return sourcePort;
	}

	public String getDestinationHost() {
		return destinationHost;
	}

	public String getDestinationPort() {
		return destinationPort;
	}

	public String getProtocol() {
		return protocol;
	}

	@Override
	public Map<String, String> getAnnotationsMap() {
		Map<String, String> annotations = new HashMap<String, String>();
		annotations.put("source address", sourceHost);
		annotations.put("source port", sourcePort);
		annotations.put("destination address", destinationHost);
		annotations.put("destination port", destinationPort);
//		annotations.put("protocol", protocol);
		return annotations;
	}

	public String getSubtype(){
		return SUBTYPE_SOCKET;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((destinationHost == null) ? 0 : destinationHost.hashCode());
		result = prime * result + ((destinationPort == null) ? 0 : destinationPort.hashCode());
		result = prime * result + ((protocol == null) ? 0 : protocol.hashCode());
		result = prime * result + ((sourceHost == null) ? 0 : sourceHost.hashCode());
		result = prime * result + ((sourcePort == null) ? 0 : sourcePort.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		NetworkSocketIdentity other = (NetworkSocketIdentity) obj;
		if (destinationHost == null) {
			if (other.destinationHost != null)
				return false;
		} else if (!destinationHost.equals(other.destinationHost))
			return false;
		if (destinationPort == null) {
			if (other.destinationPort != null)
				return false;
		} else if (!destinationPort.equals(other.destinationPort))
			return false;
		if (protocol == null) {
			if (other.protocol != null)
				return false;
		} else if (!protocol.equals(other.protocol))
			return false;
		if (sourceHost == null) {
			if (other.sourceHost != null)
				return false;
		} else if (!sourceHost.equals(other.sourceHost))
			return false;
		if (sourcePort == null) {
			if (other.sourcePort != null)
				return false;
		} else if (!sourcePort.equals(other.sourcePort))
			return false;
		return true;
	}
	
	
}
