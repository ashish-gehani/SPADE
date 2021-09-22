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
package spade.query.quickgrail.core;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractTransformer;
import spade.core.AbstractTransformer.ArgumentName;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Kernel;
import spade.core.Vertex;
import spade.query.quickgrail.core.EnvironmentVariableManager.Name;
import spade.query.quickgrail.core.QuickGrailQueryResolver.PredicateOperator;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.entities.GraphMetadata;
import spade.query.quickgrail.entities.GraphPredicate;
import spade.query.quickgrail.instruction.DescribeGraph;
import spade.query.quickgrail.instruction.DescribeGraph.ElementType;
import spade.query.quickgrail.instruction.ExportGraph;
import spade.query.quickgrail.instruction.GetEdgeEndpoint.Component;
import spade.query.quickgrail.instruction.GetGraphStatistic;
import spade.query.quickgrail.instruction.GetLineage;
import spade.query.quickgrail.instruction.GetLineage.Direction;
import spade.query.quickgrail.instruction.SaveGraph;
import spade.query.quickgrail.instruction.SetGraphMetadata;
import spade.query.quickgrail.utility.QuickGrailPredicateTree.PredicateNode;
import spade.query.quickgrail.utility.ResultTable;
import spade.utility.DiscrepancyDetector;
import spade.utility.HelperFunctions;
import spade.utility.RemoteSPADEQueryConnection;
import spade.utility.Result;

public abstract class QueryInstructionExecutor{

	private static final Logger logger = Logger.getLogger(QueryInstructionExecutor.class.getName());

	private final DiscrepancyDetector discrepancyDetector;

	public QueryInstructionExecutor(){
		try{
			this.discrepancyDetector = new DiscrepancyDetector();
		}catch(Throwable t){
			throw new RuntimeException("Failed to initialize discrepancy detection", t);
		}
	}

	public final DiscrepancyDetector getDiscrepancyDetector(){
		return discrepancyDetector;
	}

	public abstract AbstractQueryEnvironment getQueryEnvironment();

	public abstract AbstractStorage getStorage();

	public abstract void collapseEdge(final Graph targetGraph, final Graph sourceGraph, final ArrayList<String> fields);

	public abstract void createEmptyGraph(final Graph graph);

	public abstract void createEmptyGraphMetadata(final GraphMetadata metadata);

	public abstract GraphDescription describeGraph(final DescribeGraph instruction);

	public abstract void distinctifyGraph(final Graph targetGraph, final Graph sourceGraph);

	public final void eraseSymbols(final List<String> symbols){
		for(String symbol : symbols){
			getQueryEnvironment().removeSymbol(symbol);
		}
	}

	public abstract ResultTable evaluateQuery(final String nativeQuery);

	public final spade.core.Graph exportGraph(final Graph targetGraph, final boolean force){
		final GraphStatistic.Count count = getGraphCount(targetGraph);
		long verticesAndEdges = count.getVertices() + count.getEdges();
		if(!force){
			final int exportLimit = (int)getQueryEnvironment().getEnvVarManager().get(Name.exportLimit).getValue();
			if(verticesAndEdges > exportLimit){
				throw new RuntimeException(
						"Graph export limit set at '" + exportLimit + "'. Total vertices and edges requested '"
								+ verticesAndEdges + "'. " + "Please use 'dump all ...' to force the print.");
			}
		}
		final Map<String, Map<String, String>> queriedVerticesMap = exportVertices(targetGraph);
		final Map<String, AbstractVertex> verticesMap = new HashMap<String, AbstractVertex>();
		for(Map.Entry<String, Map<String, String>> entry : queriedVerticesMap.entrySet()){
			AbstractVertex vertex = new Vertex(entry.getKey()); // always create reference vertices
			vertex.addAnnotations(entry.getValue());
			verticesMap.put(entry.getKey(), vertex);
		}

		final Set<QueriedEdge> queriedEdges = exportEdges(targetGraph);
		final Set<AbstractEdge> edges = new HashSet<AbstractEdge>();
		for(final QueriedEdge queriedEdge : queriedEdges){
			AbstractVertex child = verticesMap.get(queriedEdge.childHash);
			AbstractVertex parent = verticesMap.get(queriedEdge.parentHash);
			if(child == null){
				child = new Vertex(queriedEdge.childHash);
				// verticesMap.put(queriedEdge.childHash, child);
			}
			if(parent == null){
				parent = new Vertex(queriedEdge.parentHash);
				// verticesMap.put(queriedEdge.parentHash, parent);
			}
			final AbstractEdge edge = new Edge(queriedEdge.edgeHash, child, parent);
			edge.addAnnotations(queriedEdge.getCopyOfAnnotations());
			edges.add(edge);
		}

		final spade.core.Graph resultGraph = new spade.core.Graph();
		resultGraph.vertexSet().addAll(verticesMap.values());
		resultGraph.edgeSet().addAll(edges);
		return resultGraph;
	}

