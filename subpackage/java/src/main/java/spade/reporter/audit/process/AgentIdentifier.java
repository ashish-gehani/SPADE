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
package spade.reporter.audit.process;

import java.util.HashMap;
import java.util.Map;

import spade.reporter.audit.OPMConstants;
import spade.reporter.audit.VertexIdentifier;

public class AgentIdentifier implements VertexIdentifier{

	private static final long serialVersionUID = 7999238514755805769L;
	public final String uid, euid, gid, egid,
			// Following values optional
			suid, fsuid, sgid, fsgid;
	
	// Simple agent
	public AgentIdentifier(String uid, String euid, String gid, String egid){
		this(uid, euid, gid, egid, null, null, null, null);
	}
	
	// Complete agent
	public AgentIdentifier(String uid, String euid, String gid, String egid, 
			String suid, String fsuid, String sgid, String fsgid){
		this.uid = uid;
		this.euid = euid;
		this.gid = gid;
		this.egid = egid;
		this.suid = suid;
		this.fsuid = fsuid;
		this.sgid = sgid;
		this.fsgid = fsgid;
	}
	
	public Map<String, String> getAnnotationsMap(){
		Map<String, String> map = new HashMap<String, String>();
		map.put(OPMConstants.AGENT_UID, uid);
		map.put(OPMConstants.AGENT_EUID, euid);
		map.put(OPMConstants.AGENT_GID, gid);
		map.put(OPMConstants.AGENT_EGID, egid);
		if(suid != null){
			map.put(OPMConstants.AGENT_SUID, suid);
		}
		if(fsuid != null){
			map.put(OPMConstants.AGENT_FSUID, fsuid);
		}
		if(sgid != null){
			map.put(OPMConstants.AGENT_SGID, sgid);
		}
		if(fsgid != null){
			map.put(OPMConstants.AGENT_FSGID, fsgid);
		}
		return map;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((egid == null) ? 0 : egid.hashCode());
		result = prime * result + ((euid == null) ? 0 : euid.hashCode());
		result = prime * result + ((fsgid == null) ? 0 : fsgid.hashCode());
		result = prime * result + ((fsuid == null) ? 0 : fsuid.hashCode());
		result = prime * result + ((gid == null) ? 0 : gid.hashCode());
		result = prime * result + ((sgid == null) ? 0 : sgid.hashCode());
		result = prime * result + ((suid == null) ? 0 : suid.hashCode());
		result = prime * result + ((uid == null) ? 0 : uid.hashCode());
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
		AgentIdentifier other = (AgentIdentifier) obj;
		if (egid == null) {
			if (other.egid != null)
				return false;
		} else if (!egid.equals(other.egid))
			return false;
		if (euid == null) {
			if (other.euid != null)
				return false;
		} else if (!euid.equals(other.euid))
			return false;
		if (fsgid == null) {
			if (other.fsgid != null)
				return false;
		} else if (!fsgid.equals(other.fsgid))
			return false;
		if (fsuid == null) {
			if (other.fsuid != null)
				return false;
		} else if (!fsuid.equals(other.fsuid))
			return false;
		if (gid == null) {
			if (other.gid != null)
				return false;
		} else if (!gid.equals(other.gid))
			return false;
		if (sgid == null) {
			if (other.sgid != null)
				return false;
		} else if (!sgid.equals(other.sgid))
			return false;
		if (suid == null) {
			if (other.suid != null)
				return false;
		} else if (!suid.equals(other.suid))
			return false;
		if (uid == null) {
			if (other.uid != null)
				return false;
		} else if (!uid.equals(other.uid))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "AgentIdentifier [uid=" + uid + ", euid=" + euid + ", gid=" + gid + ", egid=" + egid + ", suid=" + suid
				+ ", fsuid=" + fsuid + ", sgid=" + sgid + ", fsgid=" + fsgid + "]";
	}
}
