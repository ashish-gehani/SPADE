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
package spade.query.quickgrail;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Kernel;
import spade.core.Query;
import spade.core.Settings;
import spade.core.Vertex;
import spade.query.quickgrail.core.AbstractQueryEnvironment;
import spade.query.quickgrail.core.EnvironmentVariable;
import spade.query.quickgrail.core.GraphDescription;
import spade.query.quickgrail.core.GraphStats;
import spade.query.quickgrail.core.Program;
import spade.query.quickgrail.core.QueriedEdge;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.QuickGrailQueryResolver;
import spade.query.quickgrail.core.QuickGrailQueryResolver.PredicateOperator;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.entities.GraphPredicate;
import spade.query.quickgrail.instruction.CollapseEdge;
import spade.query.quickgrail.instruction.CreateEmptyGraph;
import spade.query.quickgrail.instruction.CreateEmptyGraphMetadata;
import spade.query.quickgrail.instruction.DescribeGraph;
import spade.query.quickgrail.instruction.DistinctifyGraph;
import spade.query.quickgrail.instruction.EnvironmentVariableOperation;
import spade.query.quickgrail.instruction.EraseSymbols;
import spade.query.quickgrail.instruction.EvaluateQuery;
import spade.query.quickgrail.instruction.ExportGraph;
import spade.query.quickgrail.instruction.ExportGraph.Format;
import spade.query.quickgrail.instruction.GetAdjacentVertex;
import spade.query.quickgrail.instruction.GetEdge;
import spade.query.quickgrail.instruction.GetEdgeEndpoint;
import spade.query.quickgrail.instruction.GetLineage;
import spade.query.quickgrail.instruction.GetLineage.Direction;
import spade.query.quickgrail.instruction.GetLink;
import spade.query.quickgrail.instruction.GetMatch;
import spade.query.quickgrail.instruction.GetPath;
import spade.query.quickgrail.instruction.GetShortestPath;
import spade.query.quickgrail.instruction.GetSimplePath;
import spade.query.quickgrail.instruction.GetSubgraph;
import spade.query.quickgrail.instruction.GetVertex;
import spade.query.quickgrail.instruction.InsertLiteralEdge;
import spade.query.quickgrail.instruction.InsertLiteralVertex;
import spade.query.quickgrail.instruction.Instruction;
import spade.query.quickgrail.instruction.IntersectGraph;
import spade.query.quickgrail.instruction.LimitGraph;
import spade.query.quickgrail.instruction.List.ListType;
import spade.query.quickgrail.instruction.OverwriteGraphMetadata;
import spade.query.quickgrail.instruction.PrintPredicate;
import spade.query.quickgrail.instruction.SetGraphMetadata;
import spade.query.quickgrail.instruction.StatGraph;
import spade.query.quickgrail.instruction.SubtractGraph;
import spade.query.quickgrail.instruction.UnionGraph;
import spade.query.quickgrail.parser.DSLParserWrapper;
import spade.query.quickgrail.parser.ParseProgram;
import spade.query.quickgrail.types.LongType;
import spade.query.quickgrail.types.StringType;
import spade.query.quickgrail.utility.QuickGrailPredicateTree.PredicateNode;
import spade.query.quickgrail.utility.ResultTable;
import spade.query.quickgrail.utility.Schema;
import spade.reporter.audit.OPMConstants;
import spade.transformer.ABE;
import spade.utility.DiscrepancyDetector;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;
import spade.utility.RemoteSPADEQueryConnection;
import spade.utility.Result;

/**
 * Top level class for the QuickGrail graph query executor.
 */
public class QuickGrailExecutor{

	// TODO should this be static i.e. one for the whole system?
	private final DiscrepancyDetector discrepancyDetector;

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private static final String configKeyDumpLimit = "dumpLimit", configKeyVisualizeLimit = "visualizeLimit"; 
	private long exportGraphDumpLimit, exportGraphVisualizeLimit;

	private final AbstractQueryEnvironment queryEnvironment;
	private final QueryInstructionExecutor instructionExecutor;

	public QuickGrailExecutor(QueryInstructionExecutor instructionExecutor){
		initializeGlobalsFromDefaultConfigFile();
		
		this.instructionExecutor = instructionExecutor;
		if(this.instructionExecutor == null){
			throw new IllegalArgumentException("NULL instruction executor");
		}
		this.queryEnvironment = instructionExecutor.getQueryEnvironment();
		if(this.queryEnvironment == null){
			throw new IllegalArgumentException("NULL variable manager");
		}
		try{
			this.discrepancyDetector = new DiscrepancyDetector();
		}catch(Throwable t){
			throw new RuntimeException("Failed to initialize discrepancy detection", t);
		}
	}
	
