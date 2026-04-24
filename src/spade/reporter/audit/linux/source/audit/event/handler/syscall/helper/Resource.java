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

import spade.reporter.audit.linux.platform.resource.ID;
import spade.reporter.audit.linux.platform.runtime.ResourceTable;
import spade.reporter.audit.linux.provenance.SourceEvent;
import spade.reporter.audit.linux.provenance.PlatformProcess;
import spade.reporter.audit.linux.provenance.PlatformResource;
import spade.reporter.audit.linux.provenance.event.resource.create.Event;
import spade.reporter.audit.linux.source.audit.event.handler.Context;
import spade.reporter.audit.linux.source.audit.event.record.Syscall;

public class Resource{

	public static void create(
		final List<spade.reporter.audit.core.provenance.event.Event> result,
		final Context context,
		final Syscall syscallRecord,
		final spade.reporter.audit.linux.platform.process.State processState,
		final ID resourceId
	){
		final ResourceTable resourceTable = context.getPlatformContext().getRuntimeState().getResourceTable();

		spade.reporter.audit.linux.platform.resource.State resourceState = resourceTable.get(resourceId);
		if(resourceState != null){
			resourceState.incrementEpoch();
		}else{
            resourceState = new spade.reporter.audit.linux.platform.resource.State(resourceId);
		    resourceTable.put(resourceId, resourceState);
        }

		final spade.reporter.audit.linux.source.audit.event.ID auditEventId = syscallRecord.getId();
		final Event createEvent = new Event(
			context.getPlatformContext().nextProvEventId(),
			new SourceEvent(auditEventId),
			new PlatformProcess(processState.getId()),
			new PlatformResource(resourceId)
		);
		result.add(createEvent);
	}

}
