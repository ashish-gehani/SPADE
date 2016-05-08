/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2016 SRI International

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

package spade.transformer;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.client.QueryParameters;
import spade.core.AbstractEdge;
import spade.core.Graph;
import spade.core.Settings;
import spade.utility.FileUtility;

public class OPM2ProvTC extends OPM2Prov{
	
	private static final Logger logger = Logger.getLogger(OPM2ProvTC.class.getName());
	
	private Map<String, String> opm2ProvTCMapping = null;
	
	public boolean initialize(String arguments){
		String filepath = Settings.getDefaultConfigFilePath(this.getClass());
		try{
			opm2ProvTCMapping = FileUtility.readOPM2ProvTCFile(filepath);
			return true;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to read the file: " + filepath, e);
			return false;
		}
	}

	public Graph putGraph(Graph graph, QueryParameters digQueryParams){
		graph = super.putGraph(graph, digQueryParams);
		graph.commitIndex();
		
		Graph resultGraph = new Graph();
		
		for(AbstractEdge edge : graph.edgeSet()){
			if(edge != null && edge.getSourceVertex() != null && edge.getDestinationVertex() != null){
				AbstractEdge newEdge = createNewWithoutAnnotations(edge);
				replaceAnnotations(newEdge.getAnnotations(), opm2ProvTCMapping);
				replaceAnnotations(newEdge.getSourceVertex().getAnnotations(), opm2ProvTCMapping);
				replaceAnnotations(newEdge.getDestinationVertex().getAnnotations(), opm2ProvTCMapping);
				resultGraph.putVertex(newEdge.getSourceVertex());
				resultGraph.putVertex(newEdge.getDestinationVertex());
				resultGraph.putEdge(newEdge);
			}
		}
		
		return resultGraph;
	}
	
	private void replaceAnnotations(Map<String, String> annotations, Map<String, String> newMapping){
		for(String annotation : annotations.keySet()){
			if(newMapping.get(annotation) != null){
				annotations.put(newMapping.get(annotation), annotations.get(annotation));
				annotations.remove(annotation);
			}
		}
	}
	
}
