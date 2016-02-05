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

public class PipeInfo implements ArtifactInfo{

	private String fd1, fd2;
	
	public PipeInfo(String fd1, String fd2) {
		this.fd1 = fd1;
		this.fd2 = fd2;
	}

	public String getFd1() {
		return fd1;
	}

	public String getFd2() {
		return fd2;
	}
	
	public String getStringFormattedValue(){
		return "pipe:["+fd1+"-"+fd2+"]";
	}

	public String getSubtype(){
		return SUBTYPE_PIPE;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((fd1 == null) ? 0 : fd1.hashCode());
		result = prime * result + ((fd2 == null) ? 0 : fd2.hashCode());
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
		PipeInfo other = (PipeInfo) obj;
		if (fd1 == null) {
			if (other.fd1 != null)
				return false;
		} else if (!fd1.equals(other.fd1))
			return false;
		if (fd2 == null) {
			if (other.fd2 != null)
				return false;
		} else if (!fd2.equals(other.fd2))
			return false;
		return true;
	}	
}
