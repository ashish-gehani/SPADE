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
package spade.reporter.audit.process;

import java.util.HashMap;
import java.util.Map;

import spade.reporter.audit.artifact.ArtifactIdentifier;

/**
 * Manages any state for the currently active processes
 * 
 * 1) Memory tgid
 * 2) Fd tgid
 * 3) Fd table
 * 
 */
public abstract class ProcessStateManager{

	private Map<String, ProcessState> processStates = new HashMap<String, ProcessState>();
	
	private void setMemoryTgid(String pid, String memoryTgid){
		_getProcessState(pid).memoryTgid = memoryTgid;
	}
	
	public String getMemoryTgid(String pid){
		return _getProcessState(pid).memoryTgid;
	}
	
	private void setFdTgid(String pid, String fdTgid){
		_getProcessState(pid).fdTgid = fdTgid;
	}
	
	public String getFdTgid(String pid){
		return _getProcessState(pid).fdTgid;
	}
	
	public void setFd(String pid, String fd, ArtifactIdentifier fdIdentifier){
		_getProcessState(pid).fds.put(fd, fdIdentifier);
	}
	
	public void setFd(String pid, String fd, ArtifactIdentifier fdIdentifier, Boolean wasOpenedForRead){
		fdIdentifier.setOpenedForRead(wasOpenedForRead);
		_getProcessState(pid).fds.put(fd, fdIdentifier);
	}
	
	public ArtifactIdentifier getFd(String pid, String fd){
		return _getProcessState(pid).fds.get(fd);
	}
	
	public ArtifactIdentifier removeFd(String pid, String fd){
		return _getProcessState(pid).fds.remove(fd);
	}
	
	private void copyFds(String fromPid, String toPid){
		ProcessState fromState = _getProcessState(fromPid);
		ProcessState toState = _getProcessState(toPid);
		toState.fds = new HashMap<String, ArtifactIdentifier>(fromState.fds);
	}
	
	private void linkFds(String fromPid, String toPid){
		ProcessState fromState = _getProcessState(fromPid);
		ProcessState toState = _getProcessState(toPid);
		toState.fds = fromState.fds;
	}
	
	private void unlinkFds(String pid){
		ProcessState state = _getProcessState(pid);
		state.fds = new HashMap<String, ArtifactIdentifier>(state.fds);
	}
		
	private ProcessState _getProcessState(String pid){
		ProcessState state = processStates.get(pid);
		if(state == null){
			state = new ProcessState(pid, pid);
			processStates.put(pid, state);
		}
		return state;
	}
	
	private void removeProcessState(String pid){
		processStates.remove(pid);
	}
	
	////////
	
	protected void processForked(String parentPid, String childPid){
		// only fds copied
		copyFds(parentPid, childPid);
	}
	
	protected void processVforked(String parentPid, String childPid){
		// fds copied and memory shared (parent suspended)
		setMemoryTgid(childPid, getMemoryTgid(parentPid));
		copyFds(parentPid, childPid);
	}
	
	protected void processCloned(String parentPid, String childPid, boolean linkFds, boolean shareMemory){
		if(linkFds){
			setFdTgid(childPid, getFdTgid(parentPid));
			linkFds(parentPid, childPid);
		}else{
			copyFds(parentPid, childPid);
		}
		if(shareMemory){
			setMemoryTgid(childPid, getMemoryTgid(parentPid));
		}
	}
	
	protected void processExecved(String pid){
		setMemoryTgid(pid, pid);
		setFdTgid(pid, pid);
		unlinkFds(pid);
	}
	
	protected void processExited(String pid){
		removeProcessState(pid);
	}
	
	protected void clearAll(){
		processStates.clear();
	}
}

class ProcessState{
	String memoryTgid;
	String fdTgid;
	Map<String, ArtifactIdentifier> fds = new HashMap<String, ArtifactIdentifier>();
	
	ProcessState(String memoryTgid, String fdTgid){
		this.memoryTgid = memoryTgid;
		this.fdTgid = fdTgid;
	}
}