	public abstract void getAdjacentVertex(Graph targetGraph, Graph subjectGraph, Graph sourceGraph, GetLineage.Direction direction);

	public abstract void getEdge(Graph targetGraph, Graph subjectGraph, String annotationKey, PredicateOperator operator,
			String annotationValue, final boolean hasArguments);

	public abstract void getEdgeEndpoint(Graph targetGraph, Graph subjectGraph, Component component);

	public abstract GraphStatistic.Count getGraphCount(final Graph graph);

	public abstract GraphStatistic.Distribution getGraphDistribution(final Graph graph, final ElementType elementType, final String annotationKey,
			final Integer binCount);

	public abstract GraphStatistic.Histogram getGraphHistogram(final Graph graph, final ElementType elementType, final String annotationKey);

	public abstract GraphStatistic.Mean getGraphMean(final Graph graph, final ElementType elementType, final String annotationKey);

	public abstract GraphStatistic.StandardDeviation getGraphStandardDeviation(
			final Graph graph, final ElementType elementType, final String annotationKey);

	public abstract long getGraphStatisticSize(final Graph graph, final ElementType elementType,
			final String annotationKey);

	public abstract void getLineage(Graph targetGraph, Graph subjectGraph, Graph startGraph, int depth, Direction direction);

	public abstract void getLink(Graph targetGraph, Graph subjectGraph, Graph srcGraph, Graph dstGraph, int maxDepth);

	public abstract void getMatch(final Graph targetGraph, final Graph graph1, final Graph graph2,
			final ArrayList<String> annotationKeys);

	public abstract void getSimplePath(Graph targetGraph, Graph subjectGraph, Graph srcGraph, Graph dstGraph, int maxDepth);

	public abstract void getShortestPath(Graph targetGraph, Graph subjectGraph, Graph srcGraph, Graph dstGraph, int maxDepth);

	public abstract void getSubgraph(Graph targetGraph, Graph subjectGraph, Graph skeletonGraph);

	public abstract void getVertex(Graph targetGraph, Graph subjectGraph, String annotationKey, PredicateOperator operator,
			String annotationValue, final boolean hasArguments);

	public abstract void getWhereAnnotationsExist(final Graph targetGraph, final Graph subjectGraph,
			final ArrayList<String> annotationNames);

	public abstract void insertLiteralEdge(Graph targetGraph, ArrayList<String> edges);

	public abstract void insertLiteralVertex(Graph targetGraph, ArrayList<String> vertices);

	public abstract void intersectGraph(Graph outputGraph, Graph lhsGraph, Graph rhsGraph);

	public abstract void limitGraph(Graph targetGraph, Graph sourceGraph, int limit);

