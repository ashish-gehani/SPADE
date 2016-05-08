/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2016 SRI International

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

public class MemoryInfo implements ArtifactInfo{
	
	private String memoryAddress;
	
	public MemoryInfo(String memoryAddress){
		this.memoryAddress = memoryAddress;
	}
	
	public String getMemoryAddress(){
		return memoryAddress;
	}
	
	@Override
	public String getStringFormattedValue() {
		return "0x"+memoryAddress;
	}
	
	public String getSubtype(){
		return SUBTYPE_MEMORY;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((memoryAddress == null) ? 0 : memoryAddress.hashCode());
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
		MemoryInfo other = (MemoryInfo) obj;
		if (memoryAddress == null) {
			if (other.memoryAddress != null)
				return false;
		} else if (!memoryAddress.equals(other.memoryAddress))
			return false;
		return true;
	}

}
