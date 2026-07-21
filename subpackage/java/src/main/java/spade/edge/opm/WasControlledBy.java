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
package spade.edge.opm;

import spade.core.AbstractEdge;
import spade.vertex.opm.Agent;
import spade.vertex.opm.Process;

/**
 * WasControlledBy edge based on the OPM model.
 *
 * @author Dawood Tariq
 */
public class WasControlledBy extends AbstractEdge{

	private static final long serialVersionUID = 2718697821506451812L;

	public static final String typeValue = "WasControlledBy";

	/**
	 * Constructor for Process->Agent edge
	 *
	 * @param controlledProcess Process vertex
	 * @param controllingAgent  Agent vertex
	 */
	public WasControlledBy(Process controlledProcess, Agent controllingAgent){
		super();
		setChildVertex(controlledProcess);
		setParentVertex(controllingAgent);
		setType(typeValue);
	}

	public WasControlledBy(final String bigHashCode, Process controlledProcess, Agent controllingAgent){
		super(bigHashCode);
		setChildVertex(controlledProcess);
		setParentVertex(controllingAgent);
		setType(typeValue);
	}
}