	private void initializeGlobalsFromDefaultConfigFile() throws RuntimeException{
		String configFilePath = Settings.getDefaultConfigFilePath(this.getClass());
		try{
			Map<String, String> map = FileUtility.readConfigFileAsKeyValueMap(configFilePath, "=");
			Result<Long> dumpLimitResult = HelperFunctions.parseLong(map.get(configKeyDumpLimit), 10, 0, Long.MAX_VALUE);
			if(dumpLimitResult.error){
				throw new RuntimeException("Invalid '"+configKeyDumpLimit+"' value. " + dumpLimitResult.toErrorString());
			}
			exportGraphDumpLimit = dumpLimitResult.result;
			
			Result<Long> visualizeLimitResult = HelperFunctions.parseLong(map.get(configKeyVisualizeLimit), 10, 0, Long.MAX_VALUE);
			if(visualizeLimitResult.error){
				throw new RuntimeException("Invalid '"+configKeyVisualizeLimit+"' value. " + visualizeLimitResult.toErrorString());
			}
			exportGraphVisualizeLimit = visualizeLimitResult.result;
			
			logger.log(Level.INFO, "Globals: {0}={1}, {2}={3}", 
					new Object[]{
							configKeyDumpLimit, String.valueOf(exportGraphDumpLimit),
							configKeyVisualizeLimit, String.valueOf(exportGraphVisualizeLimit)
							});
		}catch(Exception e){
			throw new RuntimeException("Failed to initialize globals in file '"+configFilePath+"'. " + e.getMessage());
		}
	}

	public Query execute(Query query){
		try{
			
			DSLParserWrapper parserWrapper = new DSLParserWrapper();

			ParseProgram parseProgram = parserWrapper.fromText(query.query);

			logger.log(Level.INFO, "Parse tree:\n" + parseProgram.toString());

			QuickGrailQueryResolver resolver = new QuickGrailQueryResolver();
			Program program = resolver.resolveProgram(parseProgram, queryEnvironment);

			logger.log(Level.INFO, "Execution plan:\n" + program.toString());

			try{
				int instructionsSize = program.getInstructionsSize();
				for(int i = 0; i < instructionsSize; i++){
					Instruction executableInstruction = program.getInstruction(i);
					try{
						query = executeInstruction(executableInstruction, query);
					}catch(Exception e){
						throw e;
					}
				}

			}finally{
				queryEnvironment.doGarbageCollection();
			}

			// Only here if success
			if(query.getResult() != null){
				// The result of this query has already been pre-set by one of the
				// instructions.
			}else{
				query.querySucceeded("OK");
			}
			return query;
		}catch(Exception e){
			logger.log(Level.SEVERE, null, e);

			StringWriter stackTrace = new StringWriter();
			PrintWriter pw = new PrintWriter(stackTrace);
			pw.println("Error evaluating QuickGrail command:");
			pw.println("------------------------------------------------------------");
			//e.printStackTrace(pw);
			pw.println(e.getMessage());
			pw.println("------------------------------------------------------------");

			query.queryFailed(new Exception(stackTrace.toString(), e));
			return query;
		}
	}

