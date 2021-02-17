/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International

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
package spade.query.quickgrail;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import spade.core.AbstractVertex;
import spade.core.Kernel;
import spade.core.Settings;
import spade.query.quickgrail.core.GraphStats;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.QuickGrailQueryResolver.PredicateOperator;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.instruction.CreateEmptyGraph;
import spade.query.quickgrail.instruction.ExportGraph;
import spade.query.quickgrail.instruction.ExportGraph.Format;
import spade.query.quickgrail.instruction.GetLineage;
import spade.query.quickgrail.instruction.GetVertex;
import spade.query.quickgrail.instruction.StatGraph;
import spade.reporter.audit.OPMConstants;
import spade.utility.HelperFunctions;

public class RemoteResolver{

	// Schema dependent
	
	public static String getAnnotationRemoteAddress(){ return OPMConstants.ARTIFACT_REMOTE_ADDRESS; }
	public static String getAnnotationLocalAddress(){ return OPMConstants.ARTIFACT_LOCAL_ADDRESS; }
	public static String getAnnotationLocalPort(){ return OPMConstants.ARTIFACT_LOCAL_PORT; }
	public static String getAnnotationRemotePort(){ return OPMConstants.ARTIFACT_REMOTE_PORT; }
	
	public static String getRemoteAddress(AbstractVertex networkVertex){
		return networkVertex.getAnnotation(getAnnotationRemoteAddress());
	}
	
	public static String getLocalAddress(AbstractVertex networkVertex){
		return networkVertex.getAnnotation(getAnnotationLocalAddress());
	}
	
	public static String getRemotePort(AbstractVertex networkVertex){
		return networkVertex.getAnnotation(getAnnotationRemotePort());
	}
	
	public static String getLocalPort(AbstractVertex networkVertex){
		return networkVertex.getAnnotation(getAnnotationLocalPort());
	}
	
	private static boolean isPortUsable(String port){
		if(HelperFunctions.isNullOrEmpty(port)){
			return false;
		}else{
			port = port.trim();
			int portInt = HelperFunctions.parseInt(port, -1);
			if(portInt > -1){
				return true;
			}else{
				return false;
			}
		}
	}
	
	private static boolean isIPv4Address(String remoteAddress){
		String tokens[] = remoteAddress.split("\\.");
		if(tokens.length == 4){
			for(String token : tokens){
				int i = HelperFunctions.parseInt(token, -1);
				if(i < 0 || i > 255){
					return false;
				}
			}
			return true;
		}
		return false;
	}
	
	private static boolean isIPv6Address(String remoteAddress){
		return false;
	}
	
	// Incomplete
	public static boolean isRemoteAddressRemoteInNetworkVertex(AbstractVertex vertex){
		return isRemoteAddressRemote(getRemoteAddress(vertex));
	}
	
	// Incomplete
	public static boolean isRemoteAddressRemote(String remoteAddress){
		// ipv4 and ipv6
		// only ipv4 for now
		if(HelperFunctions.isNullOrEmpty(remoteAddress)){
			return false;
		}else{
			remoteAddress = remoteAddress.trim();
			if(isIPv4Address(remoteAddress)){
				if(remoteAddress.equals("127.0.0.1")){
					return false;
				}else{
					// missing other checks
					return true;
				}
			}else if(isIPv6Address(remoteAddress)){
				return false;
			}else{
				return false;
			}
		}
	}
	
	// Incomplete
	private static boolean isLocalAddressUsableForRemoteQuery(String localAddress){
		// ipv4 and ipv6
		// only ipv4 for now
		if(HelperFunctions.isNullOrEmpty(localAddress)){
			return false;
		}else{
			localAddress = localAddress.trim();
			if(isIPv4Address(localAddress)){
				if(localAddress.equals("127.0.0.1") || 
						localAddress.equals("0.0.0.0")){
					return false;
				}else{
					// missing other checks
					return true;
				}
			}else if(isIPv6Address(localAddress)){
				return false;
			}else{
				return false;
			}
		}
	}
	
	private static GetVertex getVertexForNetworkArtifactsInstruction(Graph targetGraph, Graph subjectGraph){
		return new GetVertex(targetGraph, subjectGraph, 
				OPMConstants.ARTIFACT_SUBTYPE, PredicateOperator.EQUAL, OPMConstants.SUBTYPE_NETWORK_SOCKET);
	}
	
	//////////
	
