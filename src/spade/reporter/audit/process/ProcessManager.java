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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.edge.opm.WasTriggeredBy;
import spade.reporter.Audit;
import spade.reporter.audit.ArtifactIdentifier;
import spade.reporter.audit.AuditEventReader;
import spade.reporter.audit.BlockDeviceIdentifier;
import spade.reporter.audit.CharacterDeviceIdentifier;
import spade.reporter.audit.DirectoryIdentifier;
import spade.reporter.audit.FileIdentifier;
import spade.reporter.audit.LinkIdentifier;
import spade.reporter.audit.NamedPipeIdentifier;
import spade.reporter.audit.NetworkSocketIdentifier;
import spade.reporter.audit.OPMConstants;
import spade.reporter.audit.SYSCALL;
import spade.reporter.audit.UnixSocketIdentifier;
import spade.reporter.audit.UnnamedPipeIdentifier;
import spade.utility.CommonFunctions;
import spade.utility.Execute;
import spade.vertex.opm.Process;

public abstract class ProcessManager extends ProcessStateManager{
	
	private final Logger logger = Logger.getLogger(this.getClass().getName());

	//  Following constant values are taken from:
	//  http://lxr.free-electrons.com/source/include/uapi/linux/sched.h 
	//  AND  
	//  http://lxr.free-electrons.com/source/include/uapi/asm-generic/signal.h
	private final int SIGCHLD = 17, CLONE_VFORK = 0x00004000, CLONE_VM = 0x00000100,
			CLONE_FILES = 0x00000400;
	
	private Audit reporter;
	
	/**
	 * Contains a mapping from pid to the keys of currently active processes
	 */
	private Map<String, ProcessKey> active = new HashMap<String, ProcessKey>();
	
	/**
	 * Contains a mapping from keys of all (needed) processes to actual state.
	 * Needed to keep some process states in case of a unit dependency event because we need
	 * to recreate the unit vertex as it was.
	 */
	private Map<ProcessKey, ProcessUnitState> processUnitStates = new HashMap<ProcessKey, ProcessUnitState>();
	
	/**
	 * Used to tell if simple agent or complete agent needs to be created
	 */
	private final boolean simplify;
	
	/**
	 * Used to tell whether to use unitid=0 for containing processes or not
	 */
	private final boolean units;
	
	protected ProcessManager(Audit reporter, boolean simplify, boolean units){
		this.reporter = reporter;
		this.simplify = simplify;
		this.units = units;
	}
	
	protected Audit getReporter(){
		return reporter;
	}
	
	/**
	 * Needed for any state cleanup
	 */
	protected abstract void clearAll();
	
	/**
	 * Creates either the process vertex or the unit vertex depending on arguments.
	 * 
	 * If unit is null then process vertex created else process vertex.
	 * 
	 * @param process
	 * @param agent
	 * @param unit
	 * @return process vertex
	 */
	protected abstract Process buildVertex(ProcessIdentifier process, AgentIdentifier agent, UnitIdentifier unit);
	
	/**
	 * Only to be called when a new process seen. New process identified as: 
	 * a) when created explicitly
	 * b) when state for the given pid doesn't exist
	 * 
	 * Can be called from either a system call or proc FS. Agent source depends on that.
	 * 
	 * Must DOs:
	 * 1) Sets/overwrites process unit state from the given arguments by calling setProcessUnitState.
	 * 2) Outputs the vertices and edges required for the new process vertex.
	 * 
	 * @param time time of the event
	 * @param eventId id of the event
	 * @param process the process identifier to set in process unit state
	 * @param agent the agent identifier to set in process unit state
	 * @param source data source. Can be either from system call or from proc FS at the moment.
	 * @return the process vertex that is put
	 */
	protected abstract Process putProcessVertex(String time, String eventId, ProcessIdentifier process,
			AgentIdentifier agent, String source);
	
	/**
	 * Only to be called when a new unit seen. New units only come from unit entry event.
	 *
	 * Always called from a system call event.
	 *
	 * Must DOs:
	 * 1) Only to be called when the process unit state for the pid already exists.
	 * 2) Sets the unit in the process state.
	 * 3) Outputs the vertices and edges required for the new unit vertex.
	 * 
	 * @param time time of the event
	 * @param eventId id of the event
	 * @param pid process id
	 * @param unit the unit to be set
	 * @return the unit vertex that is put
	 */
	protected abstract Process putUnitVertex(String time, String eventId, String pid, UnitIdentifier unit);
	