	// Have to set queryinstruction success
	private Query executeInstruction(Instruction instruction, Query query) throws Exception{

		Serializable result = "OK"; // default result

		if(instruction.getClass().equals(ExportGraph.class)){
			spade.core.Graph graph = exportGraph((ExportGraph)instruction);
			if(graph != null){
				result = graph;
			}

		}else if(instruction.getClass().equals(CollapseEdge.class)){
			instructionExecutor.collapseEdge((CollapseEdge)instruction);

		}else if(instruction.getClass().equals(CreateEmptyGraph.class)){
			instructionExecutor.createEmptyGraph((CreateEmptyGraph)instruction);

		}else if(instruction.getClass().equals(CreateEmptyGraphMetadata.class)){
			instructionExecutor.createEmptyGraphMetadata((CreateEmptyGraphMetadata)instruction);

		}else if(instruction.getClass().equals(DistinctifyGraph.class)){
			instructionExecutor.distinctifyGraph((DistinctifyGraph)instruction);

		}else if(instruction.getClass().equals(EraseSymbols.class)){
			instructionExecutor.eraseSymbols((EraseSymbols)instruction);

		}else if(instruction.getClass().equals(EvaluateQuery.class)){
			ResultTable table = instructionExecutor.evaluateQuery((EvaluateQuery)instruction);
			if(table == null){
				result = "No Result!";
			}else{
				result = String.valueOf(table);
			}

		}else if(instruction.getClass().equals(GetAdjacentVertex.class)){
			instructionExecutor.getAdjacentVertex((GetAdjacentVertex)instruction);

		}else if(instruction.getClass().equals(GetEdge.class)){
			instructionExecutor.getEdge((GetEdge)instruction);

		}else if(instruction.getClass().equals(GetEdgeEndpoint.class)){
			instructionExecutor.getEdgeEndpoint((GetEdgeEndpoint)instruction);

		}else if(instruction.getClass().equals(GetLineage.class)){
			GetLineage getLineage = (GetLineage)instruction;
			query = getLineage(getLineage, query);
		}else if(instruction.getClass().equals(GetLink.class)){
			instructionExecutor.getLink((GetLink)instruction);

		}else if(instruction.getClass().equals(GetPath.class)){
			getPath((GetPath)instruction);

		}else if(instruction.getClass().equals(GetSimplePath.class)){
			instructionExecutor.getPath((GetSimplePath)instruction);

		}else if(instruction.getClass().equals(GetShortestPath.class)){
			instructionExecutor.getShortestPath((GetShortestPath)instruction);

		}else if(instruction.getClass().equals(GetSubgraph.class)){
			instructionExecutor.getSubgraph((GetSubgraph)instruction);

		}else if(instruction.getClass().equals(GetVertex.class)){
			instructionExecutor.getVertex((GetVertex)instruction);

		}else if(instruction.getClass().equals(InsertLiteralEdge.class)){
			instructionExecutor.insertLiteralEdge((InsertLiteralEdge)instruction);

		}else if(instruction.getClass().equals(InsertLiteralVertex.class)){
			instructionExecutor.insertLiteralVertex((InsertLiteralVertex)instruction);

		}else if(instruction.getClass().equals(IntersectGraph.class)){
			instructionExecutor.intersectGraph((IntersectGraph)instruction);

		}else if(instruction.getClass().equals(LimitGraph.class)){
			instructionExecutor.limitGraph((LimitGraph)instruction);

		}else if(instruction.getClass().equals(spade.query.quickgrail.instruction.List.class)){
			String tablesAsString = list((spade.query.quickgrail.instruction.List)instruction);
			if(tablesAsString == null){
				result = "No Result!";
			}else{
				result = tablesAsString;
			}

		}else if(instruction.getClass().equals(OverwriteGraphMetadata.class)){
			instructionExecutor.overwriteGraphMetadata((OverwriteGraphMetadata)instruction);

		}else if(instruction.getClass().equals(SetGraphMetadata.class)){
			instructionExecutor.setGraphMetadata((SetGraphMetadata)instruction);

		}else if(instruction.getClass().equals(StatGraph.class)){
			GraphStats stats = instructionExecutor.statGraph((StatGraph)instruction);
			if(stats == null){
				result = "No Result!";
			}else{
				result = stats;
			}
		}else if(instruction.getClass().equals(DescribeGraph.class)){
			GraphDescription graphDescription = instructionExecutor.describeGraph((DescribeGraph)instruction);
			if(graphDescription == null){
				result = "No Result!";
			}else{
				result = graphDescription;
			}
		}else if(instruction.getClass().equals(SubtractGraph.class)){
			instructionExecutor.subtractGraph((SubtractGraph)instruction);

		}else if(instruction.getClass().equals(UnionGraph.class)){
			instructionExecutor.unionGraph((UnionGraph)instruction);

		}else if(instruction.getClass().equals(PrintPredicate.class)){
			PredicateNode predicateNode = instructionExecutor.printPredicate((PrintPredicate)instruction);
			if(predicateNode == null){
				result = "No Result!";
			}else{
				result = String.valueOf(predicateNode);
			}

		}else if(instruction.getClass().equals(EnvironmentVariableOperation.class)){
			final Serializable optionalResult = environmentVariableOperation((EnvironmentVariableOperation)instruction);
			if(optionalResult != null){
				result = optionalResult;
			}
		}else if(instruction.getClass().equals(GetMatch.class)){
			instructionExecutor.getMatch((GetMatch)instruction);

		}else{
			throw new RuntimeException("Unhandled instruction: " + instruction.getClass());
		}
		query.querySucceeded(result);
		return query;
	}

	private final Serializable environmentVariableOperation(final EnvironmentVariableOperation instruction){
		switch(instruction.type){
			case SET:{
				final String name = instruction.name;
				final EnvironmentVariable envVar = queryEnvironment.getEnvironmentVariable(name);
				if(envVar == null){
					throw new RuntimeException("No environment variable defined by name: " + name);
				}
				envVar.setValue(instruction.value);
				return null;
			}
			case UNSET:{
				final String name = instruction.name;
				final EnvironmentVariable envVar = queryEnvironment.getEnvironmentVariable(name);
				if(envVar == null){
					throw new RuntimeException("No environment variable defined by name: " + name);
				}
				envVar.unsetValue();
				return null;
			}
			case LIST:{
				return getTableOfEnvironmentVariables().toString();
			}
			case PRINT:{
				final String name = instruction.name;
				final EnvironmentVariable envVar = queryEnvironment.getEnvironmentVariable(name);
				if(envVar == null){
					throw new RuntimeException("No environment variable defined by name: " + name);
				}
				Object value = envVar.getValue();
				if(value == null){
					return AbstractQueryEnvironment.environmentVariableValueUNSET; // empty
				}else{
					return String.valueOf(value);
				}
			}
			default: throw new RuntimeException("Unhandled type for environment operation: " + instruction.type);
		}
	}
	
	private final ResultTable getTableOfEnvironmentVariables(){
		final List<EnvironmentVariable> envVars = queryEnvironment.getEnvironmentVariables();
		final ResultTable table = new ResultTable();
		for(final EnvironmentVariable envVar : envVars){
			ResultTable.Row row = new ResultTable.Row();
			row.add(envVar.name);
			if(envVar.getValue() == null){
				row.add(AbstractQueryEnvironment.environmentVariableValueUNSET);
			}else{
				row.add(String.valueOf(envVar.getValue()));
			}
			table.addRow(row);
		}
		Schema schema = new Schema();
		schema.addColumn("Environment Variable Name", StringType.GetInstance());
		schema.addColumn("Value", StringType.GetInstance());
		table.setSchema(schema);
		return table;
	}

