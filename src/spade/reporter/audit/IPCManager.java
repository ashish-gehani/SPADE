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
package spade.reporter.audit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import spade.core.AbstractEdge;
import spade.edge.opm.Used;
import spade.edge.opm.WasGeneratedBy;
import spade.reporter.Audit;
import spade.reporter.audit.artifact.ArtifactIdentifier;
import spade.reporter.audit.artifact.SystemVArtifactIdentifier;
import spade.reporter.audit.artifact.SystemVMessageQueueIdentifier;
import spade.reporter.audit.artifact.SystemVSharedMemoryIdentifier;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

public class IPCManager{

	public static enum IPCType{
		SHARED_MEMORY_SYSTEMV, MESSAGE_QUEUE_POSIX, MESSAGE_QUEUE_SYSTEMV
	}

	public static List<SYSCALL> getSyscallsFor(final IPCType ipcType) throws Exception{
		if(ipcType == null){
			throw new Exception("NULL IPCType to get system calls for");
		}else{
			switch(ipcType){
				case SHARED_MEMORY_SYSTEMV:
					return Arrays.asList(SYSCALL.SHMGET, SYSCALL.SHMDT, SYSCALL.SHMAT, SYSCALL.SHMCTL);
				case MESSAGE_QUEUE_POSIX:
					return Arrays.asList(SYSCALL.MQ_OPEN, SYSCALL.MQ_TIMEDSEND, SYSCALL.MQ_TIMEDRECEIVE, SYSCALL.MQ_UNLINK);
				case MESSAGE_QUEUE_SYSTEMV:
					return Arrays.asList(SYSCALL.MSGGET, SYSCALL.MSGSND, SYSCALL.MSGRCV, SYSCALL.MSGCTL);
				default:
					throw new Exception("Unexpected IPCType: " + ipcType);
			}
		}
	}

	public static List<SYSCALL> getSyscallsForAll() throws Exception{
		final List<SYSCALL> all = new ArrayList<SYSCALL>();
		for(IPCType ipcType : IPCType.values()){
			all.addAll(getSyscallsFor(ipcType));
		}
		return all;
	}

	public static List<String> getSyscallNamesForAuditctlForAll() throws Exception{
		final List<String> all = new ArrayList<String>();
		for(final SYSCALL syscall : getSyscallsForAll()){
			all.add(syscall.name().toLowerCase());
		}
		return all;
	}

	///////////////////////////////////////////////////////

	private final static int IPC_CREAT = 00001000;
	private final static int S_IWUSR = 00200;
	private final static int S_IWGRP = 00020;
	private final static int S_IWOTH = 00002;
	private final static int IPC_RMID = 0;
	private final static int IPC_PRIVATE = 0;

	private final static int SHM_EXEC = 0100000;
	private final static int SHM_RDONLY = 010000;
	private final static int SHM_REMAP = 040000;
	private final static int SHM_RND = 020000;

	private final Audit reporter;

	public IPCManager(final Audit reporter){
		this.reporter = reporter;
	}
	
	public final void handleMq_open(final Map<String, String> eventData, final SYSCALL syscall){
		reporter.handleOpen(eventData, syscall);
	}
	
	public final void handleMq_timedsend(final Map<String, String> eventData, final SYSCALL syscall){
		reporter.handleIOEvent(syscall, eventData, false, eventData.get(AuditEventReader.MSG_LEN));
	}

	public final void handleMq_timedreceive(final Map<String, String> eventData, final SYSCALL syscall){
		reporter.handleIOEvent(syscall, eventData, true, eventData.get(AuditEventReader.MSG_LEN));
	}

	public final void handleMq_unlink(final Map<String, String> eventData, final SYSCALL syscall){
		reporter.handleUnlink(eventData, syscall);
	}

	////

	public final void handleShmget(final Map<String, String> eventData, final SYSCALL syscall){
		handleSystemVget(eventData, syscall, OPMConstants.SUBTYPE_SYSV_SHARED_MEMORY,
				eventData.get(AuditEventReader.ARG2), eventData.get(AuditEventReader.ARG1));
	}

	public final void handleShmdt(final Map<String, String> eventData, final SYSCALL syscall){
		// Nothing to note TODO
	}