	private static Map<String, Set<AbstractVertex>> getRemoteIpToCompleteRemoteNetworkVertex(Set<AbstractVertex> networkVertices){
		Map<String, Set<AbstractVertex>> remoteIpToNetworkVertex = new HashMap<String, Set<AbstractVertex>>();
		if(networkVertices != null){
			for(AbstractVertex networkVertex : networkVertices){
				String remoteAddress = getRemoteAddress(networkVertex);
				if(isRemoteAddressRemote(remoteAddress)){
					String localAddress = getLocalAddress(networkVertex);
					if(isLocalAddressUsableForRemoteQuery(localAddress)){
						String remotePort = getRemotePort(networkVertex);
						if(isPortUsable(remotePort)){
							String localPort = getLocalPort(networkVertex);
							if(isPortUsable(localPort)){
								// complete network artifact
								Set<AbstractVertex> set = remoteIpToNetworkVertex.get(remoteAddress);
								if(set == null){
									set = new HashSet<AbstractVertex>();
									remoteIpToNetworkVertex.put(remoteAddress, set);
								}
								set.add(networkVertex);
							}else{
								// local port not usable.
							}
						}else{
							// remote port not usable.
						}
					}else{
						// local address is not usable.
					}
				}else{
					// is not remote network so don't need to do anything
				}
			}
		}
		return remoteIpToNetworkVertex;
	}
	
	//////////
	
	private static GraphStats getGraphStats(QueryInstructionExecutor instructionExecutor, Graph graph){
		return instructionExecutor.statGraph(new StatGraph(graph));
	}
	
	private static spade.core.Graph exportGraph(QuickGrailExecutor executor, Graph graph){
		return executor.exportGraph(new ExportGraph(graph, Format.kDot, true, null));
	}
	
	private static Graph allocateNewGraph(QueryInstructionExecutor instructionExecutor){
		Graph newGraph = instructionExecutor.getQueryEnvironment().allocateGraph();
		instructionExecutor.createEmptyGraph(new CreateEmptyGraph(newGraph));
		return newGraph;
	}
	
	private static Map<AbstractVertex, Integer> getMinimumDepthOfNetworkVerticesMap(
			GetLineage getLineage){
		// Shortest path from the set of source to each network vertex?
		return new HashMap<AbstractVertex, Integer>(); 
	}
	
	// post instruction execution. Only called if resolve remotely is true in get lineage
/*	public static void resolveRemotely(
			QuickGrailExecutor queryExecutor,
			QueryInstructionExecutor instructionExecutor,
			QueryEnvironment queryEnvironment,
			GetLineage getLineage, 
			SPADEQuery spadeQuery,
			QuickGrailInstruction queryInstruction) throws Exception{		
		// Basic checks to make it compatible with old code
		GraphStats startGraphStats = getGraphStats(instructionExecutor, getLineage.startGraph);
		if(startGraphStats.vertices > 1){
			throw new RuntimeException("Remote resolution of lineage of multiple vertices not supported yet");
		}
		
		Graph networkVerticesVariable = allocateNewGraph(instructionExecutor);
		
		instructionExecutor.getVertex(getVertexForNetworkArtifactsInstruction(networkVerticesVariable, getLineage.targetGraph));
		
		GraphStats networkVerticesStats = getGraphStats(instructionExecutor, networkVerticesVariable);
		
		if(networkVerticesStats.vertices == 0){
			// nothing to do
			spade.core.Graph graphToExport = exportGraph(queryExecutor, getLineage.targetGraph);
			queryInstruction.instructionSucceeded(graphToExport);
			return;
		}else{
			spade.core.Graph networkVerticesSpadeGraph = exportGraph(queryExecutor, networkVerticesVariable);
			Set<AbstractVertex> networkVertices = networkVerticesSpadeGraph.vertexSet();
			
			Map<String, Set<AbstractVertex>> remoteIpToNetworkVertex = getRemoteIpToCompleteRemoteNetworkVertex(networkVertices);
			if(remoteIpToNetworkVertex.size() == 0){
				throw new RuntimeException("No complete network artifact found in start graph");
			}else{
				Map<AbstractVertex, Integer> localNetworkVerticesToLocalMinDepth = getMinimumDepthOfNetworkVerticesMap(getLineage);
				
				Set<spade.core.Graph> remoteGraphs = new HashSet<spade.core.Graph>();
				
				for(Map.Entry<String, Set<AbstractVertex>> entry : remoteIpToNetworkVertex.entrySet()){
					String remoteAddress = entry.getKey();
					Set<AbstractVertex> vertices = entry.getValue();
					Map<AbstractVertex, Integer> localMinDepths = new HashMap<AbstractVertex, Integer>();
					for(AbstractVertex vertex : vertices){
						Integer localMinDepth = localNetworkVerticesToLocalMinDepth.get(vertex);
						if(localMinDepth == null){
							// missing. what to do? use max i.e. 0?
						}else{
							localMinDepths.put(vertex, localMinDepth);
						}
					}
					if(localMinDepths.isEmpty()){
						// no relevant depth found so no vertex usable unless fallback depth assigned
					}else{
						// make it asynchronous
						getRemoteLineage(remoteAddress, localMinDepths, getLineage.depth, getLineage.direction);
					}
				}
				
				// wait for all asynchronous calls to finish
			}
		}
	}*/
	
	//////
	// remote
	