	public spade.core.Graph exportGraph(final ExportGraph instruction){// throws Exception{
		GraphStats stats = instructionExecutor.statGraph(new StatGraph(instruction.targetGraph));
		long verticesAndEdges = stats.vertices + stats.edges;
		if(!instruction.force){
			if(instruction.format == Format.kNormal && verticesAndEdges > exportGraphDumpLimit){
				throw new RuntimeException(
						"Dump export limit set at '" + exportGraphDumpLimit + "'. Total vertices and edges requested '"
								+ verticesAndEdges + "'. " + "Please use 'dump force ...' to force the print.");
			}else if(instruction.format == Format.kDot && verticesAndEdges > exportGraphVisualizeLimit){
				throw new RuntimeException("Dot export limit set at '" + exportGraphVisualizeLimit
						+ "'. Total vertices and edges requested '" + verticesAndEdges + "'. "
						+ "Please use 'visualize force ...' to force the transfer.");
			}
		}
		final Map<String, Map<String, String>> queriedVerticesMap = instructionExecutor.exportVertices(instruction);
		final Map<String, AbstractVertex> verticesMap = new HashMap<String, AbstractVertex>();
		for(Map.Entry<String, Map<String, String>> entry : queriedVerticesMap.entrySet()){
			AbstractVertex vertex = new Vertex(entry.getKey()); // always create reference vertices
			vertex.addAnnotations(entry.getValue());
			verticesMap.put(entry.getKey(), vertex);
		}
		
		final Set<QueriedEdge> queriedEdges = instructionExecutor.exportEdges(instruction);
		final Set<AbstractEdge> edges = new HashSet<AbstractEdge>();
		for(QueriedEdge queriedEdge : queriedEdges){
			AbstractVertex child = verticesMap.get(queriedEdge.childHash);
			AbstractVertex parent = verticesMap.get(queriedEdge.parentHash);
			if(child == null){
				child = new Vertex(queriedEdge.childHash);
				//verticesMap.put(queriedEdge.childHash, child);
			}
			if(parent == null){
				parent = new Vertex(queriedEdge.parentHash);
				//verticesMap.put(queriedEdge.parentHash, parent);
			}
			AbstractEdge edge = new Edge(queriedEdge.edgeHash, child, parent);
			edge.addAnnotations(queriedEdge.getCopyOfAnnotations());
			edges.add(edge);
		}
		
		spade.core.Graph resultGraph = new spade.core.Graph();
		resultGraph.vertexSet().addAll(verticesMap.values());
		resultGraph.edgeSet().addAll(edges);
		if(instruction.filePathOnServer != null){
			try{
				spade.core.Graph.exportGraphToFile(instruction.format, instruction.filePathOnServer, resultGraph);
				return null;
			}catch(Exception e){
				throw new RuntimeException("Failed to export graph to file '"+instruction.filePathOnServer+"' on server", e);
			}
		}else{
			return resultGraph;
		}
	}
	
	private String list(final spade.query.quickgrail.instruction.List instruction){
		if(instruction.type == null){
			throw new RuntimeException("NULL type for list instruction");
		}else{
			ResultTable graphTable = null;
			ResultTable predicateTable = null;
			ResultTable envVarTable = null;
			
			if(instruction.type == ListType.ALL || instruction.type == ListType.GRAPH){
				graphTable = new ResultTable();
				
				Map<String, GraphStats> graphsMap = instructionExecutor.listGraphs(instruction);

				List<String> sortedNonBaseSymbolNames = new ArrayList<String>();
				sortedNonBaseSymbolNames.addAll(graphsMap.keySet());
				sortedNonBaseSymbolNames.remove(queryEnvironment.getBaseGraphSymbol());
				Collections.sort(sortedNonBaseSymbolNames);

				for(String symbolName : sortedNonBaseSymbolNames){
					GraphStats graphStats = graphsMap.get(symbolName);
					ResultTable.Row row = new ResultTable.Row();
					row.add(symbolName);
					row.add(graphStats.vertices);
					row.add(graphStats.edges);
					graphTable.addRow(row);
				}

				// Add base last
				GraphStats graphStats = graphsMap.get(queryEnvironment.getBaseGraphSymbol());
				ResultTable.Row row = new ResultTable.Row();
				row.add(queryEnvironment.getBaseGraphSymbol());
				row.add(graphStats.vertices);
				row.add(graphStats.edges);
				graphTable.addRow(row);

				Schema schema = new Schema();
				schema.addColumn("Graph Name", StringType.GetInstance());
				schema.addColumn("Number of Vertices", LongType.GetInstance());
				schema.addColumn("Number of Edges", LongType.GetInstance());
				graphTable.setSchema(schema);
			}
			
			if(instruction.type == ListType.ALL || instruction.type == ListType.CONSTRAINT){
				predicateTable = new ResultTable();
				List<String> predicateSymbolsSorted = new ArrayList<String>(queryEnvironment.getCurrentPredicateSymbolsStringMap().keySet());
				Collections.sort(predicateSymbolsSorted);
				for(String predicateSymbolName : predicateSymbolsSorted){
					GraphPredicate predicate = queryEnvironment.getPredicateSymbol(predicateSymbolName);
					if(predicate != null){
						ResultTable.Row prow = new ResultTable.Row();
						prow.add(predicateSymbolName);
						prow.add(predicate.predicateRoot.toString());
						predicateTable.addRow(prow);
					}
				}

				Schema pschema = new Schema();
				pschema.addColumn("Constraint Name", StringType.GetInstance());
				pschema.addColumn("Value", StringType.GetInstance());
				predicateTable.setSchema(pschema);
			}
			
			if(instruction.type == ListType.ALL || instruction.type == ListType.ENV){
				envVarTable = getTableOfEnvironmentVariables();
			}
			
			switch(instruction.type){
				case ALL: 
					return graphTable.toString() 
							+ System.lineSeparator() 
							+ predicateTable.toString()
							+ System.lineSeparator()
							+ envVarTable.toString();
				case GRAPH: return graphTable.toString();
				case CONSTRAINT: return predicateTable.toString();
				case ENV: return envVarTable.toString();
				default: throw new RuntimeException("Unknown type for list instruction: " + instruction.type.toString().toLowerCase());
			}
		}
	}