	public final spade.query.quickgrail.core.List.GraphList listGraphs(){
		final AbstractQueryEnvironment environment = getQueryEnvironment();
		final String baseSymbolName = environment.getBaseGraphSymbol();
		final spade.query.quickgrail.core.List.GraphList result = new spade.query.quickgrail.core.List.GraphList(
				baseSymbolName);

		final Set<String> symbolNames = new HashSet<String>(getQueryEnvironment().getCurrentGraphSymbolsStringMap().keySet());
		symbolNames.add(baseSymbolName); // include base

		for(final String symbolName : symbolNames){
			final Graph graph = environment.getGraphSymbol(symbolName);
			try{
				final GraphStatistic.Count count = new GetGraphStatistic.Count(graph).execute(this);
				result.put(symbolName, count);
			}catch(RuntimeException e){
				throw new RuntimeException("Failed to stat graph: '" + symbolName + "'", e);
			}
		}

		return result;
	}

	public final spade.query.quickgrail.core.List.ConstraintList listConstraints(){
		final AbstractQueryEnvironment environment = getQueryEnvironment();
		final spade.query.quickgrail.core.List.ConstraintList result = new spade.query.quickgrail.core.List.ConstraintList();

		for(final String constraintName : environment.getCurrentPredicateSymbolsStringMap().keySet()){
			final GraphPredicate constraint = environment.getPredicateSymbol(constraintName);
			if(constraint != null){
				result.put(constraintName, constraint.predicateRoot);
			}
		}

		return result;
	}

	public final spade.query.quickgrail.core.List.EnvironmentList listEnvironment(){
		final spade.query.quickgrail.core.List.EnvironmentList result = new spade.query.quickgrail.core.List.EnvironmentList();
		final java.util.Set<EnvironmentVariable> envVars = getQueryEnvironment().getEnvVarManager().getAll();
		for(final EnvironmentVariable envVar : envVars){
			final String envVarValue;
			if(envVar.getValue() == null){
				envVarValue = EnvironmentVariableManager.getUndefinedConstant();
			}else{
				envVarValue = String.valueOf(envVar.getValue());
			}
			result.put(envVar.name, envVarValue);
		}
		return result;
	}

	public final spade.query.quickgrail.core.List.AllList listAll(){
		final spade.query.quickgrail.core.List.AllList result = 
				new spade.query.quickgrail.core.List.AllList(
						listGraphs(),
						listConstraints(),
						listEnvironment()
						);
		return result;
	}

	public abstract void overwriteGraphMetadata(GraphMetadata targetMetadata, GraphMetadata lhsMetadata, GraphMetadata rhsMetadata);

	public final PredicateNode printPredicate(PredicateNode predicateRoot){
		return predicateRoot;
	}

	public final void saveGraph(final Graph targetGraph, final SaveGraph.Format format, final boolean force,
			final String filePath){
		final boolean verify = false;
		final spade.core.Graph exportedGraph = new ExportGraph(targetGraph, force, verify).execute(this);
		try{
			spade.core.Graph.exportGraphToFile(format, filePath, exportedGraph);
		}catch(Exception e){
			throw new RuntimeException("Failed to save graph to file '" + filePath + "' on SPADE server", e);
		}
	}

	public abstract void setGraphMetadata(GraphMetadata targetMetadata, SetGraphMetadata.Component component, Graph sourceGraph, String name,
			String value);

	public abstract void subtractGraph(Graph outputGraph, Graph minuendGraph, Graph subtrahendGraph, Graph.Component component);

	public abstract void unionGraph(Graph targetGraph, Graph sourceGraph);

	//////////////////////////////////////////////////////////////

	public abstract Map<String, Map<String, String>> exportVertices(final Graph targetGraph);

	public abstract Set<QueriedEdge> exportEdges(final Graph targetGraph);

	public final Graph createNewGraph(){
		Graph newGraph = getQueryEnvironment().allocateGraph();
		createEmptyGraph(newGraph);
		return newGraph;
	}

	public final void clearGraph(final Graph graph){
		createEmptyGraph(graph);
	}

