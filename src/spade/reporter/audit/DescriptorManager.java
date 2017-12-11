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

import java.util.HashMap;
import java.util.Map;

public class DescriptorManager {

	/**
	 * Map of process id to file descriptor to the artifact identifier
	 */
	private Map<String, Map<String, ArtifactIdentifier>> descriptors = new HashMap<>();
		
	/**
	 * Adds an artifact identifier against the pid, fd combination
	 * 
	 * @param pid process id
	 * @param fd file descriptor number
	 * @param artifactIdentifier ArtifactIdentifier subclass
	 * @param wasOpenedForRead null, true, false. Whether the artifact was opened for read or write
	 */
	public void addDescriptor(String pid, String fd, ArtifactIdentifier artifactIdentifier, Boolean wasOpenedForRead){
		if(artifactIdentifier == null){
			return;
		}
		if(descriptors.get(pid) == null){
			descriptors.put(pid, new HashMap<String, ArtifactIdentifier>());
		}
		descriptors.get(pid).put(fd, artifactIdentifier);
		artifactIdentifier.setOpenedForRead(wasOpenedForRead);
	}
	
	/**
	 * Copies all artifact identifiers from the given map to the given process id
	 * 
	 * @param pid process id
	 * @param newDescriptors descriptors to add the given process id
	 */
	public void addDescriptors(String pid, Map<String, ArtifactIdentifier> newDescriptors){
		if(descriptors.get(pid) == null){
			descriptors.put(pid, new HashMap<String, ArtifactIdentifier>());
		}
		descriptors.get(pid).putAll(newDescriptors);
	}
	
	/**
	 * Remove descriptors from internal map for the given process id
	 * 
	 * @param pid process id
	 */
	public void removeDescriptorsOf(String pid){
		descriptors.remove(pid);
	}
	
	/**
	 * Removes the descriptor if it exists and returns that
	 * 
	 * @param pid process id
	 * @param fd file descriptor number
	 * @return ArtifactIdentifier subclass instance or null
	 */
	public ArtifactIdentifier removeDescriptor(String pid, String fd){
		if(descriptors.get(pid) == null){
			return null;
		}
		return descriptors.get(pid).remove(fd);
	}
	
	/**
	 * Returns a descriptor
	 * 
	 * @param pid process id
	 * @param fd file descriptor number
	 * @return ArtifactIdentifier subclass instance or null
	 */
	public ArtifactIdentifier getDescriptor(String pid, String fd){
		if(descriptors.get(pid) == null){
			descriptors.put(pid, new HashMap<String, ArtifactIdentifier>());
		}
		//cannot be sure if the file descriptors 0,1,2 are stdout, stderr, stdin, respectively. so just use unknowns
//		if(descriptors.get(pid).get(fd) == null){
//			String path = null;
//			if("0".equals(fd)){
//	    		path = "stdin";
//	    	}else if("1".equals(fd)){
//	    		path = "stdout";
//	    	}else if("2".equals(fd)){
//	    		path = "stderr";
//	    	}
//			if(path != null){
//				descriptors.get(pid).put(fd, new FileIdentity(path));
//			}
//		}
		return descriptors.get(pid).get(fd);
	}
	
	/**
	 * Duplicates a descriptor by copying it
	 *  
	 * @param pid process id
	 * @param oldFd old file descriptor number
	 * @param newFd new file descriptor number
	 */
	public void duplicateDescriptor(String pid, String oldFd, String newFd){
		if(descriptors.get(pid) == null){
			return;
		}else{
			if(descriptors.get(pid).get(oldFd) == null){
				return;
			}else{
				descriptors.get(pid).put(newFd, descriptors.get(pid).get(oldFd));
			}
		}
	}
	
	/**
	 * Copies all the descriptors from one pid to another while retaining the old ones if any
	 * 
	 * @param fromPid process id of the process to copy from
	 * @param toPid process id of the process to copy to
	 */
	public void copyDescriptors(String fromPid, String toPid){
		if(descriptors.get(fromPid) == null){
			return;
		}
		if(descriptors.get(toPid) == null){
			descriptors.put(toPid, new HashMap<String, ArtifactIdentifier>());
		}
		descriptors.get(toPid).putAll(descriptors.get(fromPid));
	}
	
	/**
	 * Replaces all the FDs of the process id with the given one.
	 * The FDs aren't copied but linked by reference.
	 * 
	 * @param fromPid process id to link FDs of
	 * @param toPid process id to link FDs to
	 */
	public void linkDescriptors(String fromPid, String toPid){
		if(descriptors.get(fromPid) == null){
			descriptors.put(fromPid, new HashMap<String, ArtifactIdentifier>());
		}
		descriptors.put(toPid, descriptors.get(fromPid));
	}
	
	/**
	 * Unlinks the reference of the FDs for the given process id and hard copies them
	 * 
	 * @param pid process id
	 */
	public void unlinkDescriptors(String pid){
		if(descriptors.get(pid) == null){
			return;
		}
		descriptors.put(pid, new HashMap<String, ArtifactIdentifier>(descriptors.get(pid)));
	}
	
	/**
	 * Adds an UnknownIdentifier
	 * 
	 * @param pid process id
	 * @param fd file descriptor number
	 */
	public ArtifactIdentifier addUnknownDescriptor(String pid, String fd){
		ArtifactIdentifier unknown = new UnknownIdentifier(pid, fd);
		addDescriptor(pid, fd, unknown, null);
		return unknown;
	}
}