	private void getPath(final GetPath instruction){
		Graph unionResultGraph = createNewGraph();
		
		Graph subjectGraph = instruction.subjectGraph;
		Graph sourceGraph = instruction.srcGraph;
		int totalIntermediateSteps = instruction.getIntermediateStepsCount();
		for(int i = 0; i < totalIntermediateSteps; i++){
			SimpleEntry<Graph, Integer> intermediateStep = instruction.getIntermediateStep(i);
			Graph intermediateStepGraph = intermediateStep.getKey();
			int intermediateStepMaxDepth = intermediateStep.getValue();
			
			Graph intermediateResult = createNewGraph();
			instructionExecutor.getPath(new GetSimplePath(
					intermediateResult, subjectGraph, sourceGraph, intermediateStepGraph, intermediateStepMaxDepth));
			
			if(i == totalIntermediateSteps - 1){
				// last step so no need to get the intersection
			}else{
				Graph intermediateIntersectionResult = createNewGraph();
				instructionExecutor.intersectGraph(new IntersectGraph(intermediateIntersectionResult, 
						intermediateResult, intermediateStepGraph));
				
				GraphStats stats = getGraphStats(intermediateIntersectionResult);
				if(stats.vertices <= 0){
					// No point in going further
					break;
				}else{
					sourceGraph = intermediateIntersectionResult;
				}
			}
			instructionExecutor.unionGraph(new UnionGraph(unionResultGraph, intermediateResult));
		}
		
		instructionExecutor.distinctifyGraph(new DistinctifyGraph(instruction.targetGraph, unionResultGraph));
	}
	
	/*
	 * private void getPath(GetPath instruction){ Graph ancestorsOfFromGraph =
	 * queryEnvironment.allocateGraph(); instructionExecutor.createEmptyGraph(new
	 * CreateEmptyGraph(ancestorsOfFromGraph));
	 * 
	 * Graph descendantsOfToGraph = queryEnvironment.allocateGraph();
	 * instructionExecutor.createEmptyGraph(new
	 * CreateEmptyGraph(descendantsOfToGraph));
	 * 
	 * getLineage(new GetLineage(ancestorsOfFromGraph, instruction.subjectGraph,
	 * instruction.srcGraph, instruction.maxDepth, GetLineage.Direction.kAncestor,
	 * false));
	 * 
	 * getLineage(new GetLineage(descendantsOfToGraph, instruction.subjectGraph,
	 * instruction.dstGraph, instruction.maxDepth, GetLineage.Direction.kDescendant,
	 * false));
	 * 
	 * Graph intersectionGraph = queryEnvironment.allocateGraph();
	 * instructionExecutor.createEmptyGraph(new
	 * CreateEmptyGraph(intersectionGraph));
	 * 
	 * instructionExecutor .intersectGraph(new IntersectGraph(intersectionGraph,
	 * ancestorsOfFromGraph, descendantsOfToGraph));
	 * 
	 * Graph fromGraphInIntersection = queryEnvironment.allocateGraph();
	 * instructionExecutor.createEmptyGraph(new
	 * CreateEmptyGraph(fromGraphInIntersection));
	 * 
	 * instructionExecutor .intersectGraph(new
	 * IntersectGraph(fromGraphInIntersection, intersectionGraph,
	 * instruction.srcGraph));
	 * 
	 * Graph toGraphInIntersection = queryEnvironment.allocateGraph();
	 * instructionExecutor.createEmptyGraph(new
	 * CreateEmptyGraph(toGraphInIntersection));
	 * 
	 * instructionExecutor .intersectGraph(new IntersectGraph(toGraphInIntersection,
	 * intersectionGraph, instruction.dstGraph));
	 * 
	 * if(!instructionExecutor.statGraph(new
	 * StatGraph(fromGraphInIntersection)).isEmpty() &&
	 * !instructionExecutor.statGraph(new
	 * StatGraph(toGraphInIntersection)).isEmpty()){
	 * instructionExecutor.unionGraph(new UnionGraph(instruction.targetGraph,
	 * intersectionGraph)); // means we // found a path } }
	 */
	