	/**
	 * ASSUMES that the process state already exists for the pid.
	 * 
	 * Updates the agent and outputs any necessary vertices and edge. Updates even if the newAgent is the same.
	 * 
	 * Always called from a system call event.
	 * 
	 * @param time time time of the event
	 * @param eventId eventId id of the event
	 * @param pid pid process id
	 * @param newAgent new agent identifier
	 * @param operation can only be either update (i.e. indirect update), or setuid, and setgid (direct update)
	 */
	protected abstract void handleAgentUpdate(String time, String eventId, String pid, AgentIdentifier newAgent, 
			String operation);
	
	/**
	 * Removes the following states for the pid:
	 * 1) process and unit state if there were no units for the pid.
	 * 2) ids, and fds for the pid. ALWAYS.
	 * 
	 * If the process state cannot be removed completely because we might need some state for unit dependency event
	 * then only partial clean (i.e. unnecessary state) is done.
	 * 
	 * @param pid process id
	 */
	public void removeProcessUnitState(String pid){
		super.processExited(pid);
		ProcessKey key = active.remove(pid);
		if(key != null){
			ProcessUnitState state = processUnitStates.get(key);
			if(state != null){
				if(!state.hadUnits()){
					processUnitStates.remove(key);
				}else{
					state.partialClean();
				}
			}
		}
	}
	
	/**
	 * Sets the given state as the active one for the pid.
	 * 
	 * ProcessKey is constructed using pid and startTime. startTime could be null.
	 * 
	 * startTime needed in key for unit dependency event.
	 * 
	 * Note: For now don't call this function from this class and use the child classes!
	 * 
	 * @param state the currently active state for the process to set.
	 */
	protected void setProcessUnitState(ProcessUnitState state){
		String pid = state.getProcess().pid;
		String startTime = state.getProcess().startTime;		
		ProcessKey key = new ProcessKey(pid, startTime);
		processUnitStates.put(key, state);
		active.put(pid, key);
	}
	
	/**
	 * Returns the currently active process unit state
	 * 
	 * @param pid process id
	 * @return process unit state
	 */
	protected ProcessUnitState getProcessUnitState(String pid){
		ProcessKey key = active.get(pid);
		if(key != null){
			return processUnitStates.get(key);
		}else{
			return null;
		}
	}
		
	/**
	 * Returns the process state (which might not be active anymore) by pid and start time.
	 * 
	 * @param pid process id
	 * @param startTime thread start time as gotten in the unit information
	 * @return process unit state
	 */
	private ProcessUnitState getProcessUnitState(String pid, String startTime){
		ProcessKey key = new ProcessKey(pid, startTime);
		return processUnitStates.get(key);
	}
	
	/**
	 * Returns the vertex for the currently active process with pid.
	 * 
	 * @param pid process id
	 * @return vertex
	 */
	public Process getVertex(String pid){
		ProcessUnitState state = getProcessUnitState(pid);
		if(state != null){
			return buildVertex(state.getProcess(), state.getAgent(), state.getUnit());
		}else{
			return null;
		}
	}
	
	/**
	 * Clears all state for every process. ALL.
	 */
	public void daemonStart(){
		super.clearAll();
		clearAll();
		active.clear();
		processUnitStates.clear();
	}
	
	/**
	 * Returns either null (if unit==false) or 0 (if unit==true)
	 * 
	 * @return null/0
	 */
	private String getUnitId(){
		return units ? "0" : null;
	}
	
	/**
	 * Creates process identifier from system call event data.
	 * The source is always system call.
	 * The event data must contain pid, ppid, comm, time
	 * 
	 * The process identifier created doesn't have start time and has seen time instead.
	 * 
	 * Note: Only to be called if process creation wasn't seen.
	 * 
	 * @param eventData event data from a system call event
	 * @return process identifier
	 */
	protected ProcessIdentifier buildProcessIdentifierFromSyscall(Map<String, String> eventData){
		return new ProcessIdentifier(eventData.get(AuditEventReader.PID), eventData.get(AuditEventReader.PPID), 
				eventData.get(AuditEventReader.COMM), eventData.get(AuditEventReader.CWD), null, null, 
				eventData.get(AuditEventReader.TIME), getUnitId(), OPMConstants.SOURCE_AUDIT_SYSCALL);
	}
	
