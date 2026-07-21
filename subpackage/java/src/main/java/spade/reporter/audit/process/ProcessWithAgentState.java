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

import java.util.AbstractMap.SimpleEntry;
import java.util.HashSet;
import java.util.Set;

public class ProcessWithAgentState extends ProcessUnitState{

	private static final long serialVersionUID = -614042966379285211L;
	
	private final Set<SimpleEntry<AgentIdentifier, NamespaceIdentifier>> previousProcessAgentsAndNamespaces = 
			new HashSet<SimpleEntry<AgentIdentifier, NamespaceIdentifier>>();
	private final Set<SimpleEntry<AgentIdentifier, NamespaceIdentifier>> previousUnitAgentsAndNamespaces = 
			new HashSet<SimpleEntry<AgentIdentifier, NamespaceIdentifier>>();
	
	protected ProcessWithAgentState(ProcessIdentifier process, AgentIdentifier agent, NamespaceIdentifier namespace){
		super(process, agent, namespace);
		previousProcessAgentsAndNamespaces.add(new SimpleEntry<AgentIdentifier, NamespaceIdentifier>(agent, namespace));
	}
	
	protected void setAgentAndNamespace(Double time, AgentIdentifier agent, NamespaceIdentifier namespace){
		super.setAgentAndNamespace(time, agent, namespace);
		previousProcessAgentsAndNamespaces.add(new SimpleEntry<AgentIdentifier, NamespaceIdentifier>(agent, namespace));
		if(isUnitActive()){
			previousUnitAgentsAndNamespaces.add(new SimpleEntry<AgentIdentifier, NamespaceIdentifier>(agent, namespace));
		}
	}
	
	protected boolean isAgentAndNamespaceSeenBeforeForUnit(AgentIdentifier agent, NamespaceIdentifier namespace){
		return previousUnitAgentsAndNamespaces.contains(new SimpleEntry<AgentIdentifier, NamespaceIdentifier>(agent, namespace));
	}
	
	protected boolean isAgentAndNamespaceSeenBeforeForProcess(AgentIdentifier agent, NamespaceIdentifier namespace){
		return previousProcessAgentsAndNamespaces.contains(new SimpleEntry<AgentIdentifier, NamespaceIdentifier>(agent, namespace));
	}
	
	protected void unitEnter(UnitIdentifier unit){
		super.unitEnter(unit);
		previousUnitAgentsAndNamespaces.add(new SimpleEntry<AgentIdentifier, NamespaceIdentifier>(getAgent(), getNamespace()));
	}
	
	protected void unitExit(){
		super.unitExit();
		previousUnitAgentsAndNamespaces.clear();
	}
	
	protected void partialClean(){
		super.partialClean();
		previousProcessAgentsAndNamespaces.clear();
		previousUnitAgentsAndNamespaces.clear();
	}
}