	public final GraphRemoteCount listRemoteVariables(final Graph localGraph){
		final GraphRemoteCount result = new GraphRemoteCount(localGraph.name);
		final Map<SimpleEntry<String, Integer>, Set<Graph.Remote>> groupedRemotes = localGraph.groupRemotesByConnections();
		for(final Map.Entry<SimpleEntry<String, Integer>, Set<Graph.Remote>> entry : groupedRemotes.entrySet()){
			final SimpleEntry<String, Integer> sock = entry.getKey();
			final Set<Graph.Remote> remotes = entry.getValue();
			RemoteSPADEQueryConnection connection = null;
			try{
				connection = new RemoteSPADEQueryConnection(Kernel.getHostName(), sock.getKey(), sock.getValue());
				connection.connect(Kernel.getClientSocketFactory(), 5 * 1000);
			}catch(Exception e){
				logger.log(Level.WARNING, "Failed to connect to remote SPADE server at: " + sock, e);
				connection = null;
			}
			for(final Graph.Remote remote : remotes){
				final String remoteSymbol = remote.symbol;
				GraphStatistic.Count remoteGraphStatistics = null;
				if(connection != null){
					try{
						remoteGraphStatistics = connection.getGraphCount(remoteSymbol);
					}catch(Exception e){
						logger.log(Level.WARNING, "Failed to talk to remote SPADE server at: " + sock, e);
						remoteGraphStatistics = null;
					}
				}
				if(remoteGraphStatistics == null){
					remoteGraphStatistics = new GraphStatistic.Count(-1, -1);
				}
				result.put(remote.toFormattedString(), remoteGraphStatistics);
			}
			try{
				connection.close();
			}catch(Exception e){
				logger.log(Level.WARNING, "Failed to close connection to remote SPADE server at: " + sock, e);
			}
		}

		return result;
	}

	public void putGraph(final long putGraphBatchSize, final Graph targetGraph, final spade.core.Graph graph){
		final AbstractStorage storage = getStorage();

		final Graph verticesGraph = createNewGraph();
		final Graph edgesGraph = createNewGraph();

		// The following two calls insert vertices/edges in 'graph' that exist in $base,
		// into the variable 'targetGraph'
		insertVertexHashesInBatches(putGraphBatchSize, verticesGraph, graph);
		insertEdgeHashesInBatches(putGraphBatchSize, edgesGraph, graph);

		// Getting the vertices/edges that were assigned to variable 'targetGraph'
		final spade.core.Graph vertexGraph = exportGraph(verticesGraph, true);
		final spade.core.Graph edgeGraph = exportGraph(edgesGraph, true);

		// Subtracting vertices in 'targetGraph' from 'graph' to get the vertices which
		// are not in $base
		final spade.core.Graph subgraphNotPresent = new spade.core.Graph();
		subgraphNotPresent.vertexSet().addAll(graph.vertexSet());
		subgraphNotPresent.edgeSet().addAll(graph.edgeSet());
		subgraphNotPresent.vertexSet().removeAll(vertexGraph.vertexSet());
		subgraphNotPresent.edgeSet().removeAll(edgeGraph.edgeSet());

		// Putting the new vertices in $base
		for(final AbstractVertex vertex : subgraphNotPresent.vertexSet()){
			storage.putVertex(vertex);
		}

		// Putting the new edges in $base
		for(final AbstractEdge edge : subgraphNotPresent.edgeSet()){
			storage.putEdge(edge);
		}

		storage.flushTransactions(true);

		HelperFunctions.sleepSafe(50);

		// The vertices/edges that were not in $base (but were in 'graph') are now in
		// $base
		// Insert the just-added vertices and edges to 'targetGraph'
		insertVertexHashesInBatches(putGraphBatchSize, verticesGraph, graph);
		insertEdgeHashesInBatches(putGraphBatchSize, edgesGraph, graph);

		// Union into one variable 'targetGraph'
		unionGraph(targetGraph, verticesGraph);
		unionGraph(targetGraph, edgesGraph);
	}