	/**
	 * Creates agent identifier from system call event data.
	 * 
	 * if simplify==true then returns AgentIdentifier instance
	 * if simplify==false then returns full AgentIdentifier
	 * 
	 * @param eventData event data from a system call event
	 * @return AgentIdentifier instance
	 */
	protected AgentIdentifier buildAgentIdentifierFromSyscall(Map<String, String> eventData){
		String uid = eventData.get(AuditEventReader.UID);
		String euid = eventData.get(AuditEventReader.EUID);
		String gid = eventData.get(AuditEventReader.GID);
		String egid = eventData.get(AuditEventReader.EGID);
		if(simplify){
			return new AgentIdentifier(uid, euid, gid, egid);
		}else{
			return new AgentIdentifier(uid, euid, gid, egid, 
					eventData.get(AuditEventReader.SUID), eventData.get(AuditEventReader.FSUID), 
					eventData.get(AuditEventReader.SGID), eventData.get(AuditEventReader.FSGID));
		}
	}
	
	/**
	 * Calls the private function handleProcessFromSyscall with the operation update.
	 * 
	 * Only to be called when the process needs to be created indirectly (i.e. creation event wasn't seen)
	 * 
	 * @param eventData system call event data
	 * @return the active vertex (unit or process) or null (if error i.e. no process state)
	 */
	public Process handleProcessFromSyscall(Map<String, String> eventData){
		return handleProcessFromSyscall(eventData, OPMConstants.OPERATION_UPDATE);
	}
	
	/**
	 * Does the following:
	 * 
	 * Creates the process unit state if it doesn't exist. Sets that state and returns the created process vertex.
	 * 
	 * If process state already existed then checks whether the agent has been updated or not for the process.
	 * If agent updated then handles that by calling the child class function.
	 * 
	 * Returns the updated/same process or unit vertex
	 * 
	 * @param eventData event data from the system call
	 * @param operation can only be either update or setuid or setgid for now.
	 * @return the vertex for the pid
	 */
	private Process handleProcessFromSyscall(Map<String, String> eventData, String operation){
		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		String pid = eventData.get(AuditEventReader.PID);
		String source = OPMConstants.SOURCE_AUDIT_SYSCALL;
		
		AgentIdentifier agentIdentifier = buildAgentIdentifierFromSyscall(eventData);
		ProcessIdentifier processIdentifier = null;
		ProcessUnitState state = getProcessUnitState(pid);
		if(state == null){
			processIdentifier = buildProcessIdentifierFromSyscall(eventData);
			putProcessVertex(time, eventId, processIdentifier, agentIdentifier, source);
		}else{
			AgentIdentifier existingAgent = state.getAgent();
			if(!existingAgent.equals(agentIdentifier)){
				handleAgentUpdate(time, eventId, pid, agentIdentifier, operation);
			}
		}
		
		return getVertex(pid);
	}
	
	/**
	 * Does the following:
	 * 
	 * 1) Adds and Puts a new process vertex with seenTime if this process wasn't seen before
	 * 2) Updates the process state (tgids and fds)
	 * 3) Creates a new process vertex for the same pid with startTime and extra new info
	 * 4) Replaces the existing process unit state with the new one
	 * 5) Puts the new vertex
	 * 6) Draws the execve edge
	 *  
	 * @param eventData system call audit event key values
	 * @param syscall execve syscall
	 * @return the created process vertex
	 */
	public Process handleExecve(Map<String, String> eventData, SYSCALL syscall){
		String source = OPMConstants.SOURCE_AUDIT_SYSCALL;
		String pid = eventData.get(AuditEventReader.PID);
		String ppid = eventData.get(AuditEventReader.PPID);
		String comm = eventData.get(AuditEventReader.COMM);
		String cwd = eventData.get(AuditEventReader.CWD);
		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		
		Process parentProcessVertex = handleProcessFromSyscall(eventData);

		// look at code
		processExecved(pid);
		
		String commandLine = null;
		String execveArgcString = eventData.get(AuditEventReader.EXECVE_ARGC);
		if(execveArgcString != null){
			int execveArgc = CommonFunctions.parseInt(execveArgcString, null);
			commandLine = "";
			for(int a = 0; a < execveArgc; a++){
				String execveArg = eventData.get(AuditEventReader.EXECVE_PREFIX + "a" + a);
				commandLine += execveArg + " ";
			}
			commandLine = commandLine.trim();
		}

		AgentIdentifier agentIdentifier = buildAgentIdentifierFromSyscall(eventData);
		ProcessIdentifier childProcessIdentifier = new ProcessIdentifier(pid, ppid, comm, cwd, commandLine, time, 
				null, getUnitId(), source);
		
		Process childProcessVertex = putProcessVertex(time, eventId, childProcessIdentifier, agentIdentifier, source);
		
		WasTriggeredBy edge = new WasTriggeredBy(childProcessVertex, parentProcessVertex);
		reporter.putEdge(edge, reporter.getOperation(syscall), time, eventId, source);
		
		return childProcessVertex;
	}
	
