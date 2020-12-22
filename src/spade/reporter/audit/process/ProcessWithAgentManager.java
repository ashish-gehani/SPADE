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
import spade.reporter.audit.LinuxConstants;
import spade.reporter.audit.OPMConstants;
import spade.utility.HelperFunctions;
import spade.vertex.opm.Process;

/**
 * Manages deduplication of process vertices where agent info is contained inside process vertices.
 * 
 * Uses ProcessUnitWithAgentState to decide if a process vertex has been put before or not.
 */
public class ProcessWithAgentManager extends ProcessManager{

	public ProcessWithAgentManager(Audit reporter, boolean simplify, boolean units, boolean namespaces, final LinuxConstants platformConstants) throws Exception{
		super(reporter, simplify, units, namespaces, platformConstants);
	}
	
	protected void clearAll(){}

	protected Process buildVertex(ProcessIdentifier process, AgentIdentifier agent, UnitIdentifier unit,
			NamespaceIdentifier namespace){
		Process vertex = new Process();
		vertex.addAnnotations(process.getAnnotationsMap());
		vertex.addAnnotations(agent.getAnnotationsMap());
		if(unit != null){
			// remove seen time if it exists
			vertex.removeAnnotation(OPMConstants.PROCESS_SEEN_TIME);
			// replaces 'unit', 'source', and 'start time' annotations
			vertex.addAnnotations(unit.getAnnotationsMap());
		}
		vertex.addAnnotations(namespace.getAnnotationsMap());
		return vertex;
	}

	protected Process putProcessVertex(String time, String eventId, ProcessIdentifier process, AgentIdentifier agent, 
			NamespaceIdentifier namespace, String source){
		ProcessWithAgentState state = new ProcessWithAgentState(process, agent, namespace);
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

	protected void handleAgentUpdate(String timeString, String eventId, String pid, AgentIdentifier newAgent, 
			NamespaceIdentifier namespace, String operation){
		String source = OPMConstants.SOURCE_AUDIT_SYSCALL;
		ProcessWithAgentState state = (ProcessWithAgentState)getProcessUnitState(pid);
		Double time = HelperFunctions.parseDouble(timeString, null);
		if(state != null){
			Process oldProcessVertex = buildVertex(state.getProcess(), state.getAgent(), null, namespace);
			Process newProcessVertex = buildVertex(state.getProcess(), newAgent, null, namespace);
			if(state.isUnitActive()){
				UnitIdentifier unit = state.getUnit();
				
				Process oldUnitVertex = buildVertex(state.getProcess(), state.getAgent(), unit, namespace);
				Process newUnitVertex = buildVertex(state.getProcess(), newAgent, unit, namespace);

				if(!state.isAgentAndNamespaceSeenBeforeForProcess(newAgent, namespace)){
					getReporter().putVertex(newProcessVertex);
				}
				
				WasTriggeredBy newToOldProcess = new WasTriggeredBy(newProcessVertex, oldProcessVertex);
				getReporter().putEdge(newToOldProcess, operation, timeString, eventId, source);

				if(!state.isAgentAndNamespaceSeenBeforeForUnit(newAgent, namespace)){
					getReporter().putVertex(newUnitVertex);
				}
				
				WasTriggeredBy newToOldUnit = new WasTriggeredBy(newUnitVertex, oldUnitVertex);
				getReporter().putEdge(newToOldUnit, operation, timeString, eventId, source);

				WasTriggeredBy newUnitToNewProcess = new WasTriggeredBy(newUnitVertex, newProcessVertex);
				getReporter().putEdge(newUnitToNewProcess, OPMConstants.OPERATION_UNIT, unit.startTime, unit.eventId,
						OPMConstants.SOURCE_BEEP);
				
				// TODO order of vertices and edges
				
				state.setAgentAndNamespace(time, newAgent, namespace);
			}else{
				if(!state.isAgentAndNamespaceSeenBeforeForProcess(newAgent, namespace)){
					getReporter().putVertex(newProcessVertex);
				}
				state.setAgentAndNamespace(time, newAgent, namespace);
				WasTriggeredBy newToOldProcess = new WasTriggeredBy(newProcessVertex, oldProcessVertex);
				getReporter().putEdge(newToOldProcess, operation, timeString, eventId, source);
			}
		}else{
			getReporter().log(Level.INFO, "Tried to update agent without seeing process", null, timeString, eventId, null);
		}
	}
	
	protected void handleNamespaceUpdate(String timeString, String eventId, String pid, AgentIdentifier agent, 
			NamespaceIdentifier newNamespace, String operation){
		String source = OPMConstants.SOURCE_AUDIT_SYSCALL;
		ProcessWithAgentState state = (ProcessWithAgentState)getProcessUnitState(pid);
		Double time = HelperFunctions.parseDouble(timeString, null);
		if(state != null){
			Process oldProcessVertex = buildVertex(state.getProcess(), state.getAgent(), null, state.getNamespace());
			Process newProcessVertex = buildVertex(state.getProcess(), state.getAgent(), null, newNamespace);
			if(state.isUnitActive()){
				UnitIdentifier unit = state.getUnit();
				
				Process oldUnitVertex = buildVertex(state.getProcess(), state.getAgent(), unit, state.getNamespace());
				Process newUnitVertex = buildVertex(state.getProcess(), state.getAgent(), unit, newNamespace);

				if(!state.isAgentAndNamespaceSeenBeforeForProcess(agent, newNamespace)){
					getReporter().putVertex(newProcessVertex);
				}
				
				WasTriggeredBy newToOldProcess = new WasTriggeredBy(newProcessVertex, oldProcessVertex);
				getReporter().putEdge(newToOldProcess, operation, timeString, eventId, source);

				if(!state.isAgentAndNamespaceSeenBeforeForUnit(agent, newNamespace)){
					getReporter().putVertex(newUnitVertex);
				}
				
				WasTriggeredBy newToOldUnit = new WasTriggeredBy(newUnitVertex, oldUnitVertex);
				getReporter().putEdge(newToOldUnit, operation, timeString, eventId, source);

				WasTriggeredBy newUnitToNewProcess = new WasTriggeredBy(newUnitVertex, newProcessVertex);
				getReporter().putEdge(newUnitToNewProcess, OPMConstants.OPERATION_UNIT, unit.startTime, unit.eventId,
						OPMConstants.SOURCE_BEEP);
				
				// TODO order of vertices and edges
				
				state.setAgentAndNamespace(time, agent, newNamespace);
			}else{
				if(!state.isAgentAndNamespaceSeenBeforeForProcess(agent, newNamespace)){
					getReporter().putVertex(newProcessVertex);
				}
				state.setAgentAndNamespace(time, agent, newNamespace);
				WasTriggeredBy newToOldProcess = new WasTriggeredBy(newProcessVertex, oldProcessVertex);
				getReporter().putEdge(newToOldProcess, operation, timeString, eventId, source);
			}
		}else{
			getReporter().log(Level.INFO, "Tried to update namespace without seeing a process", null, timeString, eventId, null);
		}
	}
}
