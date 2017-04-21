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

public class SimpleForks extends AbstractTransformer {

	public Graph putGraph(Graph graph, QueryParameters digQueryParams){
		Map<String, AbstractEdge> forkcloneEdges = new HashMap<String, AbstractEdge>();
		Map<String, AbstractEdge> execveEdges = new HashMap<String, AbstractEdge>();
		for(AbstractEdge edge : graph.edgeSet()){
			AbstractEdge newEdge = createNewWithoutAnnotations(edge);
			if(getAnnotationSafe(newEdge, "operation").equals("clone")
					|| getAnnotationSafe(newEdge, "operation").equals("fork")
					|| getAnnotationSafe(newEdge, "operation").equals("vfork")){
				forkcloneEdges.put(getAnnotationSafe(newEdge.getChildVertex(), "pid"), newEdge);
			}else if(getAnnotationSafe(newEdge, "operation").equals("execve")){
				execveEdges.put(getAnnotationSafe(newEdge.getChildVertex(), "pid"), newEdge);
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
				edge.setChildVertex(execveEdges.get(pid).getChildVertex());
				edge.addAnnotation("operation", edge.getAnnotation("operation")+"-execve");
			}else if(hasForkClone && !hasExecve){
				edge = forkcloneEdges.get(pid);
			}else if(!hasForkClone && hasExecve){
				edge = execveEdges.get(pid);
				pidToVertex.put(pid, edge.getChildVertex());
				continue;
			}else{ //both false
				continue;
			}
			pidToVertex.put(pid, edge.getChildVertex());
			resultGraph.putVertex(edge.getChildVertex());
			resultGraph.putVertex(edge.getParentVertex());
			resultGraph.putEdge(edge);
		}
		
		for(AbstractEdge edge : graph.edgeSet()){
			if(getAnnotationSafe(edge, "operation").equals("execve") || getAnnotationSafe(edge, "operation").equals("clone")){
				continue;
			}
			AbstractEdge newEdge = createNewWithoutAnnotations(edge);
			String srcPid = getAnnotationSafe(newEdge.getChildVertex(), "pid");
			if(srcPid != null && pidToVertex.get(srcPid) != null){
				newEdge.setChildVertex(pidToVertex.get(srcPid));
			}
			String dstPid = getAnnotationSafe(newEdge.getParentVertex(), "pid");
			if(dstPid != null && pidToVertex.get(dstPid) != null){
				newEdge.setParentVertex(pidToVertex.get(dstPid));
			}
			if(newEdge != null && newEdge.getChildVertex() != null && newEdge.getParentVertex() != null){
				resultGraph.putVertex(newEdge.getChildVertex());
				resultGraph.putVertex(newEdge.getParentVertex());
				resultGraph.putEdge(newEdge);
			}
		}
		return resultGraph;
	}
	
}
