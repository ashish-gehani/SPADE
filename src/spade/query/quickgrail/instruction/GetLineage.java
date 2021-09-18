/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2018 SRI International

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
package spade.query.quickgrail.instruction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Kernel;
import spade.core.Settings;
import spade.query.quickgrail.core.GraphStatistic;
import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.QuickGrailQueryResolver.PredicateOperator;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.utility.TreeStringSerializable;
import spade.reporter.audit.OPMConstants;
import spade.transformer.ABE;
import spade.utility.HelperFunctions;
import spade.utility.RemoteSPADEQueryConnection;

/**
 * Get the lineage of a set of vertices in a graph.
 */
public class GetLineage extends Instruction<String>{

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	public static enum Direction{
		kAncestor, kDescendant, kBoth
	}

	// Output graph.
	public final Graph targetGraph;
	// Input graph.
	public final Graph subjectGraph;
	// Set of starting vertices.
	public final Graph startGraph;
	// Max depth.
	public final int depth;
	// Direction (ancestors / descendants, or both).
	public final Direction direction;

	public final boolean onlyLocal;

	public GetLineage(Graph targetGraph, Graph subjectGraph, Graph startGraph, int depth, Direction direction,
			boolean onlyLocal){
		this.targetGraph = targetGraph;
		this.subjectGraph = subjectGraph;
		this.startGraph = startGraph;
		this.depth = depth;
		this.direction = direction;
		this.onlyLocal = onlyLocal;
	}

	@Override
	public String getLabel(){
		return "GetLineage";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("targetGraph");
		inline_field_values.add(targetGraph.name);
		inline_field_names.add("subjectGraph");
		inline_field_values.add(subjectGraph.name);
		inline_field_names.add("startGraph");
		inline_field_values.add(startGraph.name);
		inline_field_names.add("depth");
		inline_field_values.add(String.valueOf(depth));
		inline_field_names.add("direction");
		inline_field_values.add(direction.name().substring(1));
		inline_field_names.add("local");
		inline_field_values.add(String.valueOf(onlyLocal));
	}

	@Override
	public void updateTransformerExecutionContext(final QueryInstructionExecutor executor,
			final AbstractTransformer.ExecutionContext context){
		final spade.core.Graph sourceGraph = executor.exportGraph(startGraph, true);
		context.setSourceGraph(sourceGraph);
		context.setMaxDepth(depth);
		context.setDirection(direction);
	}

