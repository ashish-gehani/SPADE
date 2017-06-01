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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.utility.CommonFunctions;

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
public class MLFeatures extends AbstractFilter{

	private Logger logger = Logger.getLogger(this.getClass().getName());
	private HashMap<String,HashMap<String,Float>> features = new HashMap<>();
	private HashMap<String,String> agentsName = new HashMap<>();
	private final String USER = "User";
	private final String WCB = "WasControlledBy";
	private final String PROCESS = "Process";
	private final String PROCESS_IDENTIFIER = "pid";
	private final String USED = "Used";
	private final String COUNT_USED = "countUsed";
	private final String WGB = "WasGeneratedBy";
	private final String COUNT_WGB = "countWgb";
	private final String CURRENT_TIME_USED = "CurrentTimeUsed";
	private final String TIME = "time";
	private final String MEAN_TIME_BETWEEN_TWO_USED = "meanTimeBetweenTwoUsed";
	private final Float INITIAL_ZERO = (float) 0;
	private final String AVG_DURATION_USED = "avgDurationUsed";
	private final String AVG_DURATION_WGB = "avgDurationWgb";
	private final String DURATION = "duration";
	
	
	public boolean initialize(String arguments){

		return true;

	}

	@Override
	public void putVertex(AbstractVertex incomingVertex) {
		if(incomingVertex != null){
			if(incomingVertex.type() == PROCESS){
				
				String processPid = incomingVertex.getAnnotation(PROCESS_IDENTIFIER);
				HashMap<String,Float> initialFeatures = new HashMap<>();
				initialFeatures.put(COUNT_USED, INITIAL_ZERO);
				initialFeatures.put(COUNT_WGB, INITIAL_ZERO);
				initialFeatures.put(MEAN_TIME_BETWEEN_TWO_USED,INITIAL_ZERO);
				initialFeatures.put(AVG_DURATION_USED,INITIAL_ZERO);
				features.put(processPid,initialFeatures);
				
			}
			putInNextFilter(incomingVertex);
		}else{
			logger.log(Level.WARNING, "Null vertex");
		}
	}

	@Override
	public void putEdge(AbstractEdge incomingEdge) {
		if(incomingEdge != null && incomingEdge.getSourceVertex() != null && incomingEdge.getDestinationVertex() != null){
			if (incomingEdge.getSourceVertex().type() == PROCESS) {
				
				AbstractVertex sourceProcessVertex = incomingEdge.getSourceVertex();
				HashMap<String, Float> sourceProcess = features.get(sourceProcessVertex.getAnnotation(PROCESS_IDENTIFIER));
				
				if (incomingEdge.type() == USED){
					
					float count_used = sourceProcess.get(COUNT_USED);
					float duration = Float.parseFloat(sourceProcessVertex.getAnnotation(DURATION));
					float currentDurationMean = sourceProcess.get(AVG_DURATION_USED);
					sourceProcess.put(AVG_DURATION_USED,(currentDurationMean*count_used + duration)/(count_used+1) );
					sourceProcess.put(COUNT_USED, count_used + 1);
					
				}else if (incomingEdge.type() == WCB){
					
					agentsName.put(PROCESS_IDENTIFIER,incomingEdge.getDestinationVertex().getAnnotation(USER));
					
				}
				
			}else if (incomingEdge.getDestinationVertex().type() == PROCESS){	
				
				AbstractVertex sourceProcessVertex = incomingEdge.getSourceVertex();
				HashMap<String, Float> sourceProcess = features.get(sourceProcessVertex.getAnnotation(PROCESS_IDENTIFIER));
				
				if (incomingEdge.type() == WGB){
					
					float count_wgb = sourceProcess.get(COUNT_WGB);
					float duration = Float.parseFloat(sourceProcessVertex.getAnnotation(DURATION));
					float currentDurationMean = sourceProcess.get(AVG_DURATION_WGB);
					sourceProcess.put(AVG_DURATION_WGB,(currentDurationMean*count_wgb + duration)/(count_wgb+1) );
					sourceProcess.replace(COUNT_WGB, count_wgb + 1);
					
				}
			}
			putInNextFilter(incomingEdge);
		}else{
			logger.log(Level.WARNING, "Invalid edge: {0}, source: {1}, destination: {2}", new Object[]{
					incomingEdge,
					incomingEdge == null ? null : incomingEdge.getSourceVertex(),
					incomingEdge == null ? null : incomingEdge.getDestinationVertex()
			});
		}
	}


}
