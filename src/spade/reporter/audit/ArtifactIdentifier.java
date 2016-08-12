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

import java.util.Map;

/**
 * A class to be extended by all artifact types to be used in the Audit reporter.
 * 
 * Used to build annotations specific to an artifact and the subtype of the artifact.
 * 
 */
public abstract class ArtifactIdentifier {
	
	public static final String SUBTYPE_FILE = "file",
								SUBTYPE_SOCKET = "network",
								SUBTYPE_MEMORY = "memory",
								SUBTYPE_PIPE = "pipe",
								SUBTYPE_UNKNOWN = "unknown";
	
	/**
	 * Used to tell whether the artifact has been written to
	 * Note: Value of this variable NOT to be used in {@link #equals(Object) equals} or {@link #hashCode() hashCode} function
	 */
	private boolean hasBeenWrittenTo = false;
	
	/**
	 * Returns the value of the variable used to tell if the artifact file descriptor was written to
	 * 
	 * @return true, false
	 */
	public boolean hasBeenWrittenTo(){
		return hasBeenWrittenTo;
	}
	
	/**
	 * Sets whether the file descriptor was written to
	 *
	 * @param hasBeenWrittenTo true, false
	 */
	public void setHasBeenWrittenTo(boolean hasBeenWrittenTo){
		this.hasBeenWrittenTo = hasBeenWrittenTo;
	}
	
	/**
	 * Returns the annotations to add to the OPM Artifact
	 * 
	 * @return map of annotations
	 */
	public abstract Map<String, String> getAnnotationsMap();
	
	/**
	 * Returns subtype of the artifact
	 * 
	 * Must return one of the values: file, network, memory, pipe, unknown
	 * 
	 * @return file, network, memory, pipe, unknown
	 */
	public abstract String getSubtype();
		
}
