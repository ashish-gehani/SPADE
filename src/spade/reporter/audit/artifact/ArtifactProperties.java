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
import java.util.HashSet;
import java.util.Set;

public class ArtifactProperties implements Serializable{
	
	private static final long serialVersionUID = -1299250614232336780L;

	public static final long ARTIFACT_PROPERTY_UNINITIALIZED = -1;
	
	private long version = ARTIFACT_PROPERTY_UNINITIALIZED;

	private long epoch = ARTIFACT_PROPERTY_UNINITIALIZED;
	
	private boolean epochPending = true;
	
	/**
	 * Used to tell if this permission was seen before or not. Needed when versions are off
	 * or the artifact isn't being version on change of permissions
	 */
	private Set<String> seenPermissions = new HashSet<String>();
	/**
	 * Current permissions set
	 */
	private String currentPermissions = null;
	
	/**
	 * Used to figure out if the artifact has been seen before or not IF
	 * the decision comes down to permissions
	 * @return true/false
	 */
	public boolean isPermissionsUninitialized(){
		return !seenPermissions.contains(String.valueOf(ARTIFACT_PROPERTY_UNINITIALIZED));
	}
	
	/**
	 * Used to mark that this artifact has been seen before and must always be called if it 
	 * hasn't been called
	 */
	public void initializePermissions(){
		seenPermissions.add(String.valueOf(ARTIFACT_PROPERTY_UNINITIALIZED));
	}
	
	public void clearAllPermissionsExceptCurrent(){
		seenPermissions.clear();
		initializePermissions();
		if(currentPermissions != null){
			seenPermissions.add(currentPermissions);
		}
	}

	/**
	 * Sets the current permissions and as well as adds the permission to the set of seen 
	 * permissions
	 * @param permissions permissions
	 */
	public void setCurrentPermissions(String permissions){
		this.currentPermissions = permissions;
		seenPermissions.add(permissions);
	}
	
	public String getCurrentPermissions(){
		return currentPermissions;
	}
	
	public boolean permissionsSeenBefore(String permissions){
		return seenPermissions.contains(permissions);
	}
		
	public void markNewEpoch(){
		this.epochPending = true;
		this.version = ARTIFACT_PROPERTY_UNINITIALIZED;
		this.currentPermissions = null;
		this.seenPermissions.clear();
	}
	
	//autoincrements if pending true or uninitialized
	public long getEpoch(){
		if(epochPending || epoch == ARTIFACT_PROPERTY_UNINITIALIZED){
			epochPending = false;
			epoch++;
		}
		return epoch;
	}
	
	public boolean isEpochUninitialized(){
		return epoch == ARTIFACT_PROPERTY_UNINITIALIZED;
	}
	
	public boolean isEpochPending(){
		return epochPending;
	}
	
	public long getVersion(boolean increment){
		if(increment || version == ARTIFACT_PROPERTY_UNINITIALIZED){
			version++;
		}
		return version;
	}
	
	public boolean isVersionUninitialized(){
		return version == ARTIFACT_PROPERTY_UNINITIALIZED;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((currentPermissions == null) ? 0 : currentPermissions.hashCode());
		result = prime * result + (int) (epoch ^ (epoch >>> 32));
		result = prime * result + (epochPending ? 1231 : 1237);
		result = prime * result + ((seenPermissions == null) ? 0 : seenPermissions.hashCode());
		result = prime * result + (int) (version ^ (version >>> 32));
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
		ArtifactProperties other = (ArtifactProperties) obj;
		if (currentPermissions == null) {
			if (other.currentPermissions != null)
				return false;
		} else if (!currentPermissions.equals(other.currentPermissions))
			return false;
		if (epoch != other.epoch)
			return false;
		if (epochPending != other.epochPending)
			return false;
		if (seenPermissions == null) {
			if (other.seenPermissions != null)
				return false;
		} else if (!seenPermissions.equals(other.seenPermissions))
			return false;
		if (version != other.version)
			return false;
		return true;
	}

}
