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

public abstract class FdPairIdentifier extends ArtifactIdentifier {

	private static final long serialVersionUID = -4930748608565367219L;
	
	public final String tgid, fd0, fd1;
	
	public FdPairIdentifier(String tgid, String fd0, String fd1){
		this.tgid = tgid;
		this.fd0 = fd0;
		this.fd1 = fd1;
	}
	
	@Override
	public Map<String, String> getAnnotationsMap(){
		Map<String, String> map = new HashMap<String, String>();
		map.put(OPMConstants.ARTIFACT_TGID, String.valueOf(tgid));
		return map;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((fd0 == null) ? 0 : fd0.hashCode());
		result = prime * result + ((fd1 == null) ? 0 : fd1.hashCode());
		result = prime * result + ((tgid == null) ? 0 : tgid.hashCode());
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
		FdPairIdentifier other = (FdPairIdentifier) obj;
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
		if (tgid == null) {
			if (other.tgid != null)
				return false;
		} else if (!tgid.equals(other.tgid))
			return false;
		return true;
	}

	public abstract String toString();
}
