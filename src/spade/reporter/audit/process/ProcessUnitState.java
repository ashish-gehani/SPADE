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

/**
 * Maintains the state to deduplicate, and manages process and unit vertices
 */
public class ProcessUnitState{

	// Process identifier cannot be modified for a state. Only set in constructor.
	private ProcessIdentifier process;
	
	// The current agent for the process and the unit (if active).
	private AgentIdentifier agent;
	
	// The currently active unit or null (no active unit).
	private UnitIdentifier unit;
	
	// + Needed for the case when unit dependency for an exited process seen and need to recreate
	// the unit vertex. spadeAuditBridge masks which agent was current when write was done by a
	// unit so using the last agent seen for that unit. NOTE: Memory overhead.
	// + Possible fix for memory overhead could be that spadeAuditBridge reports memory ops by units
	// to Audit and they are used to keep agents for only those units which did the write. 
	private Map<UnitIdentifier, AgentIdentifier> unitToLastAgent = 
			new HashMap<UnitIdentifier, AgentIdentifier>();
	
	// Needed when a process exits and it's state is being removed.
	// If true then information needed to recreate a unit vertex is not removed.
	// If false then everything cleared.
	private boolean hadUnits = false;
	
	protected ProcessUnitState(ProcessIdentifier process, AgentIdentifier agent){
		this.process = process;
		this.agent = agent;
	}
	
	protected ProcessIdentifier getProcess(){
		return process;
	}
	
	protected AgentIdentifier getAgent(){
		return agent;
	}
	
	protected UnitIdentifier getUnit(){
		return unit;
	}
	
	/**
	 * Sets the current agent to the argument passed.
	 * Also if a unit is active then sets the agent for that unit too (Needed for unit dependency event)
	 * 
	 * @param agent new agent for the process
	 */
	protected void setAgent(AgentIdentifier agent){
		this.agent = agent;
		if(unit != null){
			unitToLastAgent.put(unit, agent);
		}
	}
	
	protected void unitEnter(UnitIdentifier unit){
		this.unit = unit;
		unitToLastAgent.put(unit, agent);
		if(!hadUnits){
			hadUnits = true;
		}
	}
	
	protected void unitExit(){
		unit = null;
	}
	
	protected boolean isUnitActive(){
		return unit != null;
	}
	
	protected boolean hadUnits(){
		return hadUnits;
	}
	
	protected AgentIdentifier getAgentForUnit(UnitIdentifier unit){
		return unitToLastAgent.get(unit);
	}
	
	// Only removes the state that is not needed after the process exits
	protected void partialClean(){
		unit = null;
		agent = null;
	}
}
