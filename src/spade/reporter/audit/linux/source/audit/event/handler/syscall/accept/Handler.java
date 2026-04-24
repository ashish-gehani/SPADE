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
package spade.reporter.audit.linux.source.audit.event.handler.syscall.accept;

import java.util.ArrayList;
import java.util.List;

import spade.reporter.audit.core.source.event.handler.EventHandlingException;
import spade.reporter.audit.linux.platform.process.State;
import spade.reporter.audit.linux.platform.process.fd.Descriptor;
import spade.reporter.audit.linux.platform.process.fd.OpenMode;
import spade.reporter.audit.linux.platform.process.fd.Type;
import spade.reporter.audit.linux.platform.resource.network.Network;
import spade.reporter.audit.linux.source.audit.event.ID;
import spade.reporter.audit.linux.source.audit.event.handler.Context;
import spade.reporter.audit.linux.source.audit.event.record.Record;
import spade.reporter.audit.linux.source.audit.event.record.Sockaddr;
import spade.reporter.audit.linux.source.audit.event.record.Syscall;
import spade.reporter.audit.linux.source.audit.event.record.helper.SyscallInfo;
import spade.reporter.audit.linux.source.audit.event.record.type.saddr.IPv4Saddr;
import spade.reporter.audit.linux.source.audit.event.record.type.saddr.IPv6Saddr;
import spade.reporter.audit.linux.source.audit.event.record.type.saddr.Saddr;
import spade.reporter.audit.linux.source.audit.event.record.type.saddr.UnixSaddr;
import spade.reporter.audit.linux.source.audit.event.handler.syscall.helper.Process;
import spade.reporter.audit.linux.source.audit.event.handler.syscall.helper.Resource;
import spade.reporter.audit.linux.source.audit.event.syscall.Event;
import spade.reporter.audit.linux.type.fd.Num;
import spade.reporter.audit.linux.type.network.transport.Address;
import spade.reporter.audit.linux.type.network.transport.Protocol;

/**
 * Handles accept and accept4 syscalls.
 *
 * accept4 carries an additional flags argument (arg3: SOCK_NONBLOCK, SOCK_CLOEXEC)
 * but the socket address extraction is identical for both syscalls.
 */
public class Handler implements spade.reporter.audit.core.source.event.handler.Handler<ID, Event, Context>{

	@Override
	public List<spade.reporter.audit.core.provenance.event.Event> handle(
		final Event event, final Context context
	) throws EventHandlingException{
		final List<spade.reporter.audit.core.provenance.event.Event> result = new ArrayList<>();

		final Syscall syscallRecord = event.getSyscallRecord();
		final SyscallInfo syscallInfo = syscallRecord.getSyscallInfo();

		final Num sockFd = new Num((int) syscallInfo.getArg0()); // fd the connection arrived on
		final Num fd     = new Num((int) syscallInfo.getExit());  // fd of the accepted connection

		Sockaddr sockaddrRecord = null;
		for(final Record r : event.getRecords()){
			if(r instanceof Sockaddr){
				sockaddrRecord = (Sockaddr) r;
				break;
			}
		}

		final State processState = Process.getState(result, context, syscallRecord, true);

		final Descriptor descriptor = processState.getFdTable().get(sockFd);

		final Saddr saddr = sockaddrRecord.getSaddr();

		switch(saddr.getFamily()){
			case IPV4:
				handleNetwork(result, event, context, syscallRecord, processState, sockFd, descriptor, fd, (IPv4Saddr) saddr);
				break;
			case IPV6:
				handleNetwork(result, event, context, syscallRecord, processState, sockFd, descriptor, fd, (IPv6Saddr) saddr);
				break;
			case UNIX:
				handleUnix(result, event, context, syscallRecord, processState, sockFd, descriptor, fd, (UnixSaddr) saddr);
				break;
			default:
				break;
		}

		return result;
	}

	private void handleNetwork(
		final List<spade.reporter.audit.core.provenance.event.Event> result,
		final Event event, final Context context,
		final Syscall syscallRecord, final State processState, final Num sockFd, final Descriptor sockFdDescriptor, final Num fd, final Saddr fdSaddr
	){
		if(sockFdDescriptor == null || !(sockFdDescriptor.getResource() instanceof Network)){
			return;
		}
		final Network boundNetwork = (Network) sockFdDescriptor.getResource();
		final Address src = boundNetwork.getSrc();
		final Protocol protocol = boundNetwork.getProtocol();

		final Address dst;
		if(fdSaddr instanceof IPv4Saddr){
			dst = ((IPv4Saddr) fdSaddr).getAddress();
		}else{
			dst = ((IPv6Saddr) fdSaddr).getAddress();
		}
		if(dst == null){
			return;
		}

		final Network fdNetwork = new Network(protocol, src, dst);

		final spade.reporter.audit.linux.platform.resource.ID fdResourceID =
			new spade.reporter.audit.linux.platform.resource.network.ID(fdNetwork, processState);
		final Descriptor fdObj = new Descriptor(
			Type.NETWORK_SOCKET, fd, OpenMode.READ_WRITE, fdNetwork
		);
		processState.getFdTable().put(fd, fdObj);

		Resource.create(result, context, syscallRecord, processState, fdResourceID);

		spade.reporter.audit.linux.source.audit.event.handler.syscall.helper.Event.access(
			result, context, syscallRecord, processState.getId(), fdResourceID
		);
	}

	private void handleUnix(
		final List<spade.reporter.audit.core.provenance.event.Event> result,
		final Event event, final Context context,
		final Syscall syscallRecord, final State processState, final Num sockFd, final Descriptor sockFdDescriptor, final Num fd, final UnixSaddr fdSaddr
	){
		if(sockFdDescriptor == null || !(sockFdDescriptor.getResource() instanceof spade.reporter.audit.linux.platform.resource.fs.UnixSocket)){
			return;
		}
		final spade.reporter.audit.linux.platform.resource.fs.UnixSocket unixSocket =
			(spade.reporter.audit.linux.platform.resource.fs.UnixSocket) sockFdDescriptor.getResource();

		final spade.reporter.audit.linux.platform.resource.fs.ID fdResourceID =
			new spade.reporter.audit.linux.platform.resource.fs.ID(unixSocket, processState);
		final Descriptor fdObj = new Descriptor(Type.UNIX_SOCKET, fd, OpenMode.READ_WRITE, unixSocket);
		processState.getFdTable().put(fd, fdObj);

		Resource.create(result, context, syscallRecord, processState, fdResourceID);
		spade.reporter.audit.linux.source.audit.event.handler.syscall.helper.Event.access(
			result, context, syscallRecord, processState.getId(), fdResourceID
		);
	}

}
