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

public class UnnamedPipeIdentifier extends ArtifactIdentifier{

	private String fd0, fd1;
	private String pid;
	
	public UnnamedPipeIdentifier(String pid, String fd0, String fd1){
		this.pid = pid;
		this.fd0 = fd0;
		this.fd1 = fd1;
	}
	
	public String getPid(){
		return pid;
	}

	public String getFd1() {
		return fd1;
	}

	public String getFd0() {
		return fd0;
	}

	public String getSubtype(){
		return OPMConstants.SUBTYPE_UNNAMED_PIPE;
	}
	
	@Override
	public Map<String, String> getAnnotationsMap() {
		Map<String, String> annotations = new HashMap<String, String>();
		annotations.put(OPMConstants.ARTIFACT_READ_FD, fd0);
		annotations.put(OPMConstants.ARTIFACT_WRITE_FD, fd1);
		annotations.put(OPMConstants.ARTIFACT_PID, pid);
		return annotations;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((fd0 == null) ? 0 : fd0.hashCode());
		result = prime * result + ((fd1 == null) ? 0 : fd1.hashCode());
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
		UnnamedPipeIdentifier other = (UnnamedPipeIdentifier) obj;
		if (fd0 == null) {
			if (other.fd0 != null)
				return false;
		} else if (!fd0.equals(other.fd0))
			return false;
		if (fd1 == null) {
			if (other.fd1 != null)
				return false;
		} else if (!fd1.equals(other.fd1))
			return false;
		if (pid == null) {
			if (other.pid != null)
				return false;
		} else if (!pid.equals(other.pid))
			return false;
		return true;
	}
	
	
}
