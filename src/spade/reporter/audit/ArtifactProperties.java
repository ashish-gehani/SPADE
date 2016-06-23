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

public class ArtifactProperties implements Serializable{
	
	private static final long serialVersionUID = -1299250614232336780L;

	public static final int UNINITIALIZED = -1;
	
	//used for every artifact except sockets
	private long nonSocketVersion = UNINITIALIZED;
	
	private long socketReadVersion = UNINITIALIZED, socketWriteVersion = UNINITIALIZED;
	
	private long bytesWrittenToSocket = 0, bytesReadFromSocket = 0;

	/**
	 * Returns updated version (if true) or returns 0 if value -1.
	 * Returns 0 because versions start from 0 in audit
	 * 
	 */
	private long getNonSocketVersion(boolean update) {
		if(update || nonSocketVersion == -1){
			nonSocketVersion++;
		}
		return nonSocketVersion;
	}
	
	private long getNonSocketVersion() {
		return nonSocketVersion;
	}
	
	public long getFileVersion(boolean update){
		return getNonSocketVersion(update);
	}
	
	public long getFileVersion(){
		return getNonSocketVersion();
	}
	
	public long getPipeVersion(boolean update){
		return getNonSocketVersion(update);
	}
	
	public long getPipeVersion(){
		return getNonSocketVersion();
	}
	
	public long getMemoryVersion(boolean update){
		return getNonSocketVersion(update);
	}
	
	public long getMemoryVersion(){
		return getNonSocketVersion();
	}
	
	public long getUnknownVersion(boolean update){
		return getNonSocketVersion(update);
	}
	
	public long getUnknownVersion(){
		return getNonSocketVersion();
	}

	public void setNonSocketVersion(long nonSocketVersion) {
		this.nonSocketVersion = nonSocketVersion;
	}

	/**
	 * Returns updated version (if true) or returns 0 if value -1.
	 * Returns 0 because versions start from 0 in audit
	 */
	public long getSocketReadVersion(boolean update) {
		if(update || socketReadVersion == -1){
			socketReadVersion++;
		}
		return socketReadVersion;
	}
	
	public long getSocketReadVersion() {
		return socketReadVersion;
	}

	public void setSocketReadVersion(long socketReadVersion) {
		this.socketReadVersion = socketReadVersion;
	}

	/**
	 * Returns updated version (if true) or returns 0 if value -1.
	 * Returns 0 because versions start from 0 in audit
	 */
	public long getSocketWriteVersion(boolean update) {
		if(update || socketWriteVersion == -1){
			socketWriteVersion++;
		}
		return socketWriteVersion;
	}
	
	public long getSocketWriteVersion() {
		return socketWriteVersion;
	}

	public void setSocketWriteVersion(long socketWriteVersion) {
		this.socketWriteVersion = socketWriteVersion;
	}

	public long getBytesWrittenToSocket() {
		return bytesWrittenToSocket;
	}

	public void setBytesWrittenToSocket(long bytesWrittenToSocket) {
		this.bytesWrittenToSocket = bytesWrittenToSocket;
	}

	public long getBytesReadFromSocket() {
		return bytesReadFromSocket;
	}

	public void setBytesReadFromSocket(long bytesReadFromSocket) {
		this.bytesReadFromSocket = bytesReadFromSocket;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (bytesReadFromSocket ^ (bytesReadFromSocket >>> 32));
		result = prime * result + (int) (bytesWrittenToSocket ^ (bytesWrittenToSocket >>> 32));
		result = prime * result + (int) (nonSocketVersion ^ (nonSocketVersion >>> 32));
		result = prime * result + (int) (socketReadVersion ^ (socketReadVersion >>> 32));
		result = prime * result + (int) (socketWriteVersion ^ (socketWriteVersion >>> 32));
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
		if (bytesReadFromSocket != other.bytesReadFromSocket)
			return false;
		if (bytesWrittenToSocket != other.bytesWrittenToSocket)
			return false;
		if (nonSocketVersion != other.nonSocketVersion)
			return false;
		if (socketReadVersion != other.socketReadVersion)
			return false;
		if (socketWriteVersion != other.socketWriteVersion)
			return false;
		return true;
	}	
	
}