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

import spade.client.QueryMetaData;
import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.reporter.audit.OPMConstants;
import spade.utility.CommonFunctions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SimpleForks extends AbstractTransformer
{

	public Graph putGraph(Graph graph, QueryMetaData queryMetaData)
	{
		Map<String, AbstractEdge> forkcloneEdges = new HashMap<>();
		Map<String, AbstractEdge> execveEdges = new HashMap<>();
		//added to handle multiple execves by a process
		Set<String> pendingExecveEdgeEventIds = new HashSet<>();
		for(AbstractEdge edge : graph.edgeSet())
		{
			AbstractEdge newEdge = createNewWithoutAnnotations(edge);
			if(getAnnotationSafe(newEdge, OPMConstants.EDGE_OPERATION).equals(OPMConstants.OPERATION_CLONE)
					|| getAnnotationSafe(newEdge, OPMConstants.EDGE_OPERATION).equals(OPMConstants.OPERATION_FORK))
			{
				forkcloneEdges.put(getAnnotationSafe(newEdge.getChildVertex(), OPMConstants.PROCESS_PID), newEdge);
			}
			else if(getAnnotationSafe(newEdge, OPMConstants.EDGE_OPERATION).equals(OPMConstants.OPERATION_EXECVE))
			{
				//if execve, check if there is already an edge in the map for the pid. if no then just add it
				String pid = getAnnotationSafe(newEdge.getChildVertex(), OPMConstants.PROCESS_PID);
				if(execveEdges.get(pid) == null)
				{
					execveEdges.put(pid, newEdge);
				}
				else
				{
					//if there is an edge already then compare the event ids of the older (already added) one and new one.
					// if the new event id is smaller then replace older one with the newer one and add the older one to
					// pending list, else add the newer one to the pending list.
					//the execve edge with the smallest event id by a process should be merged with fork/clone (if any)
					// of that process
					Long newEdgeEventId = CommonFunctions.parseLong(getAnnotationSafe(newEdge, OPMConstants.EDGE_EVENT_ID), null);
					Long previousEdgeEventId = CommonFunctions.parseLong(getAnnotationSafe(
							execveEdges.get(pid), OPMConstants.EDGE_EVENT_ID), null);
					if(previousEdgeEventId > newEdgeEventId)
					{
						pendingExecveEdgeEventIds.add(getAnnotationSafe(execveEdges.get(pid), 
								OPMConstants.EDGE_EVENT_ID)); //add event id of the previously added edge to pending list
						execveEdges.put(pid, newEdge); //replace the previously added with the new one
					}
					else
					{
						pendingExecveEdgeEventIds.add(getAnnotationSafe(newEdge, OPMConstants.EDGE_EVENT_ID));
					}
				}
			}
		}
		
		Graph resultGraph = new Graph();
		Set<String> allPids = new HashSet<>();
		allPids.addAll(forkcloneEdges.keySet());
		allPids.addAll(execveEdges.keySet());
		Map<String, AbstractVertex> pidToVertex = new HashMap<>();
		for(String pid : allPids)
		{
			boolean hasForkClone = forkcloneEdges.get(pid) != null;
			boolean hasExecve = execveEdges.get(pid) != null;
			AbstractEdge edge;
			if(hasForkClone && hasExecve)
			{
				edge = forkcloneEdges.get(pid);
				edge.setChildVertex(execveEdges.get(pid).getChildVertex());
				edge.addAnnotation(OPMConstants.EDGE_OPERATION, 
						OPMConstants.buildOperation(
								edge.getAnnotation(OPMConstants.EDGE_OPERATION), OPMConstants.OPERATION_EXECVE));
			}
			else if(hasForkClone && !hasExecve)
			{
				edge = forkcloneEdges.get(pid);
			}
			else if(!hasForkClone && hasExecve)
			{
				edge = execveEdges.get(pid);
				pidToVertex.put(pid, edge.getChildVertex());
				continue;
			}
			//both false
			else
			{
				continue;
			}

			pidToVertex.put(pid, edge.getChildVertex());
			resultGraph.putVertex(edge.getChildVertex());
			resultGraph.putVertex(edge.getParentVertex());
			resultGraph.putEdge(edge);
		}
		
		for(AbstractEdge edge : graph.edgeSet())
		{
			if(getAnnotationSafe(edge, OPMConstants.EDGE_OPERATION).equals(OPMConstants.OPERATION_CLONE))
			{
				continue;
			}
			if(getAnnotationSafe(edge, OPMConstants.EDGE_OPERATION).equals(OPMConstants.OPERATION_EXECVE))
			{
				String eventId = getAnnotationSafe(edge, OPMConstants.EDGE_EVENT_ID);
				//it is not a pending one. so continue otherwise add the edge.
				if(!pendingExecveEdgeEventIds.contains(eventId))
				{
					continue;
				}
			}
			AbstractEdge newEdge = createNewWithoutAnnotations(edge);
			String srcPid = getAnnotationSafe(newEdge.getChildVertex(), OPMConstants.PROCESS_PID);
			if(srcPid != null && pidToVertex.get(srcPid) != null)
			{
				newEdge.setChildVertex(pidToVertex.get(srcPid));
			}
			String dstPid = getAnnotationSafe(newEdge.getParentVertex(), OPMConstants.PROCESS_PID);
			if(dstPid != null && pidToVertex.get(dstPid) != null)
			{
				newEdge.setParentVertex(pidToVertex.get(dstPid));
			}
			if(newEdge != null && newEdge.getChildVertex() != null && newEdge.getParentVertex() != null)
			{
				resultGraph.putVertex(newEdge.getChildVertex());
				resultGraph.putVertex(newEdge.getParentVertex());
				resultGraph.putEdge(newEdge);
			}
		}
		return resultGraph;
	}
}