	@Override
	public final String execute(final QueryInstructionExecutor executor){
		final spade.core.Graph sourceGraph;

		if(executor.getGraphCount(startGraph).getVertices() > 0){
			sourceGraph = executor.exportGraph(startGraph, true);
		}else{
			sourceGraph = new spade.core.Graph();
		}

		if(sourceGraph.vertexSet().size() == 0
				|| executor.getGraphCount(subjectGraph).getEdges() == 0){
			// Nothing to start from since no vertices OR no where to go in the subject
			// since no edges
			return null;
		}

		final List<Direction> directions = new ArrayList<Direction>();
		if(Direction.kAncestor.equals(direction) || Direction.kDescendant.equals(direction)){
			directions.add(direction);
		}else if(Direction.kBoth.equals(direction)){
			directions.add(Direction.kAncestor);
			directions.add(Direction.kDescendant);
		}else{
			throw new RuntimeException(
					"Unexpected direction: '" + direction + "'. Expected: Ancestor, Descendant or Both");
		}

		if(onlyLocal){
			executor.getLineage(targetGraph, subjectGraph, startGraph, depth, direction, onlyLocal);
			return null;
		}

		final Map<Direction, Map<AbstractVertex, Integer>> directionToNetworkToMinimumDepth = new HashMap<Direction, Map<AbstractVertex, Integer>>();

		final Graph resultGraph = executor.createNewGraph();

		for(final Direction direction : directions){
			final Graph directionGraph = executor.createNewGraph();

			final Graph currentLevelVertices = executor.createNewGraph();
			executor.distinctifyGraph(currentLevelVertices, startGraph);

			int currentDepth = 1;

			final Graph nextLevelVertices = executor.createNewGraph();
			final Graph adjacentGraph = executor.createNewGraph();

			while(executor.getGraphCount(currentLevelVertices).getVertices() > 0){
				if(currentDepth > depth){
					break;
				}else{
					executor.clearGraph(adjacentGraph);
					executor.getAdjacentVertex(adjacentGraph, subjectGraph, currentLevelVertices, direction);
					if(executor.getGraphCount(adjacentGraph).getVertices() < 1){
						break;
					}else{
						// Get only new vertices
						// Get all the vertices in the adjacent graph
						// Remove all the current level vertices from it and that means we have the only
						// new vertices (i.e. next level)
						// Remove all the vertices which are already in the the result graph to avoid
						// doing duplicate work
						executor.clearGraph(nextLevelVertices);
						executor.unionGraph(nextLevelVertices, adjacentGraph);
						executor.subtractGraph(nextLevelVertices, nextLevelVertices, currentLevelVertices, null);
						executor.subtractGraph(nextLevelVertices, nextLevelVertices, directionGraph, null);

						executor.clearGraph(currentLevelVertices);
						executor.unionGraph(currentLevelVertices, nextLevelVertices);

						// Update the result graph after so that we don't remove all the relevant
						// vertices
						executor.unionGraph(directionGraph, adjacentGraph);

						final Set<AbstractVertex> networkVertices = getNetworkVertices(executor, nextLevelVertices);
						if(!networkVertices.isEmpty()){
							for(AbstractVertex networkVertex : networkVertices){
								if(OPMConstants.isCompleteNetworkArtifact(networkVertex) // this is the 'abcdef' comment
										&& isRemoteAddressRemoteInNetworkVertex(networkVertex)
										&& !sourceGraph.vertexSet().contains(networkVertex)
										&& Kernel.getHostName().equals(networkVertex.getAnnotation("host"))){ // only
																												// need
																												// to
																												// resolve
																												// local
																												// artifacts
									if(directionToNetworkToMinimumDepth.get(direction) == null){
										directionToNetworkToMinimumDepth.put(direction,
												new HashMap<AbstractVertex, Integer>());
									}
									if(directionToNetworkToMinimumDepth.get(direction).get(networkVertex) == null){
										directionToNetworkToMinimumDepth.get(direction).put(networkVertex,
												currentDepth);
									}
								}
							}
						}

						currentDepth++;
					}
				}
			}
			executor.unionGraph(resultGraph, directionGraph);
		}

		////////////////////////////////////////////////////
		executor.distinctifyGraph(targetGraph, resultGraph);

		// LOCAL query done by now
		final int clientPort = Settings.getCommandLineQueryPort();

		final ABE decrypter = new ABE();
		final boolean canDecrypt = decrypter.initialize(null);
		if(!canDecrypt){
			logger.log(Level.SEVERE, "Failed to initialize decryption module. All encrypted graphs will be discarded");
		}

		for(final Map.Entry<Direction, Map<AbstractVertex, Integer>> directionToNetworkToMinimumDepthEntry : directionToNetworkToMinimumDepth
				.entrySet()){
			final Direction direction = directionToNetworkToMinimumDepthEntry.getKey();
			final Map<AbstractVertex, Integer> networkToMinimumDepth = directionToNetworkToMinimumDepthEntry.getValue();
			for(final Map.Entry<AbstractVertex, Integer> networkToMinimumDepthEntry : networkToMinimumDepth.entrySet()){
				final AbstractVertex localNetworkVertex = networkToMinimumDepthEntry.getKey();
				final Integer localDepth = networkToMinimumDepthEntry.getValue();
				final Integer remoteDepth = depth - localDepth;
				final String remoteAddress = getRemoteAddress(localNetworkVertex);
				if(remoteDepth > 0){
					try(RemoteSPADEQueryConnection connection = new RemoteSPADEQueryConnection(Kernel.getHostName(),
							remoteAddress, clientPort)){
						connection.connect(Kernel.getClientSocketFactory(), 5 * 1000);
						final String remoteVertexPredicate = buildRemoteGetVertexPredicate(localNetworkVertex);
						final String remoteVerticesSymbol = connection.getBaseVertices(remoteVertexPredicate);
						final GraphStatistic.Count remoteVerticesStats = connection.getGraphCount(remoteVerticesSymbol);
						if(remoteVerticesStats.getVertices() > 0){
							final String remoteLineageSymbol = connection.getBaseLineage(remoteVerticesSymbol,
									remoteDepth, direction);
							final GraphStatistic.Count remoteLineageStats = connection
									.getGraphCount(remoteLineageSymbol);
							if(!remoteLineageStats.isEmpty()){
								spade.core.Graph remoteVerticesGraph = connection.exportGraph(remoteVerticesSymbol);
								spade.core.Graph remoteLineageGraph = connection.exportGraph(remoteLineageSymbol);
								String remoteHostNameInGraph = remoteLineageGraph.getHostName();
								// verification - done in export graph. if not verifiable then discarded
								if(remoteVerticesGraph.getClass().equals(spade.utility.ABEGraph.class)){
									if(!canDecrypt){
										throw new RuntimeException(
												"Remote vertices graph for get lineage discarded. Invalid decryption module");
									}
									remoteVerticesGraph = decrypter
											.decryptGraph((spade.utility.ABEGraph)remoteVerticesGraph);
									if(remoteVerticesGraph == null){
										throw new RuntimeException(
												"Failed to decrypt remote vertices graph for get lineage");
									}
									remoteVerticesGraph.setHostName(remoteHostNameInGraph);
								}
								if(remoteLineageGraph.getClass().equals(spade.utility.ABEGraph.class)){
									if(!canDecrypt){
										throw new RuntimeException(
												"Remote get lineage graph for get lineage discarded. Invalid decryption module");
									}
									remoteLineageGraph = decrypter
											.decryptGraph((spade.utility.ABEGraph)remoteLineageGraph);
									if(remoteLineageGraph == null){
										throw new RuntimeException(
												"Failed to decrypt remote lineage graph for get lineage");
									}
									remoteLineageGraph.setHostName(remoteHostNameInGraph);
								}
								// decryption - done above
								// discrepancy detection, and caching goes here.
								if(executor.getDiscrepancyDetector().doDiscrepancyDetection(remoteLineageGraph,
										new HashSet<AbstractVertex>(remoteVerticesGraph.vertexSet()), remoteDepth,
										direction, remoteHostNameInGraph)){
									spade.core.Graph patchedGraph = patchRemoteLineageGraph(localNetworkVertex,
											remoteVerticesGraph, remoteLineageGraph);
									executor.putGraph(TransformGraph.putGraphBatchSize, targetGraph, patchedGraph);
								}else{
									throw new RuntimeException(
											"Discrepancies found in result graph. Result discarded.");
								}
							}
						}
					}catch(Throwable t){
						logger.log(Level.SEVERE,
								"Failed to resolve remote get lineage for host: '" + remoteAddress + "'", t);
					}
				}
			}
		}
		return null;
	}

