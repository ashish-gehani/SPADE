/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International

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

public abstract class SystemVArtifactIdentifier extends ArtifactIdentifier{

	private static final long serialVersionUID = -8759598433244295364L;

	public final String id;
	public final String ouid, ogid;
	
	public final String ipcNamespace;

	public SystemVArtifactIdentifier(final String id, final String ouid, final String ogid,
			final String ipcNamespace){
		this.id = id;
		this.ouid = ouid;
		this.ogid = ogid;
		this.ipcNamespace = ipcNamespace;
	}

	@Override
	public Map<String, String> getAnnotationsMap(){
		final Map<String, String> annotations = new HashMap<String, String>();
		annotations.put(OPMConstants.ARTIFACT_SYSV_ID, id);
		annotations.put(OPMConstants.ARTIFACT_SYSV_OUID, ouid);
		annotations.put(OPMConstants.ARTIFACT_SYSV_OGID, ogid);
		if(ipcNamespace != null){
			annotations.put(OPMConstants.PROCESS_IPC_NAMESPACE, ipcNamespace);	
		}
		return annotations;
	}

	@Override
	public int hashCode(){
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((ogid == null) ? 0 : ogid.hashCode());
		result = prime * result + ((ouid == null) ? 0 : ouid.hashCode());
		result = prime * result + ((ipcNamespace == null) ? 0 : ipcNamespace.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj){
		if(this == obj)
			return true;
		if(!super.equals(obj))
			return false;
		if(getClass() != obj.getClass())
			return false;
		SystemVArtifactIdentifier other = (SystemVArtifactIdentifier)obj;
		if(id == null){
			if(other.id != null)
				return false;
		}else if(!id.equals(other.id))
			return false;
		if(ogid == null){
			if(other.ogid != null)
				return false;
		}else if(!ogid.equals(other.ogid))
			return false;
		if(ouid == null){
			if(other.ouid != null)
				return false;
		}else if(!ouid.equals(other.ouid))
			return false;
		if(ipcNamespace == null){
			if(other.ipcNamespace != null)
				return false;
		}else if(!ipcNamespace.equals(other.ipcNamespace))
			return false;
		return true;
	}

	@Override
	public abstract String toString();

}
