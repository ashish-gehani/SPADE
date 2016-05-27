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

public class ArtifactProperties{
	
	//used for every artifact except sockets
	private long nonSocketVersion = -1;
	
	private long socketReadVersion = -1, socketWriteVersion = -1;
	
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
	
}