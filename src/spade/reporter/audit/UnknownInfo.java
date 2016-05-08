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

public class UnknownInfo implements ArtifactInfo{

	private String pid, fd;
	
	public UnknownInfo(String pid, String fd){
		this.pid = pid;
		this.fd = fd;
	}
	
	public String getFD(){
		return fd;
	}

	public String getPID(){
		return pid;
	}
	
	public String getStringFormattedValue(){
		return "/pid/"+pid+"fd/"+fd;
	}
	
	public String getSubtype(){
		return SUBTYPE_UNKNOWN;
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
		UnknownInfo other = (UnknownInfo) obj;
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
}
