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

import spade.reporter.audit.core.platform.util.datastore.Data;
import spade.reporter.audit.core.platform.util.datastore.Key;
import spade.reporter.audit.linux.platform.resource.State;
import spade.reporter.audit.linux.platform.resource.VersionedID;
import spade.reporter.audit.linux.platform.runtime.ResourceTable;
import spade.reporter.audit.linux.provenance.event.ResourceCreate;
import spade.reporter.audit.linux.source.audit.event.ID;
import spade.reporter.audit.linux.provenance.ModelEvent;
import spade.reporter.audit.linux.provenance.ModelProcess;
import spade.reporter.audit.linux.provenance.ModelResource;
import spade.reporter.audit.linux.provenance.model.resource.ProvData;
import spade.reporter.audit.linux.source.audit.event.handler.Context;
import spade.reporter.audit.linux.source.audit.event.record.Syscall;

public class Resource{

	private static final Key PROV_DATA_KEY = new Key(0L, "provData");

	public static void create(
		final List<spade.reporter.audit.core.provenance.event.Event> result,
		final Context context,
		final Syscall syscallRecord,
		final spade.reporter.audit.linux.platform.process.State processState,
		final VersionedID resourceId
	){
		final ResourceTable resourceTable = context.getPlatformContext().getRuntimeState().getResourceTable();

		State resourceState = resourceTable.get(resourceId);
		if(resourceState != null){
			resourceState = resourceId.createNewState();
			resourceTable.put(resourceId, resourceState);
		}

		ProvData provData = null;
		if(!resourceState.getDataStore().contains(PROV_DATA_KEY)){
			provData = new ProvData();
			resourceState.getDataStore().put(PROV_DATA_KEY, new Data(PROV_DATA_KEY, provData));
		}else{
			provData = (ProvData) resourceState.getDataStore().get(PROV_DATA_KEY).getValue();
		}

		provData.incrementEpoch();

		final ID auditEventId = syscallRecord.getId();
		final ResourceCreate createEvent =
			new ResourceCreate(
				context.getPlatformContext().nextProvEventId(),
				new ModelEvent(auditEventId),
				new ModelProcess(processState.getId()),
				new ModelResource(resourceId)
			);
		result.add(createEvent);
	}

}