	/**
	 * Does the following:
	 * 
	 * 1) Creates, adds, and puts the process vertex for the parent if not seen
	 * 2) Removes the state (tgids, and fds) for the child process
	 * 3) Creates, adds, and puts the child process vertex
	 * 4) Adds the state (tgids, and fds) for the child process
	 * 5) Draws the fork/vfork/clone edge
	 * 
	 * @param eventData system call audit event key values
	 * @param syscall fork/vfork/clone syscall
	 * @return true
	 */
	public boolean handleForkVforkClone(Map<String, String> eventData, SYSCALL syscall){
		String source = OPMConstants.SOURCE_AUDIT_SYSCALL;
		String flagsString = eventData.get(AuditEventReader.ARG0);
		String comm = eventData.get(AuditEventReader.COMM);
		String cwd = eventData.get(AuditEventReader.CWD);
		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		
		int flags = CommonFunctions.parseInt(flagsString, null);
		String parentPid = eventData.get(AuditEventReader.PID);
		String childPid = eventData.get(AuditEventReader.EXIT);
		
		// handle the parent process vertex
		Process parentVertex = handleProcessFromSyscall(eventData);
		
		removeProcessUnitState(childPid); // Remove the existing state before continuing and adding the new one TODO
		
		AgentIdentifier agentIdentifier = buildAgentIdentifierFromSyscall(eventData);
		
		ProcessUnitState parentState = getProcessUnitState(parentPid);
		ProcessIdentifier parentProcessIdentifier = parentState.getProcess();
		
		cwd = cwd == null ? parentProcessIdentifier.cwd : cwd;
		comm = comm == null ? parentProcessIdentifier.name : comm;
		
		ProcessIdentifier childProcessIdentifier = new ProcessIdentifier(childPid, parentPid, comm, cwd, 
				parentProcessIdentifier.commandLine, time, null, getUnitId(), source);
		
		Process childVertex = putProcessVertex(time, eventId, childProcessIdentifier, agentIdentifier, source);

		if(syscall == SYSCALL.CLONE){
			// Source: http://www.makelinux.net/books/lkd2/ch03lev1sec3
			if((flags & SIGCHLD) == SIGCHLD && (flags & CLONE_VM) == CLONE_VM && (flags & CLONE_VFORK) == CLONE_VFORK){ 
				//is vfork
				syscall = SYSCALL.VFORK;
			}else if((flags & SIGCHLD) == SIGCHLD){ //is fork
				syscall = SYSCALL.FORK;
			}
		}
		
		boolean handle = true;
		
		if(syscall == SYSCALL.FORK){
			processForked(parentPid, childPid);
		}else if(syscall == SYSCALL.VFORK){
			processVforked(parentPid, childPid);
		}else if(syscall == SYSCALL.CLONE){
			boolean linkFds = (flags & CLONE_FILES) == CLONE_FILES;
			boolean shareMemory = (flags & CLONE_VM) == CLONE_VM;
			processCloned(parentPid, childPid, linkFds, shareMemory);
		}else{
			handle = false;
			reporter.log(Level.INFO, "Unexpected syscall", null, time, eventId, syscall);
		}
		
		if(handle){
			WasTriggeredBy edge = new WasTriggeredBy(childVertex, parentVertex);
			reporter.putEdge(edge, reporter.getOperation(syscall), time, eventId, source);
			return true;
		}else{
			return false;
		}
	}

