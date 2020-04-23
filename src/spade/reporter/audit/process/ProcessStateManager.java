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

import spade.reporter.audit.LinuxPathResolver;
import spade.utility.HelperFunctions;

/**
 * Manages any state for the currently active processes
 * 
 * 1) Memory tgid
 * 2) Fd tgid
 * 3) Fd table
 * 
 */
public abstract class ProcessStateManager{
	
	private final Map<String, ProcessState> processStates = new HashMap<String, ProcessState>();
	
	public void pivot_root(String pid, String root, String cwd){
		String mntId = _getProcessState(pid).nsMntId;
		for(ProcessState processState : processStates.values()){
			if(processState.nsMntId.equals(mntId)){
				processState.fs.root = root;
			}
		}
	}
	
	public void chroot(String pid, String root){
		ProcessFS fs = _getProcessState(pid).fs;
		fs.root = root;
	}
	
	public void absoluteChdir(String pid, String cwd){
		ProcessFS processFs = _getProcessState(pid).fs;
		processFs.cwd = cwd;
		processFs.cwdRoot = processFs.root;
	}
	
	// maintain the current cwd root
	public void relativeChdir(String pid, String cwd){
		ProcessFS processFs = _getProcessState(pid).fs;
		processFs.cwd = cwd;
	}
	
	public void fdChdir(String pid, String cwd, String cwdRoot){
		ProcessFS processFs = _getProcessState(pid).fs;
		processFs.cwd = cwd;
		processFs.cwdRoot = cwdRoot;
	}
	
	///////////////////////////////////////////////////////////////
	
	public String getMountNamespace(String pid){
		return _getProcessState(pid).nsMntId;
	}
	
	public String getUsrNamespace(String pid){
		return _getProcessState(pid).nsUsrId;
	}
	
	public String getNetNamespace(String pid){
		return _getProcessState(pid).nsNetId;
	}
	
	public String getPidNamespace(String pid){
		return _getProcessState(pid).nsPidId;
	}

	public String getIpcNamespace(String pid){
		return _getProcessState(pid).nsIpcId;
	}

	public String getPidChildrenNamespace(String pid){
		return _getProcessState(pid).nsPidChildrenId;
	}

	public void setNamespaces(String pid, NamespaceIdentifier namespace){
		if(!HelperFunctions.isNullOrEmpty(namespace.mount)){
			_getProcessState(pid).nsMntId = namespace.mount;
		}
		if(!HelperFunctions.isNullOrEmpty(namespace.net)){
			_getProcessState(pid).nsNetId = namespace.net;
		}
		if(!HelperFunctions.isNullOrEmpty(namespace.pid)){
			_getProcessState(pid).nsPidId = namespace.pid;
		}
		if(!HelperFunctions.isNullOrEmpty(namespace.user)){
			_getProcessState(pid).nsUsrId = namespace.user;
		}
		if(!HelperFunctions.isNullOrEmpty(namespace.pid_children)){
			_getProcessState(pid).nsPidChildrenId = namespace.pid_children;
		}
		if(!HelperFunctions.isNullOrEmpty(namespace.ipc)){
			_getProcessState(pid).nsIpcId = namespace.ipc;
		}
	}
	
	public String getCwd(String pid){
		return _getProcessState(pid).fs.cwd;
	}
	
	public String getCwdRoot(String pid){
		return _getProcessState(pid).fs.cwdRoot;
	}
	
	public String getRoot(String pid){
		return _getProcessState(pid).fs.root;
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
	
	private void copyFS(String fromPid, String toPid){
		ProcessState fromState = _getProcessState(fromPid);
		ProcessState toState = _getProcessState(toPid);
		
		ProcessFS copy = new ProcessFS();
		copy.root = fromState.fs.root;
		copy.cwdRoot = fromState.fs.cwdRoot;
		copy.cwd = fromState.fs.cwd;
		toState.fs = copy;
	}
		
	private void linkFds(String fromPid, String toPid){
		ProcessState fromState = _getProcessState(fromPid);
		ProcessState toState = _getProcessState(toPid);
		toState.fds = fromState.fds;
	}
	
	private void linkFS(String fromPid, String toPid){
		ProcessState fromState = _getProcessState(fromPid);
		ProcessState toState = _getProcessState(toPid);
		toState.fs = fromState.fs;
	}
		
	private void unlinkFds(String pid){
		ProcessState state = _getProcessState(pid);
		state.fds = new HashMap<String, FileDescriptor>(state.fds);
	}
	
	private void unlinkFS(String pid){
		ProcessState state = _getProcessState(pid);
		ProcessFS copy = new ProcessFS();
		copy.root = state.fs.root;
		copy.cwdRoot = state.fs.cwdRoot;
		copy.cwd = state.fs.cwd;
		state.fs = copy;
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
	
	protected void processForked(String parentPid, String childPid, NamespaceIdentifier namespaces){
		// only fds copied
		copyFds(parentPid, childPid);
		copyFS(parentPid, childPid);
		setNamespaces(childPid, namespaces);
	}
	
	protected void processVforked(String parentPid, String childPid, NamespaceIdentifier namespaces){
		// fds copied and memory shared (parent suspended)
		setMemoryTgid(childPid, getMemoryTgid(parentPid));
		copyFds(parentPid, childPid);
		copyFS(parentPid, childPid);
		setNamespaces(childPid, namespaces);
	}
	
	protected void processCloned(String parentPid, String childPid, 
			boolean linkFds, boolean shareMemory, boolean shareFS,
			NamespaceIdentifier namespaces){
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
			linkFS(parentPid, childPid);
		}else{
			copyFS(parentPid, childPid);
		}
		setNamespaces(childPid, namespaces);
	}
	
	protected void processExecved(String pid, String cwd, NamespaceIdentifier namespaces){
		setMemoryTgid(pid, pid);
		setFdTgid(pid, pid);
		unlinkFds(pid);
		unlinkFS(pid);
		_getProcessState(pid).fs.cwd = cwd;
		setNamespaces(pid, namespaces);
	}
	
	protected void processExited(String pid){
		removeProcessState(pid);
	}
	
	protected void clearAll(){
		processStates.clear();
	}
}

class ProcessState{
	String nsMntId = "-1";
	String nsPidId = "-1";
	String nsUsrId = "-1";
	String nsIpcId = "-1";
	String nsNetId = "-1";
	String nsPidChildrenId = "-1";
	
	String memoryTgid;
	String fdTgid;
	Map<String, FileDescriptor> fds = new HashMap<String, FileDescriptor>();
	ProcessFS fs = new ProcessFS();
	ProcessState(String memoryTgid, String fdTgid){
		this.memoryTgid = memoryTgid;
		this.fdTgid = fdTgid;
	}
}

class ProcessFS{
	String root = LinuxPathResolver.FS_ROOT;
	String cwdRoot = root;
	
	String cwd;

	@Override
	public String toString(){
		return "ProcessFS [root=" + root + ", cwdRoot=" + cwdRoot + ", cwd=" + cwd + "]";
	}
	
}
