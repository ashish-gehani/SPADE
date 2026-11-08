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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

	public String getCgroupNamespace(String pid){
		return _getProcessState(pid).nsCgroupId;
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
		if(!HelperFunctions.isNullOrEmpty(namespace.cgroup)){
			_getProcessState(pid).nsCgroupId = namespace.cgroup;
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
	
	/** Sets the descriptor, which is new, so not close-on-exec until setFdCloseOnExec says so. */
	public void setFd(String pid, String fd, FileDescriptor fileDescriptor){
		final ProcessState state = _getProcessState(pid);
		state.fds.put(fd, fileDescriptor);
		state.fdsCloseOnExec.remove(fd);
	}

	/**
	 * Whether the kernel closes the descriptor at a successful execve (O_CLOEXEC, SOCK_CLOEXEC,
	 * F_DUPFD_CLOEXEC, FD_CLOEXEC, close_range with CLOSE_RANGE_CLOEXEC). A per-number flag, not
	 * one of the open file: dup gives the new number the same FileDescriptor, but not the flag.
	 */
	public void setFdCloseOnExec(String pid, String fd, boolean closeOnExec){
		final ProcessState state = _getProcessState(pid);
		if(closeOnExec && state.fds.containsKey(fd)){
			state.fdsCloseOnExec.add(fd);
		}else{
			state.fdsCloseOnExec.remove(fd);
		}
	}

	public boolean isFdCloseOnExec(String pid, String fd){
		return _getProcessState(pid).fdsCloseOnExec.contains(fd);
	}

	/** The process's descriptor numbers, a copy. */
	public List<String> getFdNumbers(String pid){
		return new ArrayList<String>(_getProcessState(pid).fds.keySet());
	}

	/** Gives the process a descriptor table of its own (close_range with CLOSE_RANGE_UNSHARE). */
	public void unshareFds(String pid){
		unlinkFds(pid);
	}
	
	public FileDescriptor getFd(String pid, String fd){
		return _getProcessState(pid).fds.get(fd);
	}
	
	public FileDescriptor removeFd(String pid, String fd){
		final ProcessState state = _getProcessState(pid);
		state.fdsCloseOnExec.remove(fd);
		return state.fds.remove(fd);
	}

	private void copyFds(String fromPid, String toPid){
		ProcessState fromState = _getProcessState(fromPid);
		ProcessState toState = _getProcessState(toPid);
		toState.fds = new HashMap<String, FileDescriptor>(fromState.fds);
		toState.fdsCloseOnExec = new HashSet<String>(fromState.fdsCloseOnExec);
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
		toState.fdsCloseOnExec = fromState.fdsCloseOnExec;
	}
	
	private void linkFS(String fromPid, String toPid){
		ProcessState fromState = _getProcessState(fromPid);
		ProcessState toState = _getProcessState(toPid);
		toState.fs = fromState.fs;
	}
		
	private void unlinkFds(String pid){
		ProcessState state = _getProcessState(pid);
		state.fds = new HashMap<String, FileDescriptor>(state.fds);
		state.fdsCloseOnExec = new HashSet<String>(state.fdsCloseOnExec);
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
		// The kernel closes the close-on-exec descriptors at execve. Kept, they would name
		// artifacts the numbers no longer refer to once the numbers are reused.
		final ProcessState state = _getProcessState(pid);
		for(final String fd : state.fdsCloseOnExec){
			state.fds.remove(fd);
		}
		state.fdsCloseOnExec.clear();
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
	String nsCgroupId = "-1";
	
	String memoryTgid;
	String fdTgid;
	Map<String, FileDescriptor> fds = new HashMap<String, FileDescriptor>();
	Set<String> fdsCloseOnExec = new HashSet<String>(); // shared and copied with fds
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