	public void insertVertexHashesInBatches(final long putGraphBatchSize, final Graph targetGraph,
			final spade.core.Graph srcGraph){
		final Set<String> vertexHashSet = new HashSet<String>();
		for(AbstractVertex v : srcGraph.vertexSet()){
			vertexHashSet.add(v.bigHashCode());
			if(vertexHashSet.size() >= putGraphBatchSize){
				try{
					insertLiteralVertex(targetGraph, new ArrayList<String>(vertexHashSet));
				}finally{
					vertexHashSet.clear();
				}
			}
		}

		if(vertexHashSet.size() > 0){
			try{
				insertLiteralVertex(targetGraph, new ArrayList<String>(vertexHashSet));
			}finally{
				vertexHashSet.clear();
			}
		}
	}

	public void insertEdgeHashesInBatches(final long putGraphBatchSize, final Graph targetGraph,
			final spade.core.Graph srcGraph){
		final Set<String> edgeHashSet = new HashSet<String>();
		for(AbstractEdge e : srcGraph.edgeSet()){
			edgeHashSet.add(e.bigHashCode());
			if(edgeHashSet.size() >= putGraphBatchSize){
				try{
					insertLiteralEdge(targetGraph, new ArrayList<String>(edgeHashSet));
				}finally{
					edgeHashSet.clear();
				}
			}
		}

		if(edgeHashSet.size() > 0){
			try{
				insertLiteralEdge(targetGraph, new ArrayList<String>(edgeHashSet));
			}finally{
				edgeHashSet.clear();
			}
		}
	}

	public final void getPath(final Graph targetGraph, final Graph subjectGraph, final Graph srcGraph,
			final ArrayList<SimpleEntry<Graph, Integer>> intermediateSteps){
		final Graph unionResultGraph = createNewGraph();

		Graph sourceGraph = srcGraph;
		final int totalIntermediateSteps = intermediateSteps.size();
		for(int i = 0; i < totalIntermediateSteps; i++){
			final SimpleEntry<Graph, Integer> intermediateStep = intermediateSteps.get(i);
			final Graph intermediateStepGraph = intermediateStep.getKey();
			final int intermediateStepMaxDepth = intermediateStep.getValue();

			final Graph intermediateResult = createNewGraph();

			getSimplePath(intermediateResult, subjectGraph, sourceGraph, intermediateStepGraph,
					intermediateStepMaxDepth);

			if(i == totalIntermediateSteps - 1){
				// last step so no need to get the intersection
			}else{
				final Graph intermediateIntersectionResult = createNewGraph();
				intersectGraph(intermediateIntersectionResult, intermediateResult, intermediateStepGraph);

				final GraphStatistic.Count count = getGraphCount(intermediateIntersectionResult);
				if(count.getVertices() <= 0){
					// No point in going further
					break;
				}else{
					sourceGraph = intermediateIntersectionResult;
				}
			}
			unionGraph(unionResultGraph, intermediateResult);
		}

		distinctifyGraph(targetGraph, unionResultGraph);
	}