	private Query getLineage(final GetLineage instruction, final Query originalSPADEQuery){
		final Set<AbstractVertex> startGraphVertices = new HashSet<AbstractVertex>();
		if(getGraphStats(instruction.startGraph).vertices > 0){
			startGraphVertices.addAll(exportGraph(instruction.startGraph).vertexSet());
		}
		
		// need to do here because even if there is no lineage that might mean something to a transformer. 
		try{
			originalSPADEQuery.getQueryMetaData().setMaxLength(instruction.depth);
			originalSPADEQuery.getQueryMetaData().setDirection(instruction.direction);
			originalSPADEQuery.getQueryMetaData().addRootVertices(startGraphVertices);
		}catch(Exception e){
			logger.log(Level.WARNING, "Failed to add root vertices for transformers from get lineage query");
		}
		
		if(startGraphVertices.size() == 0
				|| getGraphStats(instruction.subjectGraph).edges == 0){
			// Nothing to start from since no vertices OR no where to go in the subject since no edges
			return originalSPADEQuery;
		}		

		final List<Direction> directions = new ArrayList<Direction>();
		if(Direction.kAncestor.equals(instruction.direction) || Direction.kDescendant.equals(instruction.direction)){
			directions.add(instruction.direction);
		}else if(Direction.kBoth.equals(instruction.direction)){
			directions.add(Direction.kAncestor);
			directions.add(Direction.kDescendant);
		}else{
			throw new RuntimeException(
					"Unexpected direction: '" + instruction.direction + "'. Expected: Ancestor, Descendant or Both");
		}
		
		if(instruction.onlyLocal){
			instructionExecutor.getLineage(instruction);
			return originalSPADEQuery;
		}
		
		final Map<Direction, Map<AbstractVertex, Integer>> directionToNetworkToMinimumDepth = 
				new HashMap<Direction, Map<AbstractVertex, Integer>>();
		
		final Graph resultGraph = createNewGraph();
		
		for(final Direction direction : directions){
			final Graph directionGraph = createNewGraph();

			final Graph currentLevelVertices = createNewGraph();
			distinctifyGraph(currentLevelVertices, instruction.startGraph);

			int currentDepth = 1;
			
			final Graph nextLevelVertices = createNewGraph();
			final Graph adjacentGraph = createNewGraph();
			
			while(getGraphStats(currentLevelVertices).vertices > 0){
				if(currentDepth > instruction.depth){
					break;
				}else{
					clearGraph(adjacentGraph);
					instructionExecutor.getAdjacentVertex(
							new GetAdjacentVertex(adjacentGraph, instruction.subjectGraph, currentLevelVertices, direction));
					if(getGraphStats(adjacentGraph).vertices < 1){
						break;
					}else{
						// Get only new vertices
						// Get all the vertices in the adjacent graph
						// Remove all the current level vertices from it and that means we have the only
						// new vertices (i.e. next level)
						// Remove all the vertices which are already in the the result graph to avoid
						// doing duplicate work
						clearGraph(nextLevelVertices);
						unionGraph(nextLevelVertices, adjacentGraph);
						subtractGraph(nextLevelVertices, currentLevelVertices);
						subtractGraph(nextLevelVertices, directionGraph);
						
						clearGraph(currentLevelVertices);
						unionGraph(currentLevelVertices, nextLevelVertices);

						// Update the result graph after so that we don't remove all the relevant
						// vertices
						unionGraph(directionGraph, adjacentGraph);

						Set<AbstractVertex> networkVertices = getNetworkVertices(nextLevelVertices);
						if(!networkVertices.isEmpty()){
							for(AbstractVertex networkVertex : networkVertices){
								if(OPMConstants.isCompleteNetworkArtifact(networkVertex) // this is the 'abcdef' comment
										&& RemoteResolver.isRemoteAddressRemoteInNetworkVertex(networkVertex)
										&& !startGraphVertices.contains(networkVertex)
										&& Kernel.getHostName().equals(networkVertex.getAnnotation("host"))){ // only need to resolve local artifacts
									if(directionToNetworkToMinimumDepth.get(direction) == null){
										directionToNetworkToMinimumDepth.put(direction, new HashMap<AbstractVertex, Integer>());
									}
									if(directionToNetworkToMinimumDepth.get(direction).get(networkVertex) == null){
										directionToNetworkToMinimumDepth.get(direction).put(networkVertex, currentDepth);
									}
								}
							}
						}
						
						currentDepth++;
					}
				}
			}
			unionGraph(resultGraph, directionGraph);
		}
		
		////////////////////////////////////////////////////

		distinctifyGraph(instruction.targetGraph, resultGraph);

		// LOCAL query done by now
		final int clientPort = Settings.getCommandLineQueryPort();
		
		final ABE decrypter = new ABE();
		final boolean canDecrypt = decrypter.initialize(null);
		if(!canDecrypt){
			logger.log(Level.SEVERE, "Failed to initialize decryption module. All encrypted graphs will be discarded");
		}

		for(final Map.Entry<Direction, Map<AbstractVertex, Integer>> directionToNetworkToMinimumDepthEntry : directionToNetworkToMinimumDepth.entrySet()){
			final Direction direction = directionToNetworkToMinimumDepthEntry.getKey();
			final Map<AbstractVertex, Integer> networkToMinimumDepth = directionToNetworkToMinimumDepthEntry.getValue();
			for(final Map.Entry<AbstractVertex, Integer> networkToMinimumDepthEntry : networkToMinimumDepth.entrySet()){
				final AbstractVertex localNetworkVertex = networkToMinimumDepthEntry.getKey();
				final Integer localDepth = networkToMinimumDepthEntry.getValue();
				final Integer remoteDepth = instruction.depth - localDepth;
				final String remoteAddress = RemoteResolver.getRemoteAddress(localNetworkVertex);
				if(remoteDepth > 0){
					try(RemoteSPADEQueryConnection connection = new RemoteSPADEQueryConnection(Kernel.getHostName(), remoteAddress, clientPort)){
						connection.connect(Kernel.getClientSocketFactory(), 5*1000);
						final String remoteVertexPredicate = buildRemoteGetVertexPredicate(localNetworkVertex);
						final String remoteVerticesSymbol = connection.getBaseVertices(remoteVertexPredicate);
						final GraphStats remoteVerticesStats = connection.statGraph(remoteVerticesSymbol);
						if(remoteVerticesStats.vertices > 0){
							final String remoteLineageSymbol = connection.getBaseLineage(remoteVerticesSymbol, remoteDepth, direction);
							final GraphStats remoteLineageStats = connection.statGraph(remoteLineageSymbol);
							if(!remoteLineageStats.isEmpty()){
								spade.core.Graph remoteVerticesGraph = connection.exportGraph(remoteVerticesSymbol);
								spade.core.Graph remoteLineageGraph = connection.exportGraph(remoteLineageSymbol);
								String remoteHostNameInGraph = remoteLineageGraph.getHostName();
								// verification - done in export graph. if not verifiable then discarded
								if(remoteVerticesGraph.getClass().equals(spade.utility.ABEGraph.class)){
									if(!canDecrypt){
										throw new RuntimeException("Remote vertices graph for get lineage discarded. Invalid decryption module");
									}
									remoteVerticesGraph = decrypter.decryptGraph((spade.utility.ABEGraph)remoteVerticesGraph);
									if(remoteVerticesGraph == null){
										throw new RuntimeException("Failed to decrypt remote vertices graph for get lineage");
									}
									remoteVerticesGraph.setHostName(remoteHostNameInGraph);
								}
								if(remoteLineageGraph.getClass().equals(spade.utility.ABEGraph.class)){
									if(!canDecrypt){
										throw new RuntimeException("Remote get lineage graph for get lineage discarded. Invalid decryption module");
									}
									remoteLineageGraph = decrypter.decryptGraph((spade.utility.ABEGraph)remoteLineageGraph);
									if(remoteLineageGraph == null){
										throw new RuntimeException("Failed to decrypt remote lineage graph for get lineage");
									}
									remoteLineageGraph.setHostName(remoteHostNameInGraph);
								}
								// decryption - done above
								// discrepancy detection, and caching goes here.
								if(discrepancyDetector.doDiscrepancyDetection(
										remoteLineageGraph, new HashSet<AbstractVertex>(
												remoteVerticesGraph.vertexSet()), remoteDepth, direction, remoteHostNameInGraph)){
									spade.core.Graph patchedGraph = patchRemoteLineageGraph(localNetworkVertex, 
											remoteVerticesGraph, remoteLineageGraph);
									putGraph(instructionExecutor.getStorage(), instruction.targetGraph, patchedGraph);
								}else{
									throw new RuntimeException("Discrepancies found in result graph. Result discarded.");
								}
							}
						}
					}catch(Throwable t){
						logger.log(Level.SEVERE, "Failed to resolve remote get lineage for host: '"+remoteAddress+"'", t);
					}
				}
			}
		}
		
		return originalSPADEQuery;
		
	}
	//////////////////////////////////////////////////
	private spade.core.Graph patchRemoteLineageGraph(AbstractVertex localVertex,
			spade.core.Graph remoteVerticesGraph, spade.core.Graph remoteLineageGraph){
		Set<AbstractVertex> commonRemoteVertices = new HashSet<AbstractVertex>();
		
		commonRemoteVertices.addAll(remoteVerticesGraph.vertexSet());
		commonRemoteVertices.retainAll(remoteLineageGraph.vertexSet());
		
		Set<AbstractEdge> newEdges = new HashSet<AbstractEdge>();
		for(AbstractVertex remoteVertex : commonRemoteVertices){
			AbstractEdge e0 = new Edge(localVertex, remoteVertex);
			AbstractEdge e1 = new Edge(remoteVertex, localVertex);
			e0.addAnnotation(OPMConstants.TYPE, OPMConstants.WAS_DERIVED_FROM);
			e1.addAnnotation(OPMConstants.TYPE, OPMConstants.WAS_DERIVED_FROM);
			newEdges.add(e0);
			newEdges.add(e1);
		}
		
		spade.core.Graph patchedGraph = new spade.core.Graph();
		patchedGraph.vertexSet().addAll(remoteVerticesGraph.vertexSet());
		patchedGraph.vertexSet().addAll(remoteLineageGraph.vertexSet());
		patchedGraph.edgeSet().addAll(remoteLineageGraph.edgeSet());
		patchedGraph.edgeSet().addAll(newEdges);
		return patchedGraph;
	}
	
