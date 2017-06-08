/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2012 SRI International

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
package spade.filter;


import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;

/**
 * A filter to drop annotations passed in arguments.
 * 
 * Arguments format: keys=name,version,epoch
 * 
 * Note 1: Filter relies on two things for successful use of Java Reflection API:
 * 1) The vertex objects passed have an empty constructor
 * 2) The edge objects passed have a constructor with source vertex and a destination vertex (in that order)
 * 
 * Note 2: Creating copy of passed vertices and edges instead of just removing the annotations from the existing ones
 * because the passed vertices and edges might be in use by some other classes specifically the reporter that
 * generated them. In future, maybe shift the responsibility of creating a copy to Kernel before it sends out
 * the vertices and edges to filters.
 */
public class AttributeLabel extends AbstractFilter{

	private Logger logger = Logger.getLogger(this.getClass().getName());
	private String maliciousName = null;
	private final String PROCESS = "Process";
	private final String LABEL = "label";
	private final String BENIGN = "benign";
	private final String MALICIOUS = "malicious";
	private final String NAME = "name";
	
	public boolean initialize(String arguments){
		
		//Must not be null or empty
		if(!(arguments == null || arguments.trim().isEmpty())){
			maliciousName = arguments.substring(1);
		}
		return true;
		
	}
	
	@Override
	public void putVertex(AbstractVertex incomingVertex) {
		if(incomingVertex != null){
			if((maliciousName != null)&&(incomingVertex.type().equals(PROCESS))&&(incomingVertex.getAnnotation(NAME).equals(maliciousName))){
				incomingVertex.addAnnotation(LABEL, MALICIOUS);
			}else{
				incomingVertex.addAnnotation(LABEL, BENIGN);
			}
			putVertex(incomingVertex);
		}else{
			logger.log(Level.WARNING, "Null vertex");
		}
	}

	@Override
	public void putEdge(AbstractEdge incomingEdge) {
		if(incomingEdge != null && incomingEdge.getSourceVertex() != null && incomingEdge.getDestinationVertex() != null){
			
			try{
				if(incomingEdge.getDestinationVertex().getAnnotation(LABEL).equals(MALICIOUS)){
				
					incomingEdge.getSourceVertex().addAnnotation(LABEL, MALICIOUS);
				
				}
			}catch(Exception e){ logger.log(Level.SEVERE, "prob", incomingEdge);
				
			}
			putEdge(incomingEdge);
		}else{
			logger.log(Level.WARNING, "Invalid edge: {0}, source: {1}, destination: {2}", new Object[]{
					incomingEdge, 
					incomingEdge == null ? null : incomingEdge.getSourceVertex(),
					incomingEdge == null ? null : incomingEdge.getDestinationVertex()
			});
		}
	}
	
}
