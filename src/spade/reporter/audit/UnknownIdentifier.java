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

public class UnknownIdentifier extends ArtifactIdentifier{

	private String pid, fd;
	
	public UnknownIdentifier(String pid, String fd){
		this.pid = pid;
		this.fd = fd;
	}
	
	public String getFD(){
		return fd;
	}

	public String getPID(){
		return pid;
	}
	
	@Override
	public Map<String, String> getAnnotationsMap() {
		Map<String, String> annotations = new HashMap<String, String>();
		annotations.put(OPMConstants.ARTIFACT_PID, pid);
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
		result = prime * result + ((pid == null) ? 0 : pid.hashCode());
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
		if (pid == null) {
			if (other.pid != null)
				return false;
		} else if (!pid.equals(other.pid))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "UnknownIdentifier [pid=" + pid + ", fd=" + fd + "]";
	}
}