	public final void handleShmat(final Map<String, String> eventData, final SYSCALL syscall){
		// shmat() receives the following messages(s):
		// - SYSCALL
		// - IPC [ optional ]
		// - EOE
		final String eventId = eventData.get(AuditEventReader.EVENT_ID);
		final String eventTime = eventData.get(AuditEventReader.TIME);
		final String id = eventData.get(AuditEventReader.ARG0);

		boolean updateVersion = false;

		final String flagsStr = eventData.get(AuditEventReader.ARG2);
		final int flagsInt = Integer.parseInt(flagsStr);
		String flagsAnnotation = "";
		if((flagsInt & SHM_EXEC) == SHM_EXEC){
			updateVersion = true;
			flagsAnnotation += "SHM_EXEC|";
		}
		if((flagsInt & SHM_RDONLY) == SHM_RDONLY){
			updateVersion = false;
			flagsAnnotation += "SHM_RDONLY|";
		}
		if((flagsInt & SHM_REMAP) == SHM_REMAP){
			updateVersion = true;
			flagsAnnotation += "SHM_REMAP|";
		}
		if((flagsInt & SHM_RND) == SHM_RND){
			updateVersion = true;
			flagsAnnotation += "SHM_RND|";
		}

		if(!flagsAnnotation.isEmpty()){
			flagsAnnotation = flagsAnnotation.substring(0, flagsAnnotation.length() - 1);
		}

		final ArtifactIdentifier identifier = createSystemVArtifactIdentifier(eventData, id,
				OPMConstants.SUBTYPE_SYSV_SHARED_MEMORY);

		if(updateVersion){
			reporter.getArtifactManager().artifactVersioned(identifier);
		}

		final Process process = reporter.getProcessManager().handleProcessFromSyscall(eventData);
		final Artifact artifact = reporter.putArtifactFromSyscall(eventData, identifier);

		final AbstractEdge edge;
		if(updateVersion){
			edge = new WasGeneratedBy(artifact, process);
		}else{
			edge = new Used(process, artifact);
		}

		if(!flagsAnnotation.isEmpty()){
			edge.addAnnotation(OPMConstants.EDGE_FLAGS, flagsAnnotation);
		}
		reporter.putEdge(edge, reporter.getOperation(syscall), eventTime, eventId, OPMConstants.SOURCE_AUDIT_SYSCALL);
	}

	public final void handleShmctl(final Map<String, String> eventData, final SYSCALL syscall){
		handleSystemVCtl(eventData, syscall, OPMConstants.SUBTYPE_SYSV_SHARED_MEMORY);
	}

	public final void handleMsgget(final Map<String, String> eventData, final SYSCALL syscall){
		handleSystemVget(eventData, syscall, OPMConstants.SUBTYPE_SYSV_MSG_Q, eventData.get(AuditEventReader.ARG1),
				null);
	}

	public final void handleMsgsnd(final Map<String, String> eventData, final SYSCALL syscall){
		// msgsnd() receives the following messages(s):
		// - SYSCALL
		// - IPC [ optional ]
		// - EOE
		final String size = eventData.get(AuditEventReader.ARG2);
		handleMsgIO(eventData, syscall, size, false);
	}

	public final void handleMsgrcv(final Map<String, String> eventData, final SYSCALL syscall){
		// msgrcv() receives the following messages(s):
		// - SYSCALL
		// - IPC [ optional ]
		// - EOE
		final String size = eventData.get(AuditEventReader.EXIT);
		handleMsgIO(eventData, syscall, size, true);
	}

	private final void handleMsgIO(final Map<String, String> eventData, final SYSCALL syscall, final String size,
			final boolean isRead){
		final String eventId = eventData.get(AuditEventReader.EVENT_ID);
		final String eventTime = eventData.get(AuditEventReader.TIME);
		final String msgQId = eventData.get(AuditEventReader.ARG0);
		final ArtifactIdentifier artifactIdentifier = createSystemVArtifactIdentifier(eventData, msgQId,
				OPMConstants.SUBTYPE_SYSV_MSG_Q);

		final Process process = reporter.getProcessManager().handleProcessFromSyscall(eventData);

		if(!isRead){
			reporter.getArtifactManager().artifactVersioned(artifactIdentifier);
		}

		final Artifact artifact = reporter.putArtifactFromSyscall(eventData, artifactIdentifier);

		final AbstractEdge edge;
		if(isRead){
			edge = new Used(process, artifact);
		}else{
			edge = new WasGeneratedBy(artifact, process);
		}

		edge.addAnnotation(OPMConstants.EDGE_SIZE, size);
		reporter.putEdge(edge, reporter.getOperation(syscall), eventTime, eventId, OPMConstants.SOURCE_AUDIT_SYSCALL);
	}

	public final void handleMsgctl(final Map<String, String> eventData, final SYSCALL syscall){
		handleSystemVCtl(eventData, syscall, OPMConstants.SUBTYPE_SYSV_MSG_Q);
	}

