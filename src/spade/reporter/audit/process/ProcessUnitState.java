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

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.utility.CommonFunctions;
import spade.utility.Series;

/**
 * Maintains the state to deduplicate, and manages process and unit vertices
 */
public class ProcessUnitState implements Serializable{

	private static final long serialVersionUID = -5842952642201154622L;

	private static final Logger logger = Logger.getLogger(ProcessUnitState.class.getName());
	
	// Process identifier cannot be modified for a state. Only set in constructor.
	private ProcessIdentifier process;
	
	// The current agent for the process and the unit (if active).
	private AgentIdentifier agent;
	
	// The currently active unit or null (no active unit).
	private UnitIdentifier unit;
	
	// Id of the thread group leader. Refers to self by default
	// Not keeping the time because there can be only one active process with the same pid at a time.
	private String threadGroupId;
	
	private Series<Double, AgentIdentifier> timeToAgent = new Series<Double, AgentIdentifier>();
	
	// Needed when a process exits and it's state is being removed.
	// If true then information needed to recreate a unit vertex is not removed.
	// If false then everything cleared.
	private boolean hadUnits = false;
	
	protected ProcessUnitState(ProcessIdentifier process, AgentIdentifier agent){
		this.process = process;
		this.agent = agent;
		threadGroupId = process.pid;
		String timeString = process.startTime == null ? process.seenTime : process.startTime;
		Double time = CommonFunctions.parseDouble(timeString, null);
		if(time != null){
			timeToAgent.add(time, agent);
		}else{
			logger.log(Level.WARNING, "Failed to get start/seen time for process: " + process);
		}
	}
	
	protected void setThreadGroupId(String threadGroupId){
		this.threadGroupId = threadGroupId;
	}
	
	protected String getThreadGroupId(){
		return threadGroupId;
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
	 * @param time the time when the agent was set
	 */
	protected void setAgent(Double time, AgentIdentifier agent){
		this.agent = agent;
		if(time != null){
			timeToAgent.add(time, agent);
		}else{
			logger.log(Level.WARNING, "Failed to set agent because time is NULL");
		}
	}
	
	protected void unitEnter(UnitIdentifier unit){
		this.unit = unit;
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
	
	protected AgentIdentifier getAgentByTime(Double time){
		if(time == null){
			return null;
		}else{
			return timeToAgent.getBestMatch(time);
		}
	}
	
	// Only removes the state that is not needed after the process exits
	protected void partialClean(){
		unit = null;
		agent = null;
	}
}
