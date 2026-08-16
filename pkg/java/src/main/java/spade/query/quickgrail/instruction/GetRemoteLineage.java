/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2021 SRI International

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
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractVertex;
import spade.core.Kernel;
import spade.core.Settings;
import spade.query.execution.Context;
import spade.query.quickgrail.core.GraphStatistic;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.QuickGrailQueryResolver.PredicateOperator;
import spade.query.quickgrail.entities.Graph;
import spade.reporter.audit.OPMConstants;
import spade.utility.DiscrepancyDetector;
import spade.utility.HelperFunctions;
import spade.utility.RemoteSPADEQueryConnection;

public class GetRemoteLineage extends GetLineage{

	private static final Logger logger = Logger.getLogger(GetRemoteLineage.class.getName());

	private static final Object lock = new Object();
	private static DiscrepancyDetector discrepancyDetector = null;

	public GetRemoteLineage(Graph targetGraph, Graph subjectGraph, Graph startGraph, int depth, Direction direction){
		super(targetGraph, subjectGraph, startGraph, depth, direction);
	}

	@Override
	public String getLabel(){
		return "GetRemoteLineage";
	}

	@Override
	public final String exec(final Context ctx) {
		final QueryInstructionExecutor executor = ctx.getExecutor();
		synchronized(lock){
			if(discrepancyDetector == null){
				try{
					discrepancyDetector = new DiscrepancyDetector();
				}catch(Exception e){
					throw new RuntimeException("Failed to initialize discrepancy detector", e);
				}
			}
		}

		if(Direction.kAncestor.equals(direction) || Direction.kDescendant.equals(direction)){
			executeOneDirection(executor, direction);
		}else if(Direction.kBoth.equals(direction)){
			executeOneDirection(executor, Direction.kAncestor);
			executeOneDirection(executor, Direction.kDescendant);
		}else{
			throw new RuntimeException(
					"Unexpected direction: '" + direction + "'. Expected: Ancestor, Descendant or Both");
		}
		return null;
	}

	private final void executeOneDirection(final QueryInstructionExecutor executor, final Direction direction){
		Graph networkVerticesGraph = executor.createNewGraph();
		executor.getVertex(networkVerticesGraph, targetGraph, OPMConstants.ARTIFACT_SUBTYPE, PredicateOperator.EQUAL,
				OPMConstants.SUBTYPE_NETWORK_SOCKET, true);
		GraphStatistic.Count networkVerticesCount = executor.getGraphCount(networkVerticesGraph);
		while(networkVerticesCount.getVertices() > 0){
			// Get one network vertex
			final Graph oneNetworkVertexGraph = executor.createNewGraph();
			executor.limitGraph(oneNetworkVertexGraph, networkVerticesGraph, 1);

			final Graph tmpNetworkVerticesGraph = executor.createNewGraph();
			// Remove the found network vertices from the working set (i.e.
			// networkVerticesGraph)
			executor.subtractGraph(tmpNetworkVerticesGraph, networkVerticesGraph, oneNetworkVertexGraph,
					Graph.Component.kVertex);
			executor.clearGraph(networkVerticesGraph);
			networkVerticesGraph = tmpNetworkVerticesGraph;
			// Update the graph count variable
			networkVerticesCount = executor.getGraphCount(networkVerticesGraph);

			final ArrayList<Integer> oneNetworkVertexDepths;
			if(direction == Direction.kAncestor){
				oneNetworkVertexDepths = executor.getPathLengths(subjectGraph, startGraph, oneNetworkVertexGraph,
						depth);
			}else{ // descendants
				oneNetworkVertexDepths = executor.getPathLengths(subjectGraph, oneNetworkVertexGraph, startGraph,
						depth);
			}
			final Integer oneNetworkVertexMinDepth = HelperFunctions.min(oneNetworkVertexDepths);
			final spade.core.Graph oneNetworkVertexGraphMaterialized = executor.exportGraph(oneNetworkVertexGraph,
					true);
			final AbstractVertex oneNetworkVertexMaterialized = oneNetworkVertexGraphMaterialized.vertexSet().iterator()
					.next();

			RemoteSPADEQueryConnection connection = null;
			try{
				final String remoteAddress = getRemoteAddress(oneNetworkVertexMaterialized);
				final int spadeQueryPort = Settings.getCommandLineQueryPort();
				connection = new RemoteSPADEQueryConnection(Kernel.getHostName(), remoteAddress, spadeQueryPort);
				connection.connect(Kernel.getClientSocketFactory(), 5 * 1000);

				final String remoteVerticesPredicate = buildRemoteGetVertexPredicate(oneNetworkVertexMaterialized);
				final String remoteVerticesSymbol = connection.getBaseVertices(remoteVerticesPredicate);
				final GraphStatistic.Count remoteVerticesCount = connection.getGraphCount(remoteVerticesSymbol);
				if(remoteVerticesCount.getVertices() <= 0){
					continue;
				}
				final Integer remoteDepth = depth - oneNetworkVertexMinDepth;
				final String remoteLineageSymbol = connection.getBaseLineage(remoteVerticesSymbol, remoteDepth,
						direction);
				final GraphStatistic.Count remoteLineageStats = connection.getGraphCount(remoteLineageSymbol);
				if(remoteLineageStats.isEmpty()){
					continue;
				}
				final String toLinkSymbolName = connection.generateUniqueRemoteSymbolName();
				connection.executeQuery(toLinkSymbolName + " = " + remoteLineageSymbol);

				synchronized(lock){
					try{
						final boolean isValidRemoteGraph;
						if(discrepancyDetector.isFindInconsistency()){
							final boolean force = true;
							final boolean verify = false;
							final spade.core.Graph remoteLineageGraph = connection.exportGraph(remoteLineageSymbol, force, verify);
							final spade.core.Graph remoteVertexGraph = connection.exportGraph(remoteVerticesSymbol, force, verify);

							isValidRemoteGraph = discrepancyDetector.doDiscrepancyDetection(
									remoteLineageGraph, new HashSet<AbstractVertex>(remoteVertexGraph.vertexSet()), 
									remoteDepth, direction, remoteLineageGraph.getHostName()
											);
						}else{
							// Everything is valid if discrepancyDetector disabled
							isValidRemoteGraph = true;
						}
						if(isValidRemoteGraph == false){
							logger.log(Level.WARNING, "Discrepancies found in result graph. Remote graph discarded.");
						}else{
							executor.getQueryEnvironment().setRemoteSymbol(targetGraph,
									new Graph.Remote(remoteAddress, spadeQueryPort, toLinkSymbolName));
						}
					}catch(Exception e){
						throw new RuntimeException("Failed to detect discrepancies in remote graph", e);
					}
				}
			}catch(Exception e){
				logger.log(Level.WARNING, "Failed to query remote SPADE server", e);
			}finally{
				if(connection != null){
					try{
						connection.close();
					}catch(Exception e){

					}
				}
			}
		}
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

}
