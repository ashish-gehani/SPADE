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

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import spade.edge.opm.WasControlledBy;
import spade.edge.opm.WasTriggeredBy;
import spade.reporter.Audit;
import spade.reporter.audit.LinuxConstants;
import spade.reporter.audit.OPMConstants;
import spade.utility.HelperFunctions;
import spade.vertex.opm.Agent;
import spade.vertex.opm.Process;

/**
 * Manages deduplication of process vertices where agent info is NOT contained inside process vertices.
 */
public class ProcessWithoutAgentManager extends ProcessManager{

	/**
	 * Used to tell whether the agent has already be put or not. And also the source for the agent.
	 */
	private final Map<AgentIdentifier, String> agentToSource = new HashMap<AgentIdentifier, String>();
	
	public ProcessWithoutAgentManager(Audit reporter, boolean simplify, boolean units, boolean namespaces, final LinuxConstants platformConstants) throws Exception{
		super(reporter, simplify, units, namespaces, platformConstants);
	}
	
	protected void clearAll(){
		agentToSource.clear();
	}
	
	protected Process buildVertex(ProcessIdentifier process, AgentIdentifier agent, UnitIdentifier unit, NamespaceIdentifier namespace){
		Process vertex = new Process();
		vertex.addAnnotations(process.getAnnotationsMap());
		if(unit != null){
			// remove seen time if it exists
			vertex.removeAnnotation(OPMConstants.PROCESS_SEEN_TIME);
			// replaces 'unit', 'source', and 'start time' annotations
			vertex.addAnnotations(unit.getAnnotationsMap());
		}
		vertex.addAnnotations(namespace.getAnnotationsMap());
		return vertex;
	}
	
	/**
	 * Assumes that there exists a source value for the given agent instance
	 * 
	 * @param agent agent identifier
	 * @return agent vertex
	 */
	private Agent buildAgentVertex(AgentIdentifier agent){
		Agent vertex = new Agent();
		vertex.addAnnotations(agent.getAnnotationsMap());
		vertex.addAnnotation(OPMConstants.SOURCE, agentToSource.get(agent));
		return vertex;
	}
	
	/**
	 * If a key value pair already exists for the given agent then that agent is returned.
	 * 
	 * If no such key value pair exists then that is added, put, and returned.
	 * 
	 * @param agent agent identifier
	 * @param source procfs or syscall at the moment
	 * @return agent vertex
	 */
	private Agent putAgentVertex(AgentIdentifier agent, String source){
		if(agentToSource.get(agent) == null){
			agentToSource.put(agent, source);
			Agent agentVertex = buildAgentVertex(agent);
			getReporter().putVertex(agentVertex);
			return agentVertex;
		}else{
			return buildAgentVertex(agent);
		}
	}
	
	protected Process putProcessVertex(String time, String eventId, ProcessIdentifier process, AgentIdentifier agent, 
			NamespaceIdentifier namespace, String source){
		ProcessUnitState state = new ProcessUnitState(process, agent, namespace);
		setProcessUnitState(state);

		String pid = process.pid;
		Process processVertex = getVertex(pid);
		getReporter().putVertex(processVertex);
		
		Agent agentVertex = putAgentVertex(agent, source);
		
		WasControlledBy edge = new WasControlledBy(processVertex, agentVertex);
		getReporter().putEdge(edge, null, time, eventId, source); // TODO operation???
		
		return getVertex(pid);
	}
	
	protected Process putUnitVertex(String time, String eventId, String pid, UnitIdentifier unit){
		String source = OPMConstants.SOURCE_AUDIT_SYSCALL;
		ProcessUnitState state = getProcessUnitState(pid);
		state.unitEnter(unit);
		
		Process unitVertex = buildVertex(state.getProcess(), state.getAgent(), state.getUnit(), state.getNamespace());
		Agent agentVertex = putAgentVertex(state.getAgent(), source);
		getReporter().putVertex(unitVertex);
		
		WasControlledBy edge = new WasControlledBy(unitVertex, agentVertex);
		getReporter().putEdge(edge, null, time, eventId, source); // TODO operation???
		
		return getVertex(pid);
	}
	
	protected void handleAgentUpdate(String timeString, String eventId, String pid, AgentIdentifier newAgent, 
			NamespaceIdentifier namespace, String operation){
		String source = OPMConstants.SOURCE_AUDIT_SYSCALL;
		ProcessUnitState state = getProcessUnitState(pid);
		Double time = HelperFunctions.parseDouble(timeString, null);
		if(state != null){
			Process processVertex = buildVertex(state.getProcess(), null, null, namespace);
			Agent newAgentVertex = putAgentVertex(newAgent, source);
			if(state.isUnitActive()){
				Process unitVertex = buildVertex(state.getProcess(), null, state.getUnit(), namespace);
				
				state.setAgentAndNamespace(time, newAgent, namespace);
				
				WasControlledBy processToAgent = new WasControlledBy(processVertex, newAgentVertex);
				WasControlledBy unitToAgent = new WasControlledBy(unitVertex, newAgentVertex);
				
				getReporter().putEdge(processToAgent, operation, timeString, eventId, source);
				getReporter().putEdge(unitToAgent, operation, timeString, eventId, source);
			}else{
				state.setAgentAndNamespace(time, newAgent, namespace);
				WasControlledBy processToAgent = new WasControlledBy(processVertex, newAgentVertex);
				getReporter().putEdge(processToAgent, operation, timeString, eventId, source);
			}
		}else{
			getReporter().log(Level.INFO, "Tried to update agent without seeing process", null, timeString, eventId, null);
		}
	}
	
	protected void handleNamespaceUpdate(String timeString, String eventId, String pid, AgentIdentifier agent, 
			NamespaceIdentifier newNamespace, String operation){
		String source = OPMConstants.SOURCE_AUDIT_SYSCALL;
		ProcessUnitState state = getProcessUnitState(pid);
		Double time = HelperFunctions.parseDouble(timeString, null);
		if(state != null){
			Process oldProcessVertex = buildVertex(state.getProcess(), null, null, state.getNamespace());
			Process newProcessVertex = buildVertex(state.getProcess(), null, null, newNamespace);
			if(state.isUnitActive()){
				UnitIdentifier unit = state.getUnit();
				
				Process oldUnitVertex = buildVertex(state.getProcess(), null, unit, state.getNamespace());
				Process newUnitVertex = buildVertex(state.getProcess(), null, unit, newNamespace);

				if(!state.hasTheNamespaceEverBeenSeenForProcess(newNamespace)){
					getReporter().putVertex(newProcessVertex);	
				}
				
				WasTriggeredBy newToOldProcess = new WasTriggeredBy(newProcessVertex, oldProcessVertex);
				getReporter().putEdge(newToOldProcess, operation, timeString, eventId, source);

				getReporter().putVertex(newUnitVertex);
				
				WasTriggeredBy newToOldUnit = new WasTriggeredBy(newUnitVertex, oldUnitVertex);
				getReporter().putEdge(newToOldUnit, operation, timeString, eventId, source);

				WasTriggeredBy newUnitToNewProcess = new WasTriggeredBy(newUnitVertex, newProcessVertex);
				getReporter().putEdge(newUnitToNewProcess, OPMConstants.OPERATION_UNIT, unit.startTime, unit.eventId,
						OPMConstants.SOURCE_BEEP);
				
				// TODO order of vertices and edges
				
				state.setAgentAndNamespace(time, agent, newNamespace);
			}else{
				if(!state.hasTheNamespaceEverBeenSeenForProcess(newNamespace)){
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