	/**
	 * Does the following:
	 * 
	 * 1) Puts the process vertex if it doesn't exist before
	 * 2) Draws the update agent edge
	 * 
	 * @param eventData system call audit event key values
	 * @param syscall fork/vfork/clone syscall
	 * @return true
	 */
	public boolean handleSetuidSetgid(Map<String, String> eventData, SYSCALL syscall){
		if(simplify){
			if(syscall == SYSCALL.SETRESUID 
					|| syscall == SYSCALL.SETRESGID
					|| syscall == SYSCALL.SETFSUID
					|| syscall == SYSCALL.SETFSGID){
				return true;
			}
		}
		
		/*
		 * Note: If process wasn't seen before then process added with the post-agent-id-update agent
		 * and then the agent-id-update edge is drawn too. Meaning that the agent update edge would be 
		 * redrawn to itself but with different operation annotation.
		 */
		
		String pid = eventData.get(AuditEventReader.PID);
		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		AgentIdentifier agentIdentifier = buildAgentIdentifierFromSyscall(eventData);
		String operation = reporter.getOperation(syscall);
		
		if(getVertex(pid) == null){
			handleProcessFromSyscall(eventData);
		}
		
		// Force draw edge even though possible that the existing process has the same agent
		handleAgentUpdate(time, eventId, pid, agentIdentifier, operation);
		
		return true;
	}

	/**
	 * Does the following:
	 * 
	 * 1) Puts the process vertex if it doesn't exist
	 * 2) Draws the exit edge to the vertex itself
	 * 
	 * @param eventData system call audit event key values
	 * @param syscall fork/vfork/clone syscall
	 * @return true
	 */
	public boolean handleExit(Map<String, String> eventData, SYSCALL syscall){
		String pid = eventData.get(AuditEventReader.PID);
		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		
		Process processVertex = handleProcessFromSyscall(eventData);
		
		WasTriggeredBy edge = new WasTriggeredBy(processVertex, processVertex);
		reporter.putEdge(edge, reporter.getOperation(syscall), time, eventId, OPMConstants.SOURCE_AUDIT_SYSCALL);

		removeProcessUnitState(pid);
		return true;
	}
	
	/**
	 * Does the following:
	 * 
	 * 1) Exits any current unit that was active
	 * 2) Puts the process vertex if it didn't exist
	 * 3) Creates, puts, and sets the unit vertex for the process
	 * 4) Draws the unit edge
	 * 
	 * @param eventData system call audit event key values
	 * @return true
	 */
	public boolean handleUnitEntry(Map<String, String> eventData){
		String pid = eventData.get(AuditEventReader.PID);
		String time = eventData.get(AuditEventReader.TIME);
		String eventId = eventData.get(AuditEventReader.EVENT_ID);
		
		String unitId = eventData.get(AuditEventReader.UNIT_UNITID);
		String unitIteration = eventData.get(AuditEventReader.UNIT_ITERATION);
		String unitCount = eventData.get(AuditEventReader.UNIT_COUNT);
		String unitStartTime = eventData.get(AuditEventReader.UNIT_TIME);
		
		// remove the last unit if there was any
		ProcessUnitState state = getProcessUnitState(pid);
		if(state != null){
			if(state.isUnitActive()){
				state.unitExit();
			}
		}
		
		Process processVertex = handleProcessFromSyscall(eventData);
		
		UnitIdentifier unitIdentifier = new UnitIdentifier(unitId, unitIteration, unitCount, unitStartTime, eventId);
				
		Process unitVertex = putUnitVertex(time, eventId, pid, unitIdentifier);
				
		WasTriggeredBy edge = new WasTriggeredBy(unitVertex, processVertex);
		reporter.putEdge(edge, reporter.getOperation(SYSCALL.UNIT), time, eventId, OPMConstants.SOURCE_BEEP);
		
		return true;
	}
	
	/**
	 * Removes the currently active unit (if existed)
	 * 
	 * @param eventData system call audit event key values
	 * @return true
	 */
	public boolean handleUnitExit(Map<String, String> eventData){
		String pid = eventData.get(AuditEventReader.PID);
		ProcessUnitState state = getProcessUnitState(pid);
		if(state != null){
			if(state.isUnitActive()){
				state.unitExit();
				return true;
			}else{
				return false;
			}
		}else{
			return false;
		}
	}
	
