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
	
	public String getCwd(String pid){
		ProcessState state = _getProcessState(pid);
		if(state.cwd == null){
			return null;
		}else{
			String cwd = state.cwd.toString().trim();
			if(cwd.isEmpty()){
				return null;
			}else{
				return cwd;
			}
		}
	}
	
	public void setCwd(String pid, String cwd){
		if(cwd != null && !cwd.trim().isEmpty()){
			ProcessState state = _getProcessState(pid);
			if(state.cwd == null){
				state.cwd = new StringBuffer();
			}
			state.cwd.setLength(0);
			state.cwd.append(cwd.trim());
		}
	}
	
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
	
	public void setFd(String pid, String fd, FileDescriptor fileDescriptor){
		_getProcessState(pid).fds.put(fd, fileDescriptor);
	}
	
	public FileDescriptor getFd(String pid, String fd){
		return _getProcessState(pid).fds.get(fd);
	}
	
	public FileDescriptor removeFd(String pid, String fd){
		return _getProcessState(pid).fds.remove(fd);
	}
	
	private void copyFds(String fromPid, String toPid){
		ProcessState fromState = _getProcessState(fromPid);
		ProcessState toState = _getProcessState(toPid);
		toState.fds = new HashMap<String, FileDescriptor>(fromState.fds);
	}
	
	private void copyCwd(String fromPid, String toPid){
		ProcessState fromState = _getProcessState(fromPid);
		ProcessState toState = _getProcessState(toPid);
		if(fromState.cwd != null){
			if(toState.cwd == null){
				toState.cwd = new StringBuffer();
			}
			toState.cwd.setLength(0);
			toState.cwd.append(fromState.cwd);
		}
	}
	
	private void linkFds(String fromPid, String toPid){
		ProcessState fromState = _getProcessState(fromPid);
		ProcessState toState = _getProcessState(toPid);
		toState.fds = fromState.fds;
	}
	
	private void linkCwd(String fromPid, String toPid){
		ProcessState fromState = _getProcessState(fromPid);
		ProcessState toState = _getProcessState(toPid);
		if(fromState.cwd == null){
			fromState.cwd = new StringBuffer();
		}
		toState.cwd = fromState.cwd;
	}
	
	private void unlinkFds(String pid){
		ProcessState state = _getProcessState(pid);
		state.fds = new HashMap<String, FileDescriptor>(state.fds);
	}
	
	private void unlinkCwd(String pid){
		ProcessState state = _getProcessState(pid);
		if(state.cwd != null){
			String existingCwd = state.cwd.toString();
			state.cwd = new StringBuffer();
			state.cwd.append(existingCwd);
		}
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
		copyCwd(parentPid, childPid);
	}
	
	protected void processVforked(String parentPid, String childPid){
		// fds copied and memory shared (parent suspended)
		setMemoryTgid(childPid, getMemoryTgid(parentPid));
		copyFds(parentPid, childPid);
		copyCwd(parentPid, childPid);
	}
	
	protected void processCloned(String parentPid, String childPid, 
			boolean linkFds, boolean shareMemory, boolean shareFS){
		if(linkFds){
			setFdTgid(childPid, getFdTgid(parentPid));
			linkFds(parentPid, childPid);
		}else{
			copyFds(parentPid, childPid);
		}
		if(shareMemory){
			setMemoryTgid(childPid, getMemoryTgid(parentPid));
		}
		if(shareFS){
			linkCwd(parentPid, childPid);
		}else{
			copyCwd(parentPid, childPid);
		}
	}
	
	protected void processExecved(String pid){
		setMemoryTgid(pid, pid);
		setFdTgid(pid, pid);
		unlinkFds(pid);
		unlinkCwd(pid);
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
	Map<String, FileDescriptor> fds = new HashMap<String, FileDescriptor>();
	StringBuffer cwd;
	ProcessState(String memoryTgid, String fdTgid){
		this.memoryTgid = memoryTgid;
		this.fdTgid = fdTgid;
	}
}