	public final void transformGraph(final String transformerName, final String transformerInitializeArgument,
			final Graph outputGraph, final Graph subjectGraph, final java.util.List<Object> arguments,
			final int putGraphBatchSize){
		final Result<AbstractTransformer> createResult = AbstractTransformer.create(transformerName);
		if(createResult.error){
			throw new RuntimeException(createResult.toErrorString());
		}

		boolean transformerInitialized = false;
		final AbstractTransformer transformer = createResult.result;

		try{
			final LinkedHashSet<ArgumentName> argumentNames = transformer.getArgumentNames();
			if(argumentNames == null){
				throw new RuntimeException("Invalid transformer implementation. NULL argument names");
			}

			if(argumentNames.size() != arguments.size()){
				throw new RuntimeException("Invalid # of transformer arguments. Expected: " + argumentNames);
			}

			final AbstractTransformer.ExecutionContext executionContext = new AbstractTransformer.ExecutionContext();

			int i = -1;
			for(final ArgumentName argumentName : argumentNames){
				i++;
				if(argumentName == null){
					throw new RuntimeException("NULL transformer argument name at index: " + i);
				}
				final Object instructionArgument = arguments.get(i);
				if(instructionArgument == null){
					throw new RuntimeException("NULL transformer argument in instruction at index: " + i);
				}
				switch(argumentName){
				case SOURCE_GRAPH:{
					if(!instructionArgument.getClass().equals(spade.query.quickgrail.entities.Graph.class)){
						throw new RuntimeException("Transformer argument must be a graph variable at index: " + i);
					}else{
						final spade.query.quickgrail.entities.Graph sourceGraphVariable = (spade.query.quickgrail.entities.Graph)instructionArgument;
						final spade.core.Graph sourceGraph = exportGraph(sourceGraphVariable, true);
						executionContext.setSourceGraph(sourceGraph); // Set
					}
					break;
				}
				case MAX_DEPTH:{
					if(!instructionArgument.getClass().equals(Integer.class)){
						throw new RuntimeException("Transformer argument must be an integer literal at index: " + i);
					}else{
						final Integer maxDepth = (Integer)instructionArgument;
						executionContext.setMaxDepth(maxDepth); // Set
					}
					break;
				}
				case DIRECTION:{
					if(!instructionArgument.getClass().equals(GetLineage.Direction.class)){
						throw new RuntimeException("Transformer argument must be a string literal at index: " + i);
					}else{
						final GetLineage.Direction direction = (GetLineage.Direction)instructionArgument;
						executionContext.setDirection(direction); // Set
					}
					break;
				}
				default:
					throw new RuntimeException("Unhandled transformer argument name: " + argumentName);
				}
			}

			final Result<Boolean> initResult = AbstractTransformer.init(transformer, transformerInitializeArgument);
			if(initResult.error){
				throw new RuntimeException(initResult.errorMessage, initResult.exception);
			}
			if(!initResult.result.booleanValue()){
				throw new RuntimeException("Failed to initialize transformer");
			}
			transformerInitialized = true;

			final spade.core.Graph subjectGraphExported = exportGraph(subjectGraph, true);
			final Result<spade.core.Graph> executeResult = AbstractTransformer.execute(transformer, subjectGraphExported,
					executionContext);
			if(executeResult.error){
				throw new RuntimeException(executeResult.errorMessage, executeResult.exception);
			}

			final spade.core.Graph transformedGraph = executeResult.result.copyContents();

			putGraph(putGraphBatchSize, outputGraph, transformedGraph);
		}finally{
			if(transformerInitialized){
				final Result<Boolean> shutdownResult = AbstractTransformer.destroy(transformer);
				if(shutdownResult.error){
					throw new RuntimeException(shutdownResult.toErrorString());
				}
			}
		}
	}

	public final ArrayList<Integer> getPathLengths(final Graph subjectGraph, final Graph startGraph, 
			final Graph toGraph, final int maxDepth){
		final java.util.ArrayList<Integer> result = new java.util.ArrayList<Integer>();
		final Graph commonGraph = createNewGraph();
		final Graph currentLevelGraph = createNewGraph();
		final Graph sourceGraph = createNewGraph();
		final Graph adjacentGraph = createNewGraph();
		unionGraph(sourceGraph, startGraph);
		for(int i = 0; i < maxDepth; i++){
			getAdjacentVertex(adjacentGraph, subjectGraph, sourceGraph, GetLineage.Direction.kAncestor);

			subtractGraph(currentLevelGraph, adjacentGraph, sourceGraph, Graph.Component.kVertex);
			final GraphStatistic.Count currentLevelCount = getGraphCount(currentLevelGraph);
			if(currentLevelCount.getVertices() <= 0){
				break;
			}

			intersectGraph(commonGraph, currentLevelGraph, toGraph);
			final GraphStatistic.Count commonCount = getGraphCount(commonGraph);
			if(commonCount.getVertices() > 0){
				// Found a path at this depth
				result.add(i + 1);
			}

			clearGraph(adjacentGraph);
			clearGraph(commonGraph);
			clearGraph(sourceGraph);
			unionGraph(sourceGraph, currentLevelGraph);
			clearGraph(currentLevelGraph);
		}
		clearGraph(adjacentGraph);
		clearGraph(commonGraph);
		clearGraph(sourceGraph);
		clearGraph(currentLevelGraph);
		return result;
	}

}