	private final Set<AbstractVertex> getNetworkVertices(final QueryInstructionExecutor executor, final Graph graph){
		final Graph tempGraph = executor.createNewGraph();
		executor.getVertex(tempGraph, graph, OPMConstants.ARTIFACT_SUBTYPE, PredicateOperator.EQUAL,
				OPMConstants.SUBTYPE_NETWORK_SOCKET, true);
		final spade.core.Graph networkVerticesGraph = executor.exportGraph(tempGraph, true);
		final Set<AbstractVertex> networkVertices = new HashSet<AbstractVertex>();
		for(final AbstractVertex networkVertex : networkVerticesGraph.vertexSet()){
			networkVertices.add(networkVertex);
		}
		return networkVertices;
	}

	private spade.core.Graph patchRemoteLineageGraph(final AbstractVertex localVertex,
			final spade.core.Graph remoteVerticesGraph, final spade.core.Graph remoteLineageGraph){
		final Set<AbstractVertex> commonRemoteVertices = new HashSet<AbstractVertex>();

		commonRemoteVertices.addAll(remoteVerticesGraph.vertexSet());
		commonRemoteVertices.retainAll(remoteLineageGraph.vertexSet());

		final Set<AbstractEdge> newEdges = new HashSet<AbstractEdge>();
		for(AbstractVertex remoteVertex : commonRemoteVertices){
			AbstractEdge e0 = new Edge(localVertex, remoteVertex);
			AbstractEdge e1 = new Edge(remoteVertex, localVertex);
			e0.addAnnotation(OPMConstants.TYPE, OPMConstants.WAS_DERIVED_FROM);
			e1.addAnnotation(OPMConstants.TYPE, OPMConstants.WAS_DERIVED_FROM);
			newEdges.add(e0);
			newEdges.add(e1);
		}

		final spade.core.Graph patchedGraph = new spade.core.Graph();
		patchedGraph.vertexSet().addAll(remoteVerticesGraph.vertexSet());
		patchedGraph.vertexSet().addAll(remoteLineageGraph.vertexSet());
		patchedGraph.edgeSet().addAll(remoteLineageGraph.edgeSet());
		patchedGraph.edgeSet().addAll(newEdges);
		return patchedGraph;
	}