	/**
	 * Does the following:
	 * 
	 * 1) Gets the reading unit and the writing unit state. If any is null then nothing done.
	 * 2) If both states exist then dependency edge drawn.
	 * 
	 * @param eventData system call audit event key values
	 * @return true/false
	 */
	public boolean handleUnitDependency(Map<String, String> eventData){
		String readingUnitPid = eventData.get(AuditEventReader.UNIT_PID);
		String readingUnitThreadStartTime = eventData.get(AuditEventReader.UNIT_THREAD_START_TIME);
		String readingUnitId = eventData.get(AuditEventReader.UNIT_UNITID);
		String readingUnitIteration = eventData.get(AuditEventReader.UNIT_ITERATION);
		String readingUnitCount = eventData.get(AuditEventReader.UNIT_COUNT);
		String readingUnitStartTime = eventData.get(AuditEventReader.UNIT_TIME);
		
		String writingUnitPid = eventData.get(AuditEventReader.UNIT_PID+0);
		String writingUnitThreadStartTime = eventData.get(AuditEventReader.UNIT_THREAD_START_TIME+0);
		String writingUnitId = eventData.get(AuditEventReader.UNIT_UNITID+0);
		String writingUnitIteration = eventData.get(AuditEventReader.UNIT_ITERATION+0);
		String writingUnitCount = eventData.get(AuditEventReader.UNIT_COUNT+0);
		String writingUnitStartTime = eventData.get(AuditEventReader.UNIT_TIME+0);
		
		ProcessUnitState readingProcessState = getProcessUnitState(readingUnitPid, readingUnitThreadStartTime);
		ProcessUnitState writingProcessState = getProcessUnitState(writingUnitPid, writingUnitThreadStartTime);
		
		if(readingProcessState != null && writingProcessState != null){
			UnitIdentifier readingUnit = new UnitIdentifier(readingUnitId, readingUnitIteration, readingUnitCount, 
					readingUnitStartTime, null);
			AgentIdentifier readingUnitAgent = readingProcessState.getAgentForUnit(readingUnit);
			Process readingUnitVertex = buildVertex(readingProcessState.getProcess(), readingUnitAgent, readingUnit);
			
			UnitIdentifier writingUnit = new UnitIdentifier(writingUnitId, writingUnitIteration, writingUnitCount, 
					writingUnitStartTime, null);
			AgentIdentifier writingUnitAgent = writingProcessState.getAgentForUnit(writingUnit);
			Process writingUnitVertex = buildVertex(writingProcessState.getProcess(), writingUnitAgent, writingUnit);
			
			WasTriggeredBy edge = new WasTriggeredBy(readingUnitVertex, writingUnitVertex);
			reporter.putEdge(edge, OPMConstants.OPERATION_UNIT_DEPENDENCY, "0", "0", OPMConstants.SOURCE_BEEP);
			return true;
		}else{
			logger.log(Level.INFO, "Incomplete state for unit dependency edge. Event data: " + eventData);
			return false;
		}
	}
	
	/*  PROCFS code below */
	
	public void putProcessesFromProcFs(){
		Long boottime = getBootTime();
		if(boottime == null){
			logger.log(Level.SEVERE, "Missing boottime. Failed to build process information from /proc");
		}else{
			String source = OPMConstants.SOURCE_PROCFS;
			try{
				Map<String, String> pidToPpid = new HashMap<String, String>();
				
				java.io.File directory = new java.io.File("/proc");
				java.io.File[] listOfFiles = directory.listFiles();
				for(int i = 0; i < listOfFiles.length; i++){
					if(listOfFiles[i].isDirectory()){
						String pid = listOfFiles[i].getName();
						// Only handle numeric directory names
						Integer pidInt = CommonFunctions.parseInt(pid, null);
						if(pidInt != null){
							SimpleEntry<ProcessIdentifier, AgentIdentifier> processAndAgent = 
									createProcessFromProcFS(pid, boottime);
							if(processAndAgent != null){
								ProcessIdentifier process = processAndAgent.getKey();
								AgentIdentifier agent = processAndAgent.getValue();
								pidToPpid.put(process.pid, process.ppid);
								
								putProcessVertex(null, null, process, agent, source);
								
								Map<String, ArtifactIdentifier> fds = getFileDescriptors(pid);
								if(fds != null){
									for(Map.Entry<String, ArtifactIdentifier> entry : fds.entrySet()){
										ArtifactIdentifier fdIdentifier = entry.getValue();
										fdIdentifier.setOpenedForRead(null); // Don't want the close edge
										setFd(pid, entry.getKey(), fdIdentifier);
									}
								}
							}else{
								logger.log(Level.WARNING, "Failed to read /proc to build process with pid: " + pid);
							}
						}
					}
				}
				
				for(Map.Entry<String, String> pidAndPpid : pidToPpid.entrySet()){
					String pid = pidAndPpid.getKey();
					String ppid = pidAndPpid.getValue();
					Process processVertex = getVertex(pid);
					Process parentProcessVertex = getVertex(ppid);
					if(processVertex != null && parentProcessVertex != null){
						WasTriggeredBy childToParent = new WasTriggeredBy(processVertex, parentProcessVertex);
						reporter.putEdge(childToParent, reporter.getOperation(SYSCALL.UNKNOWN), null, null, source);
					}
				}
			}catch(Exception e){
				logger.log(Level.WARNING, "Failed to read /proc to build processes", e);
			}
		}
	}
	
