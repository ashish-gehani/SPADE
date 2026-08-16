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
package spade.reporter.audit.process;

import spade.reporter.audit.artifact.ArtifactIdentifier;

public final class FileDescriptor{

	public final ArtifactIdentifier identifier;
	/**
	 * Used to tell whether the artifact was opened for reading or writing
	 * 
	 * 1) true means that it was opened for read
	 * 2) false means that it was opened for write
	 * 3) null means that opened wasn't seen
	 * 
	 * Note: Value of this variable NOT to be used in {@link #equals(Object) equals} or {@link #hashCode() hashCode} function
	 */
	private Boolean wasOpenedForRead = null;
	
	public FileDescriptor(ArtifactIdentifier identifier, Boolean wasOpenedForRead) throws RuntimeException{
		this.identifier = identifier;
		this.wasOpenedForRead = wasOpenedForRead;
		if(this.identifier == null){
			throw new RuntimeException("NULL artifact identifier for file descriptor");
		}
	}
	
	/**
	 * Returns the value of the variable used to tell if the artifact file descriptor was opened for read or write
	 * 
	 * @return true, false, null
	 */
	public Boolean getWasOpenedForRead(){
		return wasOpenedForRead;
	}
	
	/**
	 * Sets whether the artifact was opened for read or write
	 *
	 * @param hasBeenWrittenTo true, false, null
	 */
	public void setWasOpenedForRead(Boolean wasOpenedForRead){
		this.wasOpenedForRead = wasOpenedForRead;
	}
}
