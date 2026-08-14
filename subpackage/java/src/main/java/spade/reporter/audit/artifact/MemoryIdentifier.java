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

public class MemoryIdentifier extends ArtifactIdentifier{
	
	private static final long serialVersionUID = -4838164396726143615L;
	private String memoryAddress;
	private String size;
	private String tgid;
	
	public MemoryIdentifier(String tgid, String memoryAddress, String size){
		this.memoryAddress = memoryAddress;
		this.size = size;
		this.tgid = tgid;
	}
	
	public String getMemoryAddress(){
		return memoryAddress;
	}
		
	public String getSize(){
		return size;
	}
	
	public String getTgid(){
		return tgid;
	}

	@Override
	public Map<String, String> getAnnotationsMap() {
		Map<String, String> annotations = new HashMap<String, String>();
		annotations.put(OPMConstants.ARTIFACT_MEMORY_ADDRESS, memoryAddress);
		annotations.put(OPMConstants.ARTIFACT_SIZE, size);
		annotations.put(OPMConstants.ARTIFACT_TGID, tgid);
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
		result = prime * result + ((tgid == null) ? 0 : tgid.hashCode());
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
		if (tgid == null) {
			if (other.tgid != null)
				return false;
		} else if (!tgid.equals(other.tgid))
			return false;
		if (size == null) {
			if (other.size != null)
				return false;
		} else if (!size.equals(other.size))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "MemoryIdentifier [memoryAddress=" + memoryAddress + ", size=" + size + ", tgid=" + tgid + "]";
	}

}
