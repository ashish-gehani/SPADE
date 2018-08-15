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

public class UnknownIdentifier extends ArtifactIdentifier{

	private static final long serialVersionUID = 6511655756054136851L;
	private String tgid, fd;
	
	public UnknownIdentifier(String tgid, String fd){
		this.tgid = tgid;
		this.fd = fd;
	}
	
	public String getFD(){
		return fd;
	}

	public String getTgid(){
		return tgid;
	}
	
	@Override
	public Map<String, String> getAnnotationsMap() {
		Map<String, String> annotations = new HashMap<String, String>();
		annotations.put(OPMConstants.ARTIFACT_TGID, tgid);
		annotations.put(OPMConstants.ARTIFACT_FD, fd);
		return annotations;
	}
	
	public String getSubtype(){
		return OPMConstants.SUBTYPE_UNKNOWN;
	}
	
	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + ((fd == null) ? 0 : fd.hashCode());
		result = prime * result + ((tgid == null) ? 0 : tgid.hashCode());
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
		UnknownIdentifier other = (UnknownIdentifier) obj;
		if (fd == null) {
			if (other.fd != null)
				return false;
		} else if (!fd.equals(other.fd))
			return false;
		if (tgid == null) {
			if (other.tgid != null)
				return false;
		} else if (!tgid.equals(other.tgid))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "UnknownIdentifier [tgid=" + tgid + ", fd=" + fd + "]";
	}
}
