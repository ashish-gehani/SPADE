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
}