	private final SystemVArtifactIdentifier createSystemVArtifactIdentifier(final Map<String, String> eventData,
			final String id, final String subtype){
		String ouidStr = eventData.get(AuditEventReader.OUID);
		String ogidStr = eventData.get(AuditEventReader.OGID);

		if(ouidStr == null){
			ouidStr = eventData.get(AuditEventReader.UID);
		}
		if(ogidStr == null){
			ogidStr = eventData.get(AuditEventReader.GID);
		}
		
		final String ipcNamespace;
		if(reporter.getProcessManager().namespaces){
			final String pid = eventData.get(AuditEventReader.PID);
			ipcNamespace = reporter.getProcessManager().getIpcNamespace(pid);
		}else{
			ipcNamespace = null;
		}
		
		if(subtype.equals(OPMConstants.SUBTYPE_SYSV_MSG_Q)){
			return new SystemVMessageQueueIdentifier(id, ouidStr, ogidStr, ipcNamespace);
		}else if(subtype.equals(OPMConstants.SUBTYPE_SYSV_SHARED_MEMORY)){
			return new SystemVSharedMemoryIdentifier(id, ouidStr, ogidStr, ipcNamespace);
		}else{
			throw new RuntimeException("Unexpected subtype for SystemV artifact identifier: " + subtype);
		}
	}

	private final void handleSystemVget(final Map<String, String> eventData, final SYSCALL syscall,
			final String subtype, final String flagsStr, final String size){
		// msgget/shmget() receives the following messages(s):
		// - SYSCALL
		// - IPC [ optional ]
		// - EOE

		final String eventId = eventData.get(AuditEventReader.EVENT_ID);
		final String eventTime = eventData.get(AuditEventReader.TIME);
		final String id = eventData.get(AuditEventReader.EXIT);
		final String ftokGeneratedKeyString = eventData.get(AuditEventReader.ARG0);
		final int ftokGeneratedKeyInt = Integer.parseInt(ftokGeneratedKeyString);

		final int flagsInt = Integer.parseInt(flagsStr);
		final int permissionsInt = flagsInt & ~512; // get the 9 least significant bits
		final String permissionString = PathRecord.parsePermissions(String.format("%o", permissionsInt));

		final boolean isCreate = ((flagsInt & IPC_CREAT) == IPC_CREAT) || (ftokGeneratedKeyInt == IPC_PRIVATE);
		final boolean isWrite = ((permissionsInt & S_IWUSR) == S_IWUSR) || ((permissionsInt & S_IWGRP) == S_IWGRP)
				|| ((permissionsInt & S_IWOTH) == S_IWOTH);

		final ArtifactIdentifier artifactIdentifier = createSystemVArtifactIdentifier(eventData, id, subtype);

		final String flagsAnnotationString;

		if(isCreate){
			if(ftokGeneratedKeyInt == IPC_PRIVATE){
				flagsAnnotationString = "IPC_CREAT|IPC_PRIVATE";
			}else{
				flagsAnnotationString = "IPC_CREAT";
			}
			reporter.getArtifactManager().artifactCreated(artifactIdentifier);
		}else{
			flagsAnnotationString = null;
			if(isWrite){
				reporter.getArtifactManager().artifactVersioned(artifactIdentifier);
			}
		}

		final Process process = reporter.getProcessManager().handleProcessFromSyscall(eventData);
		final Artifact artifact = reporter.putArtifactFromSyscall(eventData, artifactIdentifier);

		final AbstractEdge edge;
		if(isWrite || isCreate){
			edge = new WasGeneratedBy(artifact, process);
		}else{
			edge = new Used(process, artifact);
		}

		if(flagsAnnotationString != null){
			edge.addAnnotation(OPMConstants.EDGE_FLAGS, flagsAnnotationString);
		}
		if(size != null){
			edge.addAnnotation(OPMConstants.EDGE_SIZE, size);
		}
		edge.addAnnotation(OPMConstants.EDGE_MODE, permissionString);
		reporter.putEdge(edge, reporter.getOperation(syscall), eventTime, eventId, OPMConstants.SOURCE_AUDIT_SYSCALL);
	}

	private final void handleSystemVCtl(final Map<String, String> eventData, final SYSCALL syscall,
			final String subtype){
		// msgctl/shmctl() receives the following messages(s):
		// - SYSCALL
		// - IPC [ optional ]
		// - EOE
		if(reporter.getFlagControl()){
			final String cmdString = eventData.get(AuditEventReader.ARG1);
			final int cmdInt = Integer.parseInt(cmdString);
			if(cmdInt == IPC_RMID){
				final String eventId = eventData.get(AuditEventReader.EVENT_ID);
				final String eventTime = eventData.get(AuditEventReader.TIME);
				final String id = eventData.get(AuditEventReader.ARG0);

				final ArtifactIdentifier artifactIdentifier = createSystemVArtifactIdentifier(eventData, id, subtype);

				final Process process = reporter.getProcessManager().handleProcessFromSyscall(eventData);
				final Artifact artifact = reporter.putArtifactFromSyscall(eventData, artifactIdentifier);
				final WasGeneratedBy deletedEdge = new WasGeneratedBy(artifact, process);
				reporter.putEdge(deletedEdge, reporter.getOperation(syscall), eventTime, eventId,
						OPMConstants.SOURCE_AUDIT_SYSCALL);
			}
		}
	}
}