	private void putGraph(AbstractStorage storage, Graph getLineageTargetGraph, spade.core.Graph graph){
		final Set<String> vertexHashSet = new HashSet<String>();
		graph.vertexSet().forEach(v -> {vertexHashSet.add(v.bigHashCode());});
		final Set<String> edgeHashSet = new HashSet<String>();
		graph.edgeSet().forEach(e -> {edgeHashSet.add(e.bigHashCode());});
		
		final Graph verticesGraph = createNewGraph();
		final Graph edgesGraph = createNewGraph();
		
		instructionExecutor.insertLiteralVertex(new InsertLiteralVertex(verticesGraph, new ArrayList<String>(vertexHashSet)));
		instructionExecutor.insertLiteralEdge(new InsertLiteralEdge(edgesGraph, new ArrayList<String>(edgeHashSet)));
		
		final spade.core.Graph vertexGraph = exportGraph(verticesGraph);
		final spade.core.Graph edgeGraph = exportGraph(edgesGraph);
		
		final spade.core.Graph subgraphNotPresent = new spade.core.Graph();
		subgraphNotPresent.vertexSet().addAll(graph.vertexSet());
		subgraphNotPresent.edgeSet().addAll(graph.edgeSet());
		subgraphNotPresent.vertexSet().removeAll(vertexGraph.vertexSet());
		subgraphNotPresent.edgeSet().removeAll(edgeGraph.edgeSet());
		
		for(AbstractVertex vertex : subgraphNotPresent.vertexSet()){
			storage.putVertex(vertex);
		}
		
		for(AbstractEdge edge : subgraphNotPresent.edgeSet()){
			storage.putEdge(edge);
		}
		
		storage.flushTransactions(true);
		
		HelperFunctions.sleepSafe(50); // TODO
		
		// The following variables have all the vertices and edges now
		instructionExecutor.insertLiteralVertex(new InsertLiteralVertex(verticesGraph, new ArrayList<String>(vertexHashSet)));
		instructionExecutor.insertLiteralEdge(new InsertLiteralEdge(edgesGraph, new ArrayList<String>(edgeHashSet)));
		
		unionGraph(getLineageTargetGraph, verticesGraph);
		unionGraph(getLineageTargetGraph, edgesGraph);
	}
	
