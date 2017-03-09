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

public class MemoryIdentifier extends ArtifactIdentifier{
	
	private String memoryAddress;
	private String size;
	private String pid;
	
	public MemoryIdentifier(String pid, String memoryAddress, String size){
		this.memoryAddress = memoryAddress;
		this.size = size;
		this.pid = pid;
	}
	
	public String getMemoryAddress(){
		return memoryAddress;
	}
		
	public String getSize(){
		return size;
	}
	
	public String getPid(){
		return pid;
	}

	@Override
	public Map<String, String> getAnnotationsMap() {
		Map<String, String> annotations = new HashMap<String, String>();
		annotations.put(OPMConstants.ARTIFACT_MEMORY_ADDRESS, memoryAddress);
		annotations.put(OPMConstants.ARTIFACT_SIZE, size);
		annotations.put(OPMConstants.ARTIFACT_PID, pid);
		return annotations;
	}
	
	public String getSubtype(){
		return OPMConstants.SUBTYPE_MEMORY_ADDRESS;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((memoryAddress == null) ? 0 : memoryAddress.hashCode());
		result = prime * result + ((pid == null) ? 0 : pid.hashCode());
		result = prime * result + ((size == null) ? 0 : size.hashCode());
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
		MemoryIdentifier other = (MemoryIdentifier) obj;
		if (memoryAddress == null) {
			if (other.memoryAddress != null)
				return false;
		} else if (!memoryAddress.equals(other.memoryAddress))
			return false;
		if (pid == null) {
			if (other.pid != null)
				return false;
		} else if (!pid.equals(other.pid))
			return false;
		if (size == null) {
			if (other.size != null)
				return false;
		} else if (!size.equals(other.size))
			return false;
		return true;
	}

}
