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

import java.util.HashSet;
import java.util.Set;

public class ProcessWithAgentState extends ProcessUnitState{

	// Needed to tell whether the process vertex with the new agent has already been reported or not.
	private Set<AgentIdentifier> previousProcessAgents = new HashSet<AgentIdentifier>();
	// Needed to tell whether the unit vertex with the new agent has already been reported or not.
	private Set<AgentIdentifier> previousUnitAgents = new HashSet<AgentIdentifier>();
	
	protected ProcessWithAgentState(ProcessIdentifier process, AgentIdentifier agent){
		super(process, agent);
		previousProcessAgents.add(agent);
	}
	
	protected void setAgent(AgentIdentifier agent){
		super.setAgent(agent);
		previousProcessAgents.add(agent);
		if(isUnitActive()){
			previousUnitAgents.add(agent);
		}
	}
	
	protected boolean isAgentSeenBeforeForUnit(AgentIdentifier agent){
		return previousUnitAgents.contains(agent);
	}
	
	protected boolean isAgentSeenBeforeForProcess(AgentIdentifier agent){
		return previousProcessAgents.contains(agent);
	}
	
	protected void unitEnter(UnitIdentifier unit){
		super.unitEnter(unit);
		previousUnitAgents.add(getAgent());
	}
	
	protected void unitExit(){
		super.unitExit();
		previousUnitAgents.clear();
	}
	
	protected void partialClean(){
		super.partialClean();
		previousProcessAgents.clear();
		previousUnitAgents.clear();
	}
}