	//////////////////////////////////////////////////
	private String buildRemoteGetVertexPredicate(AbstractVertex localNetworkVertex){
		String predicate = "";
		predicate += formatQueryName(RemoteResolver.getAnnotationLocalAddress()) + "="
				+ formatQueryValue(RemoteResolver.getRemoteAddress(localNetworkVertex));
		predicate += " and ";
		predicate += formatQueryName(RemoteResolver.getAnnotationLocalPort()) + "="
				+ formatQueryValue(RemoteResolver.getRemotePort(localNetworkVertex));
		predicate += " and ";
		predicate += formatQueryName(RemoteResolver.getAnnotationRemoteAddress()) + "="
				+ formatQueryValue(RemoteResolver.getLocalAddress(localNetworkVertex));
		predicate += " and ";
		predicate += formatQueryName(RemoteResolver.getAnnotationRemotePort()) + "="
				+ formatQueryValue(RemoteResolver.getLocalPort(localNetworkVertex));
		return predicate;
	}

	private String formatQueryName(String name){
		return '"' + name + '"';
	}

	private String formatQueryValue(String name){
		return "'" + name + "'";
	}
	//////////////////////////////////////////////////
	
	private GraphStats getGraphStats(Graph graph){
		return instructionExecutor.statGraph(new StatGraph(graph));
	}

	private spade.core.Graph exportGraph(Graph graph){
		return exportGraph(new ExportGraph(graph, Format.kDot, true, null));
	}

	private Graph createNewGraph(){
		Graph newGraph = instructionExecutor.getQueryEnvironment().allocateGraph();
		instructionExecutor.createEmptyGraph(new CreateEmptyGraph(newGraph));
		return newGraph;
	}
	
	private void clearGraph(Graph graph){
		instructionExecutor.createEmptyGraph(new CreateEmptyGraph(graph));
	}

	private void unionGraph(Graph target, Graph source){
		instructionExecutor.unionGraph(new UnionGraph(target, source));
	}
	
	private void subtractGraph(Graph target, Graph source){
		instructionExecutor.subtractGraph(new SubtractGraph(target, target, source, null));
	}
	
	private void distinctifyGraph(Graph target, Graph source){
		instructionExecutor.distinctifyGraph(new DistinctifyGraph(target, source));
	}

	private Set<AbstractVertex> getNetworkVertices(Graph graph){
		Graph tempGraph = createNewGraph();
		instructionExecutor.getVertex(new GetVertex(tempGraph, graph, OPMConstants.ARTIFACT_SUBTYPE,
				PredicateOperator.EQUAL, OPMConstants.SUBTYPE_NETWORK_SOCKET));
		spade.core.Graph networkVerticesGraph = exportGraph(tempGraph);
		Set<AbstractVertex> networkVertices = new HashSet<AbstractVertex>();
		for(AbstractVertex networkVertex : networkVerticesGraph.vertexSet()){
			networkVertices.add(networkVertex);
		}
		return networkVertices;
	}

	//////////////////////////////////////////////////

}