	private String buildRemoteGetVertexPredicate(final AbstractVertex localNetworkVertex){
		String predicate = "";
		predicate += formatQueryName(getAnnotationLocalAddress()) + "="
				+ formatQueryValue(getRemoteAddress(localNetworkVertex));
		predicate += " and ";
		predicate += formatQueryName(getAnnotationLocalPort()) + "="
				+ formatQueryValue(getRemotePort(localNetworkVertex));
		predicate += " and ";
		predicate += formatQueryName(getAnnotationRemoteAddress()) + "="
				+ formatQueryValue(getLocalAddress(localNetworkVertex));
		predicate += " and ";
		predicate += formatQueryName(getAnnotationRemotePort()) + "="
				+ formatQueryValue(getLocalPort(localNetworkVertex));
		return predicate;
	}

	private String formatQueryName(String name){
		return '"' + name + '"';
	}

	private String formatQueryValue(String name){
		return "'" + name + "'";
	}

	private String getAnnotationRemoteAddress(){
		return OPMConstants.ARTIFACT_REMOTE_ADDRESS;
	}

	private String getAnnotationLocalAddress(){
		return OPMConstants.ARTIFACT_LOCAL_ADDRESS;
	}

	private String getAnnotationLocalPort(){
		return OPMConstants.ARTIFACT_LOCAL_PORT;
	}

	private String getAnnotationRemotePort(){
		return OPMConstants.ARTIFACT_REMOTE_PORT;
	}

	private String getRemoteAddress(AbstractVertex networkVertex){
		return networkVertex.getAnnotation(getAnnotationRemoteAddress());
	}

	private String getLocalAddress(AbstractVertex networkVertex){
		return networkVertex.getAnnotation(getAnnotationLocalAddress());
	}

	private String getRemotePort(AbstractVertex networkVertex){
		return networkVertex.getAnnotation(getAnnotationRemotePort());
	}

	private String getLocalPort(AbstractVertex networkVertex){
		return networkVertex.getAnnotation(getAnnotationLocalPort());
	}

	private boolean isRemoteAddressRemoteInNetworkVertex(AbstractVertex vertex){
		return isRemoteAddressRemote(getRemoteAddress(vertex));
	}

	private boolean isRemoteAddressRemote(String remoteAddress){
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

	private boolean isIPv4Address(String remoteAddress){
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

	private boolean isIPv6Address(String remoteAddress){
		return false;
	}
}
