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

package spade.reporter.audit.artifact;

import java.util.HashMap;
import java.util.Map;

import spade.reporter.audit.OPMConstants;

public class NetworkSocketIdentifier extends ArtifactIdentifier {

	private static final long serialVersionUID = -431788671179917399L;
	private String localHost, localPort,
					remoteHost, remotePort,
					protocol;

	public NetworkSocketIdentifier(String localHost, String localPort, 
			String remoteHost, String remotePort, String protocol){
		this.localHost = localHost;
		this.localPort = localPort;
		this.remoteHost = remoteHost;
		this.remotePort = remotePort;
		this.protocol = protocol;
	}

	public String getLocalHost() {
		return localHost;
	}

	public String getLocalPort() {
		return localPort;
	}

	public String getRemoteHost() {
		return remoteHost;
	}

	public String getRemotePort() {
		return remotePort;
	}

	public String getProtocol() {
		return protocol;
	}

	@Override
	public Map<String, String> getAnnotationsMap() {
		Map<String, String> annotations = new HashMap<String, String>();
		annotations.put(OPMConstants.ARTIFACT_LOCAL_ADDRESS, localHost == null ? "" : localHost);
		annotations.put(OPMConstants.ARTIFACT_LOCAL_PORT, localPort == null ? "" : localPort);
		annotations.put(OPMConstants.ARTIFACT_REMOTE_ADDRESS, remoteHost == null ? "" : remoteHost);
		annotations.put(OPMConstants.ARTIFACT_REMOTE_PORT, remotePort == null ? "" : remotePort);
		annotations.put(OPMConstants.ARTIFACT_PROTOCOL, protocol == null ? "" : protocol);
		return annotations;
	}

	public String getSubtype(){
		return OPMConstants.SUBTYPE_NETWORK_SOCKET;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((remoteHost == null) ? 0 : remoteHost.hashCode());
		result = prime * result + ((remotePort == null) ? 0 : remotePort.hashCode());
		result = prime * result + ((protocol == null) ? 0 : protocol.hashCode());
		result = prime * result + ((localHost == null) ? 0 : localHost.hashCode());
		result = prime * result + ((localPort == null) ? 0 : localPort.hashCode());
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
		NetworkSocketIdentifier other = (NetworkSocketIdentifier) obj;
		if (remoteHost == null) {
			if (other.remoteHost != null)
				return false;
		} else if (!remoteHost.equals(other.remoteHost))
			return false;
		if (remotePort == null) {
			if (other.remotePort != null)
				return false;
		} else if (!remotePort.equals(other.remotePort))
			return false;
		if (protocol == null) {
			if (other.protocol != null)
				return false;
		} else if (!protocol.equals(other.protocol))
			return false;
		if (localHost == null) {
			if (other.localHost != null)
				return false;
		} else if (!localHost.equals(other.localHost))
			return false;
		if (localPort == null) {
			if (other.localPort != null)
				return false;
		} else if (!localPort.equals(other.localPort))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "NetworkSocketIdentifier [localHost=" + localHost + ", localPort=" + localPort + ", remoteHost="
				+ remoteHost + ", remotePort=" + remotePort + ", protocol=" + protocol + "]";
	}
	
}