	private Map<String, ArtifactIdentifier> getFileDescriptors(String pid){
		try{
			//LSOF args -> n = no DNS resolution, P = no port user-friendly naming, p = pid of process
			Execute.Output output = Execute.getOutput("lsof -nPp " + pid);
			if(!output.exitValueIndicatesError()){
				List<String> stdOutLines = output.getStdOut();
				stdOutLines.remove(0); //remove the heading line
				
				Map<String, ArtifactIdentifier> fds = new HashMap<String, ArtifactIdentifier>();
				Map<String, String> inodefd0 = new HashMap<String, String>();
				
				for(String stdOutLine : stdOutLines){
					String tokens[] = stdOutLine.split("\\s+");
					if(tokens.length >= 10){
						String type = tokens[4].toLowerCase().trim();
						// fd ends with r(read), w(write), u(read and write), W (lock)
						String fdString = tokens[3].trim().replaceAll("[^0-9]", ""); 
						if("fifo".equals(type)){
							String path = tokens[8];
							if("pipe".equals(path)){ //unnamed pipe
								String inode = tokens[7];
								if(inodefd0.get(inode) == null){
									inodefd0.put(inode, fdString);
								}else{
									ArtifactIdentifier pipeInfo = new UnnamedPipeIdentifier(pid, fdString, inodefd0.get(inode));
									fds.put(fdString, pipeInfo);
									fds.put(inodefd0.get(inode), pipeInfo);
									inodefd0.remove(inode);
								}
							}else{ //named pipe
								fds.put(fdString, new NamedPipeIdentifier(path));
							}	    						
						}else if("ipv4".equals(type) && stdOutLine.contains("(ESTABLISHED)")){
							String protocol = String.valueOf(tokens[7]).toLowerCase();
							//example of this token = 10.0.2.15:35859->172.231.72.152:443 (ESTABLISHED)
							String[] srchostport = tokens[8].split("->")[0].split(":");
							String[] dsthostport = tokens[8].split("->")[1].split(":");
							fds.put(fdString, new NetworkSocketIdentifier(srchostport[0], srchostport[1], 
									dsthostport[0], dsthostport[1], protocol));
						}else if("ipv6".equals(type) && stdOutLine.contains("(ESTABLISHED)")){
							String protocol = String.valueOf(tokens[8]).toLowerCase();
							//example of this token = [::1]:48644->[::1]:631 (ESTABLISHED)
							String src = tokens[9].split("->")[0];
							String dst = tokens[9].split("->")[1];
							String srcaddr = src.split("\\]:")[0].substring(1);
							String srcport = src.split("\\]:")[1];
							String dstaddr = dst.split("\\]:")[0].substring(1);
							String dstport = dst.split("\\]:")[1];
							fds.put(fdString, new NetworkSocketIdentifier(srcaddr, srcport, dstaddr, dstport, 
									protocol));
						}else if(type != null){
							ArtifactIdentifier identifier = null;
							String path = tokens[8];
							switch (type) {
								case "unix": 
									if(!"socket".equals(path)){ // abstract socket and don't know the name
										identifier = new UnixSocketIdentifier(path);
									}
									break;
								case "blk": identifier = new BlockDeviceIdentifier(path); break;
								case "chr": identifier = new CharacterDeviceIdentifier(path); break;
								case "dir": identifier = new DirectoryIdentifier(path); break;
								case "link": identifier = new LinkIdentifier(path); break;
								case "reg": identifier = new FileIdentifier(path); break;
								default: break;
							}
							if(identifier != null){
								fds.put(fdString, identifier);
							}
						}
					}
				}
				return fds;
			}else{
				output.log();
			}
		}catch(Exception e){
			logger.log(Level.WARNING, "Failed to read file descriptors for pid: " + pid, e);
		}
		return null;
	}
	
