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

import java.io.Serializable;

import spade.utility.CommonFunctions;

public class ArtifactProperties implements Serializable{
	
	private static final long serialVersionUID = -1299250614232336780L;

	public static final long ARTIFACT_PROPERTY_UNINITIALIZED = -1;
	
	private long version = ARTIFACT_PROPERTY_UNINITIALIZED;

	private long epoch = ARTIFACT_PROPERTY_UNINITIALIZED;
	
	private boolean epochPending = true;
	
	private long creationEventId = ARTIFACT_PROPERTY_UNINITIALIZED;
	
	public void markNewEpoch(long creationEventId){
		this.creationEventId = creationEventId;
		this.epochPending = true;
		this.version = ARTIFACT_PROPERTY_UNINITIALIZED;
	}	
	
	public void markNewEpoch(String creationEventIdString){
		long creationEventId = CommonFunctions.parseLong(creationEventIdString, ARTIFACT_PROPERTY_UNINITIALIZED);
		markNewEpoch(creationEventId);
	}
	
	public long getCreationEventId(){
		return creationEventId;
	}

	//autoincrements if pending true or uninitialized
	public long getEpoch(){
		if(epochPending || epoch == ARTIFACT_PROPERTY_UNINITIALIZED){
			epochPending = false;
			epoch++;
		}
		return epoch;
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
		result = prime * result + (int) (creationEventId ^ (creationEventId >>> 32));
		result = prime * result + (int) (epoch ^ (epoch >>> 32));
		result = prime * result + (epochPending ? 1231 : 1237);
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
		if (creationEventId != other.creationEventId)
			return false;
		if (epoch != other.epoch)
			return false;
		if (epochPending != other.epochPending)
			return false;
		if (version != other.version)
			return false;
		return true;
	}
}