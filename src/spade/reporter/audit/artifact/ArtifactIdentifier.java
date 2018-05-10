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

import java.util.Map;

import spade.reporter.audit.VertexIdentifier;

/**
 * A class to be extended by all artifact types to be used in the Audit reporter.
 * 
 * Used to build annotations specific to an artifact and the subtype of the artifact.
 * 
 */
public abstract class ArtifactIdentifier implements VertexIdentifier{
	
	private static final long serialVersionUID = 4429967254473060156L;
	/**
	 * Used to tell whether the artifact was opened for reading or writing
	 * 
	 * 1) true means that it was opened for read
	 * 2) false means that it was opened for write
	 * 3) null means that opened wasn't seen
	 * 
	 * Note: Value of this variable NOT to be used in {@link #equals(Object) equals} or {@link #hashCode() hashCode} function
	 */
	private Boolean openedForRead = null;
	
	/**
	 * Returns the value of the variable used to tell if the artifact file descriptor was opened for read or write
	 * 
	 * @return true, false, null
	 */
	public Boolean wasOpenedForRead(){
		return openedForRead;
	}
	
	/**
	 * Sets whether the artifact was opened for read or write
	 *
	 * @param hasBeenWrittenTo true, false, null
	 */
	public void setOpenedForRead(Boolean openedForRead){
		this.openedForRead = openedForRead;
	}
	
	/**
	 * Returns subtype of the artifact
	 * 
	 * Must return one of the values: file, network, memory, pipe, unknown
	 * 
	 * @return file, network, memory, pipe, unknown
	 */
	public abstract String getSubtype();
	
	@Override
	public int hashCode(){
		int hashcode = 0;
		int i = 23;
		hashcode += getAnnotationsMap() == null ? i : getAnnotationsMap().hashCode()*i;
		i = 27;
		hashcode += getSubtype() == null ? i : getSubtype().hashCode()*i;
		return hashcode;
	}
		
	@Override
	public boolean equals(Object object){
		if(object != null){
			if(object.getClass().equals(this.getClass())){
				ArtifactIdentifier that = (ArtifactIdentifier)object;
				Map<String, String> thatAnnotations = that.getAnnotationsMap();
				Map<String, String> thisAnnotations = this.getAnnotationsMap();
				String thatSubtype = that.getSubtype();
				String thisSubtype = this.getSubtype();
				if((thatAnnotations == null && thisAnnotations == null)
						|| (thatAnnotations.equals(thisAnnotations)) //what if one is null. NPE!
						&& ((thatSubtype == null && thisSubtype == null) 
								|| thatSubtype.equals(thisSubtype))){
					return true;
				}
			}
		}
		return false;
	}
}
