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

import java.util.Map;

import spade.reporter.audit.OPMConstants;

public class UnnamedNetworkSocketPairIdentifier extends FdPairIdentifier{

	private static final long serialVersionUID = -7375847456130740800L;
	
	public final String protocol;
	
	public UnnamedNetworkSocketPairIdentifier(String tgid, String fd0, String fd1, String protocol){
		super(tgid, fd0, fd1);
		this.protocol = protocol;
	}

	@Override
	public Map<String, String> getAnnotationsMap(){
		Map<String, String> map = super.getAnnotationsMap();
		map.put(OPMConstants.ARTIFACT_FD0, String.valueOf(fd0));
		map.put(OPMConstants.ARTIFACT_FD1, String.valueOf(fd1));
		map.put(OPMConstants.ARTIFACT_PROTOCOL, String.valueOf(protocol));
		return map;
	}

	@Override
	public String getSubtype() {
		return OPMConstants.SUBTYPE_UNNAMED_NETWORK_SOCKET_PAIR;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((protocol == null) ? 0 : protocol.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		UnnamedNetworkSocketPairIdentifier other = (UnnamedNetworkSocketPairIdentifier) obj;
		if (protocol == null) {
			if (other.protocol != null)
				return false;
		} else if (!protocol.equals(other.protocol))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "UnnamedNetworkSocketPairIdentifier [protocol=" + protocol + ", tgid=" + tgid + ", fd0=" + fd0 + ", fd1="
				+ fd1 + "]";
	}
}
