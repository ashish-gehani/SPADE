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

import spade.reporter.audit.linux.platform.process.VersionedID;
import spade.reporter.audit.linux.platform.resource.ID;
import spade.reporter.audit.linux.provenance.ProvEvent;
import spade.reporter.audit.linux.provenance.ProvProcess;
import spade.reporter.audit.linux.provenance.ProvResource;
import spade.reporter.audit.linux.source.audit.event.handler.Context;
import spade.reporter.audit.linux.source.audit.event.record.Syscall;

public class Event{

	public static void access(
		final List<spade.reporter.audit.core.provenance.event.Event> result,
		final Context context,
		final Syscall syscallRecord,
		final VersionedID processId,
		final ID resourceId
	){
		final spade.reporter.audit.linux.source.audit.event.ID auditEventId = syscallRecord.getId();
		final spade.reporter.audit.linux.provenance.event.resource.access.Event accessEvent =
			new spade.reporter.audit.linux.provenance.event.resource.access.Event(
				context.getPlatformContext().nextProvEventId(),
				new ProvEvent(auditEventId),
				new ProvProcess(processId),
				new ProvResource(resourceId)
			);
		result.add(accessEvent);
	}

}
