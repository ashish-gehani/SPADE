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