	private static spade.core.Graph getRemoteLineage(String remoteAddress, 
			Map<AbstractVertex, Integer> localMinDepths,
			int maxDepth, GetLineage.Direction direction) throws Exception{
		
		Map<String, String> getLineageSymbolToQuery = new HashMap<String, String>();
		Map<String, String> visualizeSymbolToQuery = new HashMap<String, String>();
		Map<String, String> eraseSymbolToQuery = new HashMap<String, String>();
		
		for(Map.Entry<AbstractVertex, Integer> entry : localMinDepths.entrySet()){
			AbstractVertex vertex = entry.getKey();
			Integer localMinDepth = entry.getValue();
			
			int remoteDepth = maxDepth - localMinDepth;
			
			String remoteGetLineageGraphSymbol = generateNewSymbol(remoteAddress);
			String remoteGetLineageQuery = 
					buildRemoteGetLineageQuery(remoteGetLineageGraphSymbol, remoteDepth, direction, vertex);
			String remoteVisualizeQuery = buildRemoteVisualizeQuery(remoteGetLineageGraphSymbol);
			String remoteEraseQuery = buildRemoteEraseQuery(remoteGetLineageGraphSymbol);
			
			getLineageSymbolToQuery.put(remoteGetLineageGraphSymbol, remoteGetLineageQuery);
			visualizeSymbolToQuery.put(remoteGetLineageGraphSymbol, remoteVisualizeQuery);
			eraseSymbolToQuery.put(remoteGetLineageGraphSymbol, remoteEraseQuery);
		}
		
		int remoteQueryPort = getRemoteQueryPort(remoteAddress);
		Socket socket = Kernel.sslSocketFactory.createSocket(remoteAddress, remoteQueryPort);
		// Timeout in case of hung connection?
		ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
		ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
		
		for(String getLineageQuery : getLineageSymbolToQuery.values()){
			
		}
		
		inputStream.close();
		outputStream.close();
		
		return null;
	}
	
	private static int getRemoteQueryPort(String remoteAddress){
		return Settings.getCommandLineQueryPort();
	}

	private static String generateNewSymbol(String remoteAddress){
		return null;
	}
	
	private static String buildRemoteVisualizeQuery(String resultSymbol){
		return "visualize " + resultSymbol;
	}
	
	private static String buildRemoteEraseQuery(String resultSymbol){
		return "erase " + resultSymbol;
	}
	
	private static String buildRemoteEraseQuery(Set<String> resultSymbols){
		String query = "erase";
		for(String resultSymbol : resultSymbols){
			query += " " + resultSymbol;
		}
		return query;
	}
	
	private static String buildRemoteGetLineageQuery(String resultSymbol, int depth, GetLineage.Direction direction,
			AbstractVertex localNetworkVertex){
		String query = "";
		query += resultSymbol + " = $base.getLineage($base.getVertex(";
		query += formatQueryName(getAnnotationLocalAddress()) + "=" + formatQueryValue(getRemoteAddress(localNetworkVertex));
		query += " and ";
		query += formatQueryName(getAnnotationLocalPort()) + "=" + formatQueryValue(getRemotePort(localNetworkVertex));
		query += " and ";
		query += formatQueryName(getAnnotationRemoteAddress()) + "=" + formatQueryValue(getLocalAddress(localNetworkVertex));
		query += " and ";
		query += formatQueryName(getAnnotationRemotePort()) + "=" + formatQueryValue(getLocalPort(localNetworkVertex));
		query += ")"; // finish of get vertex
		query += ", " + depth;
		query += ", " + formatDirection(direction);
		query += ")"; // finish of get lineage 
		return query;
	}
	
	private static String formatQueryName(String name){
		return '"' + name + '"';
	}
	
	private static String formatQueryValue(String name){
		return '"' + name + '"';
	}
	
	private static String formatDirection(GetLineage.Direction direction){
		switch(direction){
			case kAncestor: return "'a'";
			case kDescendant: return "'d'";
			case kBoth: return "'b'";
			default: throw new RuntimeException("Unexpected direction: " + direction);
		}
	}

	public static AbstractVertex findInverseNetworkVertex(Set<AbstractVertex> vertices, AbstractVertex networkVertex){
		String localAddress = getLocalAddress(networkVertex);
		String localPort = getLocalPort(networkVertex);
		String remoteAddress = getRemoteAddress(networkVertex);
		String remotePort = getRemotePort(networkVertex);
		for(AbstractVertex candidate : vertices){
			if(HelperFunctions.objectsEqual(localAddress, getRemoteAddress(candidate))
				&& HelperFunctions.objectsEqual(localPort, getRemotePort(candidate))
				&& HelperFunctions.objectsEqual(remoteAddress, getLocalAddress(candidate))
				&& HelperFunctions.objectsEqual(remotePort, getLocalPort(candidate))
				){
				return candidate;
			}
/*			if(localAddress.equals(getRemoteAddress(candidate))
					&& localPort.equals(getRemotePort(candidate))
					&& remoteAddress.equals(getLocalAddress(candidate))
					&& remotePort.equals(getLocalPort(candidate))){
				return candidate;
			}
*/
		}
		return null;
	}
	
}
