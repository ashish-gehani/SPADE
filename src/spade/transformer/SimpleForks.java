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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import spade.client.QueryParameters;
import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.utility.CommonFunctions;

public class SimpleForks extends AbstractTransformer {

	public Graph putGraph(Graph graph, QueryParameters digQueryParams){
		Map<String, AbstractEdge> forkcloneEdges = new HashMap<String, AbstractEdge>();
		Map<String, AbstractEdge> execveEdges = new HashMap<String, AbstractEdge>();
		Set<String> pendingExecveEdgeEventIds = new HashSet<String>(); //added to handle multiple execves by a process
		for(AbstractEdge edge : graph.edgeSet()){
			AbstractEdge newEdge = createNewWithoutAnnotations(edge);
			if(getAnnotationSafe(newEdge, "operation").equals("clone")
					|| getAnnotationSafe(newEdge, "operation").equals("fork")
					|| getAnnotationSafe(newEdge, "operation").equals("vfork")){
				forkcloneEdges.put(getAnnotationSafe(newEdge.getSourceVertex(), "pid"), newEdge);
			}else if(getAnnotationSafe(newEdge, "operation").equals("execve")){
				//if execve, check if there is already an edge in the map for the pid. if no then just add it
				String pid = getAnnotationSafe(newEdge.getSourceVertex(), "pid");
				if(execveEdges.get(pid) == null){
					execveEdges.put(pid, newEdge);
				}else{
					//if there is an edge already then compare the event ids of the older (already added) one and new one. if the new event id is smaller then replace older one with the newer one and add the older one to pending list
					//else add the newer one to the pending list.
					//the execve edge with the smallest event id by a process should be merged with fork/clone (if any) of that process
					Long newEdgeEventId = CommonFunctions.parseLong(getAnnotationSafe(newEdge, "event id"), null);
					Long previousEdgeEventId = CommonFunctions.parseLong(getAnnotationSafe(execveEdges.get(pid), "event id"), null);
					if(previousEdgeEventId > newEdgeEventId){
						pendingExecveEdgeEventIds.add(getAnnotationSafe(execveEdges.get(pid), "event id")); //add event id of the previously added edge to pending list
						execveEdges.put(pid, newEdge); //replace the previously added with the new one
					}else{
						pendingExecveEdgeEventIds.add(getAnnotationSafe(newEdge, "event id"));
					}
				}
			}
		}
		
		Graph resultGraph = new Graph();
		Set<String> allPids = new HashSet<String>();
		allPids.addAll(forkcloneEdges.keySet());
		allPids.addAll(execveEdges.keySet());
		Map<String, AbstractVertex> pidToVertex = new HashMap<String, AbstractVertex>();
		for(String pid : allPids){
			boolean hasForkClone = forkcloneEdges.get(pid) != null;
			boolean hasExecve = execveEdges.get(pid) != null;
			AbstractEdge edge = null;
			if(hasForkClone && hasExecve){
				edge = forkcloneEdges.get(pid);
				edge.setSourceVertex(execveEdges.get(pid).getSourceVertex());
				edge.addAnnotation("operation", edge.getAnnotation("operation")+"-execve");
			}else if(hasForkClone && !hasExecve){
				edge = forkcloneEdges.get(pid);
			}else if(!hasForkClone && hasExecve){
				edge = execveEdges.get(pid);
				pidToVertex.put(pid, edge.getSourceVertex());
				continue;
			}else{ //both false
				continue;
			}
			pidToVertex.put(pid, edge.getSourceVertex());
			resultGraph.putVertex(edge.getSourceVertex());
			resultGraph.putVertex(edge.getDestinationVertex());
			resultGraph.putEdge(edge);
		}
		
		for(AbstractEdge edge : graph.edgeSet()){
			if(getAnnotationSafe(edge, "operation").equals("clone")){
				continue;
			}
			if(getAnnotationSafe(edge, "operation").equals("execve")){
				String eventId = getAnnotationSafe(edge, "event id");
				if(!pendingExecveEdgeEventIds.contains(eventId)){ //it is not a pending one. so continue otherwise add the edge.
					continue;
				}
			}
			AbstractEdge newEdge = createNewWithoutAnnotations(edge);
			String srcPid = getAnnotationSafe(newEdge.getSourceVertex(), "pid");
			if(srcPid != null && pidToVertex.get(srcPid) != null){
				newEdge.setSourceVertex(pidToVertex.get(srcPid));
			}
			String dstPid = getAnnotationSafe(newEdge.getDestinationVertex(), "pid");
			if(dstPid != null && pidToVertex.get(dstPid) != null){
				newEdge.setDestinationVertex(pidToVertex.get(dstPid));
			}
			if(newEdge != null && newEdge.getSourceVertex() != null && newEdge.getDestinationVertex() != null){
				resultGraph.putVertex(newEdge.getSourceVertex());
				resultGraph.putVertex(newEdge.getDestinationVertex());
				resultGraph.putEdge(newEdge);
			}
		}
		return resultGraph;
	}
	
	
	
}
