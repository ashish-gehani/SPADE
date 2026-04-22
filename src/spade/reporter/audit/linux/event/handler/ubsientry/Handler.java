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
package spade.reporter.audit.linux.event.handler.ubsientry;

import spade.reporter.audit.linux.event.UbsiEntry;

public class Handler implements spade.reporter.audit.core.source.handler.Handler<UbsiEntry, Context>{

	@Override
	public void handle(Context context) {
		if(context == null){
			throw new IllegalArgumentException("Context cannot be NULL");
		}
		if(context.getEvent() == null){
			throw new IllegalArgumentException("Event in context cannot be NULL");
		}
		_handle(context);
	}

	private void _handle(Context context) {
		final UbsiEntry event = context.getEvent();

		String pid = event.getPid();
		String time = event.getTimestamp().getSecondsInAuditFormat();
		String eventId = String.valueOf(event.getId().getNum());

		String unitId = event.getUnitId();
		String unitIteration = event.getUnitIteration();
		String unitCount = event.getUnitCount();
		String unitStartTime = event.getUnitStartTime();

		// Process processVertex = null;

		// // remove the last unit if there was any
		// ProcessUnitState state = getProcessUnitState(pid);
		// if(state != null){
		// 	if(state.isUnitActive()){
		// 		state.unitExit();
		// 	}
		// 	processVertex = buildVertex(state.getProcess(), state.getAgent(), state.getUnit(), state.getNamespace());
		// }else{
		// 	processVertex = handleProcessFromSyscall(eventData);
		// }

		// UnitIdentifier unitIdentifier = new UnitIdentifier(unitId, unitIteration, unitCount, unitStartTime, eventId);

		// Process unitVertex = putUnitVertex(time, eventId, pid, unitIdentifier);

		// WasTriggeredBy edge = new WasTriggeredBy(unitVertex, processVertex);
		// reporter.putEdge(edge, reporter.getOperation(SYSCALL.UNIT), time, eventId, OPMConstants.SOURCE_BEEP);

		// return true;
	}

}
