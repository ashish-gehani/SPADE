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

import java.util.logging.Level;

import spade.edge.opm.WasTriggeredBy;
import spade.reporter.Audit;
import spade.reporter.audit.OPMConstants;
import spade.vertex.opm.Process;

/**
 * Manages deduplication of process vertices where agent info is contained inside process vertices.
 * 
 * Uses ProcessUnitWithAgentState to decide if a process vertex has been put before or not.
 */
public class ProcessWithAgentManager extends ProcessManager{

	public ProcessWithAgentManager(Audit reporter, boolean simplify, boolean units){
		super(reporter, simplify, units);
	}
	
	protected void clearAll(){}

	protected Process buildVertex(ProcessIdentifier process, AgentIdentifier agent, UnitIdentifier unit){
		Process vertex = new Process();
		vertex.addAnnotations(process.getAnnotationsMap());
		vertex.addAnnotations(agent.getAnnotationsMap());
		if(unit != null){
			// remove seen time if it exists
			vertex.removeAnnotation(OPMConstants.PROCESS_SEEN_TIME);
			// replaces 'unit', 'source', and 'start time' annotations
			vertex.addAnnotations(unit.getAnnotationsMap());
		}
		return vertex;
	}

	protected Process putProcessVertex(String time, String eventId, ProcessIdentifier process, AgentIdentifier agent, 
			String source){
		ProcessWithAgentState state = new ProcessWithAgentState(process, agent);
		setProcessUnitState(state);

		String pid = process.pid;
		Process processVertex = getVertex(pid);
		getReporter().putVertex(processVertex);
		
		return getVertex(pid);
	}

	protected Process putUnitVertex(String time, String eventId, String pid, UnitIdentifier unit){
		ProcessUnitState state = getProcessUnitState(pid);
		state.unitEnter(unit);
		
		Process unitVertex = getVertex(pid);
		getReporter().putVertex(unitVertex);
		
		return getVertex(pid);
	}

	protected void handleAgentUpdate(String time, String eventId, String pid, AgentIdentifier newAgent, String operation){
		String source = OPMConstants.SOURCE_AUDIT_SYSCALL;
		ProcessWithAgentState state = (ProcessWithAgentState)getProcessUnitState(pid);
		if(state != null){
			Process oldProcessVertex = buildVertex(state.getProcess(), state.getAgent(), null);
			Process newProcessVertex = buildVertex(state.getProcess(), newAgent, null);
			if(state.isUnitActive()){
				UnitIdentifier unit = state.getUnit();
				
				Process oldUnitVertex = buildVertex(state.getProcess(), state.getAgent(), unit);
				Process newUnitVertex = buildVertex(state.getProcess(), newAgent, unit);

				if(!state.isAgentSeenBeforeForProcess(newAgent)){
					getReporter().putVertex(newProcessVertex);
				}
				
				WasTriggeredBy newToOldProcess = new WasTriggeredBy(newProcessVertex, oldProcessVertex);
				getReporter().putEdge(newToOldProcess, operation, time, eventId, source);

				if(!state.isAgentSeenBeforeForUnit(newAgent)){
					getReporter().putVertex(newUnitVertex);
				}
				
				WasTriggeredBy newToOldUnit = new WasTriggeredBy(newUnitVertex, oldUnitVertex);
				getReporter().putEdge(newToOldUnit, operation, time, eventId, source);

				WasTriggeredBy newUnitToNewProcess = new WasTriggeredBy(newUnitVertex, newProcessVertex);
				getReporter().putEdge(newUnitToNewProcess, OPMConstants.OPERATION_UNIT, unit.startTime, unit.eventId,
						OPMConstants.SOURCE_BEEP);
				
				// TODO order of vertices and edges
				
				state.setAgent(newAgent);
			}else{
				if(!state.isAgentSeenBeforeForProcess(newAgent)){
					getReporter().putVertex(newProcessVertex);
				}
				state.setAgent(newAgent);
				WasTriggeredBy newToOldProcess = new WasTriggeredBy(newProcessVertex, oldProcessVertex);
				getReporter().putEdge(newToOldProcess, operation, time, eventId, source);
			}
		}else{
			getReporter().log(Level.INFO, "Tried to update agent without seeing process", null, time, eventId, null);
		}
	}
}
