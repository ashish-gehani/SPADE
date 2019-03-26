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

import java.io.Serializable;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

public class ArtifactState implements Serializable{

	private static final long serialVersionUID = 909107620949664529L;
	
	private boolean hasBeenPut = false;
	private BigInteger epoch = BigInteger.ZERO, version = BigInteger.ZERO, lastPutEpoch, lastPutVersion;
	private String permissions, lastPutPermissions;
	private Set<String> previousPutPermissions = new HashSet<String>();
	
	public ArtifactState(){}
	
	public ArtifactState(boolean hasBeenPut, BigInteger epoch, BigInteger version, BigInteger lastPutEpoch, BigInteger lastPutVersion,
			String permissions, String lastPutPermissions, Set<String> previousPutPermissions){
		this.hasBeenPut = hasBeenPut;
		this.epoch = epoch;
		this.version = version;
		this.lastPutEpoch = lastPutEpoch;
		this.lastPutVersion = lastPutVersion;
		this.permissions = permissions;
		this.lastPutPermissions = lastPutPermissions;
		this.previousPutPermissions = previousPutPermissions;
	}
	
	// Resets version to initial value
	// Resets permissions to null
	public void incrementEpoch(){
		epoch = epoch.add(BigInteger.ONE);
		resetVersion();
		resetPermissions();
		hasBeenPut = false;
	}
	
	private void resetVersion(){
		version = BigInteger.ZERO;
		lastPutVersion = null;
	}
	
	public void incrementVersion(){
		version = version.add(BigInteger.ONE);
		String currentPermissions = permissions;
		String currentLastPutPermissions = lastPutPermissions;
		resetPermissions();
		permissions = currentPermissions;
		lastPutPermissions = currentLastPutPermissions;
		hasBeenPut = false;
	}
	
	private void resetPermissions(){
		permissions = null;
		lastPutPermissions = null;
		previousPutPermissions.clear();
	}
	
	public void updatePermissions(String permissions){
		this.permissions = permissions;
		hasBeenPut = previousPutPermissions.contains(permissions);
	}
	
	public void put(){
		hasBeenPut = true;
		lastPutEpoch = epoch;
		lastPutVersion = version;
		lastPutPermissions = permissions;
		previousPutPermissions.add(permissions);
	}
	
	public Set<String> getPreviousPutPermissions(){
		return previousPutPermissions;
	}
	
	public boolean hasBeenPut(){
		return hasBeenPut;
	}
	
	public BigInteger getEpoch(){
		return epoch;
	}
	
	public BigInteger getLastPutEpoch(){
		return lastPutEpoch;
	}
	
	public BigInteger getVersion(){
		return version;
	}
	
	public BigInteger getLastPutVersion(){
		return lastPutVersion;
	}
	
	public String getPermissions(){
		return permissions;
	}
	
	public String getLastPutPermissions(){
		return lastPutPermissions;
	}

	@Override
	public String toString(){
		return "ArtifactState [hasBeenPut=" + hasBeenPut + ", epoch=" + epoch + ", version=" + version
				+ ", lastPutEpoch=" + lastPutEpoch + ", lastPutVersion=" + lastPutVersion + ", permissions="
				+ permissions + ", lastPutPermissions=" + lastPutPermissions + ", previousPutPermissions="
				+ previousPutPermissions + "]";
	}

	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + ((epoch == null) ? 0 : epoch.hashCode());
		result = prime * result + (hasBeenPut ? 1231 : 1237);
		result = prime * result + ((lastPutEpoch == null) ? 0 : lastPutEpoch.hashCode());
		result = prime * result + ((lastPutPermissions == null) ? 0 : lastPutPermissions.hashCode());
		result = prime * result + ((lastPutVersion == null) ? 0 : lastPutVersion.hashCode());
		result = prime * result + ((permissions == null) ? 0 : permissions.hashCode());
		result = prime * result + ((previousPutPermissions == null) ? 0 : previousPutPermissions.hashCode());
		result = prime * result + ((version == null) ? 0 : version.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj){
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		ArtifactState other = (ArtifactState) obj;
		if(epoch == null){
			if(other.epoch != null)
				return false;
		}else if(!epoch.equals(other.epoch))
			return false;
		if(hasBeenPut != other.hasBeenPut)
			return false;
		if(lastPutEpoch == null){
			if(other.lastPutEpoch != null)
				return false;
		}else if(!lastPutEpoch.equals(other.lastPutEpoch))
			return false;
		if(lastPutPermissions == null){
			if(other.lastPutPermissions != null)
				return false;
		}else if(!lastPutPermissions.equals(other.lastPutPermissions))
			return false;
		if(lastPutVersion == null){
			if(other.lastPutVersion != null)
				return false;
		}else if(!lastPutVersion.equals(other.lastPutVersion))
			return false;
		if(permissions == null){
			if(other.permissions != null)
				return false;
		}else if(!permissions.equals(other.permissions))
			return false;
		if(previousPutPermissions == null){
			if(other.previousPutPermissions != null)
				return false;
		}else if(!previousPutPermissions.equals(other.previousPutPermissions))
			return false;
		if(version == null){
			if(other.version != null)
				return false;
		}else if(!version.equals(other.version))
			return false;
		return true;
	}
	
}