	private SimpleEntry<ProcessIdentifier, AgentIdentifier> createProcessFromProcFS(String pid, long boottime){
		try{
			File procFile = new File("/proc/" + pid);
			if(procFile.exists()){
				String source = OPMConstants.SOURCE_PROCFS;
				
				// order of keys in the status file changed. So, now looping through the file to get the necessary ones
				int keysGottenCount = 0; // Used to stop reading the file once all the required keys have been gotten
				String line = null, nameline = null, ppidline = null, uidline = null, gidline = null;
				BufferedReader procReader = new BufferedReader(new FileReader(procFile.getAbsolutePath() + "/status"));
				while((line = procReader.readLine()) != null && keysGottenCount < 4){
					String tokens[] = line.split(":");
					String key = tokens[0].trim().toLowerCase();
					switch (key) {
					case "name":
						nameline = line;
						keysGottenCount++;
						break;
					case "ppid":
						ppidline = line;
						keysGottenCount++;
						break;
					case "uid":
						uidline = line;
						keysGottenCount++;
						break;
					case "gid":
						gidline = line;
						keysGottenCount++;
						break;
					default:
						break;
					}
				}
				procReader.close();

				File cwdFile = new File(procFile.getAbsolutePath() + "/cwd");
				String cwd = cwdFile.getCanonicalPath();
				
				BufferedReader statReader = new BufferedReader(new FileReader(procFile.getAbsolutePath() + "/stat"));
				String statline = statReader.readLine();
				statReader.close();

				BufferedReader cmdlineReader = new BufferedReader(new FileReader(procFile.getAbsolutePath() + "/cmdline"));
				String commandLine = cmdlineReader.readLine();
				cmdlineReader.close();

				String stats[] = statline.split("\\s+");
				double elapsedtime = CommonFunctions.parseDouble(stats[21], null) * 10;
				String startTime = String.valueOf(boottime + elapsedtime);
				
				String ppidString = ppidline.split("\\s+")[1];

				// see for order of uid, euid, suid, fsiud: http://man7.org/linux/man-pages/man5/proc.5.html
				String gidTokens[] = gidline.split("\\s+");
				String uidTokens[] = uidline.split("\\s+");
				
				String name = nameline.split("name:")[1].trim();
				commandLine = (commandLine == null) ? "" : commandLine.replace("\0", " ").replace("\"", "'").trim();

				ProcessIdentifier process = new ProcessIdentifier(pid, ppidString, name, cwd, commandLine, startTime, 
						null, getUnitId(), source);

				AgentIdentifier agent = null;
				if(simplify){
					agent = new AgentIdentifier(uidTokens[0], uidTokens[1], gidTokens[0], gidTokens[1]);
				}else{
					agent = new AgentIdentifier(uidTokens[0], uidTokens[1], gidTokens[0], gidTokens[1], 
							uidTokens[2], uidTokens[3], gidTokens[2], gidTokens[3]);
				}
				
				return new SimpleEntry<ProcessIdentifier, AgentIdentifier>(process, agent);
			}else{
				logger.log(Level.WARNING, "No /proc entry for pid: " + pid);
				return null;
			}
		}catch(Exception e){
			logger.log(Level.WARNING, "Unable to create process vertex from /proc for pid: " + pid, e);
			return null;
		}
	}
	
	private Long getBootTime(){
		BufferedReader boottimeReader = null;
		try{
			boottimeReader = new BufferedReader(new FileReader("/proc/stat"));
			String line;
			while((line = boottimeReader.readLine()) != null){
				StringTokenizer st = new StringTokenizer(line);
				if(st.nextToken().equals("btime")){
					return Long.parseLong(st.nextToken()) * 1000;
				}
			}
		}catch(Exception e){
			logger.log(Level.WARNING, "Error reading boot time information from /proc/", e);
		}finally{
			if(boottimeReader != null){
				try{
					boottimeReader.close();
				}catch(Exception e){
					// ignore
				}
			}
		}
		return null;
	}
}

class ProcessKey{
	String pid;
	String time; // starttime or null
	
	ProcessKey(String pid, String time){
		this.pid = pid;
		this.time = time;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((pid == null) ? 0 : pid.hashCode());
		result = prime * result + ((time == null) ? 0 : time.hashCode());
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
		ProcessKey other = (ProcessKey) obj;
		if (pid == null) {
			if (other.pid != null)
				return false;
		} else if (!pid.equals(other.pid))
			return false;
		if (time == null) {
			if (other.time != null)
				return false;
		} else if (!time.equals(other.time))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "ProcessKey [pid=" + pid + ", time=" + time + "]";
	}

}
