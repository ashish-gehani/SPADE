/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International

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
package spade.reporter.audit.linux.source.audit.event.handler.syscall.helper;

import java.util.List;

import spade.reporter.audit.linux.platform.process.History;
import spade.reporter.audit.linux.platform.process.VersionedID;
import spade.reporter.audit.linux.platform.process.fd.Table;
import spade.reporter.audit.linux.platform.process.info.Info;
import spade.reporter.audit.linux.platform.process.info.Memory;
import spade.reporter.audit.linux.platform.process.info.credential.Group;
import spade.reporter.audit.linux.platform.process.info.credential.Tuple;
import spade.reporter.audit.linux.platform.process.info.credential.User;
import spade.reporter.audit.linux.platform.runtime.ProcessTable;
import spade.reporter.audit.linux.provenance.SourceEvent;
import spade.reporter.audit.linux.provenance.PlatformProcess;
import spade.reporter.audit.linux.provenance.event.process.create_synthetic.Event;
import spade.reporter.audit.linux.source.audit.event.handler.Context;
import spade.reporter.audit.linux.source.audit.event.record.Syscall;
import spade.reporter.audit.linux.source.audit.event.record.helper.ProcessInfo;
import spade.reporter.audit.linux.platform.constant.Constant;
import spade.reporter.audit.linux.type.credential.PID;
import spade.reporter.audit.linux.type.fs.Inode;
import spade.reporter.audit.linux.type.fs.Path;
import spade.reporter.audit.linux.type.namespace.Type;
import spade.reporter.audit.linux.type.time.Time;

public class Process{

	public static spade.reporter.audit.linux.platform.process.State getState(
		final List<spade.reporter.audit.core.provenance.event.Event> result,
		final Context context,
		final Syscall syscallRecord,
		final boolean createSyntheticIfNotFound
	){
		final spade.reporter.audit.linux.source.audit.event.ID auditEventId = syscallRecord.getId();
		final ProcessInfo processInfo = syscallRecord.getSyscallInfo().getProcessInfo();
		final PID pid = processInfo.getPid();

		final ProcessTable processTable = context.getPlatformContext().getRuntimeState().getProcessTable();
		spade.reporter.audit.linux.platform.process.State processState = processTable.getLatest(pid);
		if(processState != null){
			return processState;
		}
		if(!createSyntheticIfNotFound){
			return null;
		}
		final VersionedID processVersionedId = new VersionedID(pid);
		processState = createSynthetic(processVersionedId, processInfo, auditEventId);
		processTable.put(processVersionedId, processState);
		final Event createSyntheticEvent = new Event(
			context.getPlatformContext().nextProvEventId(),
			new SourceEvent(auditEventId),
			new PlatformProcess(processVersionedId)
		);
		result.add(createSyntheticEvent);
		return processState;
	}

	private static PID defaultPgid(){ return new PID(0L); }
	private static PID defaultSid(){ return new PID(0L); }
	private static Path defaultExe(){ return new Path(Constant.INSTANCE.PATH.DEFAULT_ROOT, Constant.INSTANCE.PATH.DEFAULT_ROOT); }
	private static Path defaultCwd(){ return new Path(Constant.INSTANCE.PATH.DEFAULT_ROOT, Constant.INSTANCE.PATH.DEFAULT_ROOT); }
	private static Path defaultRoot(){ return new Path(Constant.INSTANCE.PATH.DEFAULT_ROOT, Constant.INSTANCE.PATH.DEFAULT_ROOT); }
	private static spade.reporter.audit.linux.type.namespace.ID defaultNamespaceId(final Type type){
		return new spade.reporter.audit.linux.type.namespace.ID(type, new Inode(0L));
	}

	private static spade.reporter.audit.linux.platform.process.State createSynthetic(
		final VersionedID processVersionedId,
		final ProcessInfo processInfo,
		final spade.reporter.audit.linux.source.audit.event.ID eventId
	){
		final spade.reporter.audit.linux.platform.process.info.credential.Process processCred =
			new spade.reporter.audit.linux.platform.process.info.credential.Process(
				processInfo.getPid(),
				processInfo.getPpid(),
				defaultPgid(),
				defaultSid()
			);
		final Tuple cred = new Tuple(
			processCred,
			new User(
				processInfo.getUid(), processInfo.getEuid(),
				processInfo.getSuid(), processInfo.getFsuid()
			),
			new Group(
				processInfo.getGid(), processInfo.getEgid(),
				processInfo.getSgid(), processInfo.getFsgid()
			)
		);
		final spade.reporter.audit.linux.type.namespace.Tuple namespace =
			new spade.reporter.audit.linux.type.namespace.Tuple(
				defaultNamespaceId(Type.MOUNT),
				defaultNamespaceId(Type.USER),
				defaultNamespaceId(Type.NET),
				defaultNamespaceId(Type.PID),
				defaultNamespaceId(Type.PID_CHILDREN),
				defaultNamespaceId(Type.IPC),
				defaultNamespaceId(Type.CGROUP)
			);
		final String exeRaw = processInfo.getExe();
		final Info info = new Info(
			processInfo.getComm(),
			defaultCwd(),
			defaultRoot(),
			new Time(eventId.getTimestamp().getValue(), spade.reporter.audit.linux.type.time.Type.SEEN),
			exeRaw != null ? new Path(Constant.INSTANCE.PATH.DEFAULT_ROOT, exeRaw) : defaultExe(),
			namespace,
			cred,
			new Memory(),
			new spade.reporter.audit.linux.platform.process.info.unit.State()
		);
		return new spade.reporter.audit.linux.platform.process.State(processVersionedId, info, new Table(), new History());
	}

}
