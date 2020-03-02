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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.SPADEQuery;
import spade.core.SPADEQuery.QuickGrailInstruction;
import spade.query.quickgrail.core.GraphStats;
import spade.query.quickgrail.core.Program;
import spade.query.quickgrail.core.QueryEnvironment;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.QuickGrailQueryResolver;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.instruction.CollapseEdge;
import spade.query.quickgrail.instruction.CreateEmptyGraph;
import spade.query.quickgrail.instruction.CreateEmptyGraphMetadata;
import spade.query.quickgrail.instruction.DistinctifyGraph;
import spade.query.quickgrail.instruction.EraseSymbols;
import spade.query.quickgrail.instruction.EvaluateQuery;
import spade.query.quickgrail.instruction.ExportGraph;
import spade.query.quickgrail.instruction.ExportGraph.Format;
import spade.query.quickgrail.instruction.GetAdjacentVertex;
import spade.query.quickgrail.instruction.GetEdge;
import spade.query.quickgrail.instruction.GetEdgeEndpoint;
import spade.query.quickgrail.instruction.GetLineage;
import spade.query.quickgrail.instruction.GetLink;
import spade.query.quickgrail.instruction.GetPath;
import spade.query.quickgrail.instruction.GetShortestPath;
import spade.query.quickgrail.instruction.GetSubgraph;
import spade.query.quickgrail.instruction.GetVertex;
import spade.query.quickgrail.instruction.InsertLiteralEdge;
import spade.query.quickgrail.instruction.InsertLiteralVertex;
import spade.query.quickgrail.instruction.Instruction;
import spade.query.quickgrail.instruction.IntersectGraph;
import spade.query.quickgrail.instruction.LimitGraph;
import spade.query.quickgrail.instruction.ListGraphs;
import spade.query.quickgrail.instruction.OverwriteGraphMetadata;
import spade.query.quickgrail.instruction.PrintPredicate;
import spade.query.quickgrail.instruction.SetGraphMetadata;
import spade.query.quickgrail.instruction.StatGraph;
import spade.query.quickgrail.instruction.SubtractGraph;
import spade.query.quickgrail.instruction.UnionGraph;
import spade.query.quickgrail.parser.DSLParserWrapper;
import spade.query.quickgrail.parser.ParseProgram;
import spade.query.quickgrail.parser.ParseStatement;
import spade.query.quickgrail.types.LongType;
import spade.query.quickgrail.types.StringType;
import spade.query.quickgrail.utility.QuickGrailPredicateTree.PredicateNode;
import spade.query.quickgrail.utility.ResultTable;
import spade.query.quickgrail.utility.Schema;

/**
 * Top level class for the QuickGrail graph query executor.
 */
public class QuickGrailExecutor{

	private final Logger logger = Logger.getLogger(this.getClass().getName());
	
	private final static long 
		exportGraphDumpLimit = 4096,
		exportGraphVisualizeLimit = 4096;
	
	private final QueryEnvironment queryEnvironment;
	private final QueryInstructionExecutor instructionExecutor;

	public QuickGrailExecutor(QueryInstructionExecutor instructionExecutor){
		this.instructionExecutor = instructionExecutor;
		if(this.instructionExecutor == null){
			throw new IllegalArgumentException("NULL instruction executor");
		}
		this.queryEnvironment = instructionExecutor.getQueryEnvironment();
		if(this.queryEnvironment == null){
			throw new IllegalArgumentException("NULL variable manager");
		}
	}
	
	public static void main(String [] args) throws Exception{
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		
		System.out.print("-> ");
		
		String line = null;
		while((line = reader.readLine()) != null){
			if(line.trim().equalsIgnoreCase("quit") ||
					line.trim().equalsIgnoreCase("exit")){
				break;
			}
			try{
				DSLParserWrapper parserWrapper = new DSLParserWrapper();
				ParseProgram parseProgram = parserWrapper.fromText(line);
				System.out.println(parseProgram);
//				for(ParseStatement statement : parseProgram.getStatements()){
//					System.out.println(statement);
//					ParseAssignment assign = (ParseAssignment)statement;
					//System.out.println(QuickGrailPredicateTree.resolveGraphPredicate(assign.getRhs()));
//					ParseOperation rhs = (ParseOperation)assign.getRhs();
//					System.out.println(rhs);
					
//					ArrayList<ParseExpression> operands = rhs.getOperands();
//					for(ParseExpression operand : operands){
//						System.out.println(operand);
//					}
//				}
				
				QuickGrailQueryResolver resolver = new QuickGrailQueryResolver();
				System.out.println();
				Program program = resolver.resolveProgram(parseProgram, null);
				System.out.println();
				System.out.println(program);
//				System.out.println(parseProgram);
			}catch(Exception e){
				e.printStackTrace();
			}
			System.out.println();
			System.out.print("-> ");
		}
		
		reader.close();
	}// java -jar ../../../../../lib/antlr-4.7-complete.jar DSL.g4
//$x = $base.getVertex(a = 'b' or "c" like 'ha' and d < 10)
	public SPADEQuery execute(SPADEQuery query){
		try{
			DSLParserWrapper parserWrapper = new DSLParserWrapper();
			
			query.setQueryParsingStartedAtMillis();
			
			ParseProgram parseProgram = parserWrapper.fromText(query.query);
			
			if(parseProgram.getStatements().size() > 1){
				throw new RuntimeException("Only 1 query allowed as of now. Found " + parseProgram.getStatements().size() + " statements.");
				// The query environment modified at statement resolution time which can cause issues when variables repeated.
				// Create instructions to update query environment.
			}
			
			final List<String> parsedProgramStatements = new ArrayList<String>();
			for(ParseStatement statement : parseProgram.getStatements()){
				parsedProgramStatements.add(String.valueOf(statement));
			}
			query.setQueryParsingCompletedAtMillis(parsedProgramStatements);
			
			logger.log(Level.INFO, "Parse tree:\n" + parseProgram.toString());

			query.setQueryInstructionResolutionStartedAtMillis();
			
			QuickGrailQueryResolver resolver = new QuickGrailQueryResolver();
			Program program = resolver.resolveProgram(parseProgram, queryEnvironment);

			query.setQueryInstructionResolutionCompletedAtMillis();
			
			logger.log(Level.INFO, "Execution plan:\n" + program.toString());

			for(int i = 0; i < program.getInstructionsSize(); i++){
				query.addQuickGrailInstruction(new QuickGrailInstruction(String.valueOf(program.getInstruction(i))));
			}

			try{
				query.setQueryExecutionStartedAtMillis();
				
				List<QuickGrailInstruction> queryInstructions = query.getQuickGrailInstructions();
				int instructionsSize = program.getInstructionsSize();
				for(int i = 0; i < instructionsSize; i++){
					Instruction executableInstruction = program.getInstruction(i);
					QuickGrailInstruction queryInstruction = queryInstructions.get(i);
					try{
						queryInstruction.setStartedAtMillis();
						
						query = executeInstruction(executableInstruction, query, queryInstruction);
						
						queryInstruction.setCompletedAtMillis();
					}catch(Exception e){
						queryInstruction.instructionFailed(new Exception("Instruction failed! " + e.getMessage(), e));
						throw e;
					}
				}
				
			}finally{
				queryEnvironment.gc();
				query.setQueryExecutionCompletedAtMillis();
			}
			
			// Only here if success
			if(query.getQuickGrailInstructions().isEmpty()){
				query.querySucceeded("OK");
			}else{
				// Currently only return the last response.
				Serializable lastResult = 
						query.getQuickGrailInstructions()
						.get(query.getQuickGrailInstructions().size() - 1)
						.getResult();
				query.querySucceeded(lastResult);
			}
			return query;
		}catch(Exception e){
			logger.log(Level.SEVERE, null, e);
			
			StringWriter stackTrace = new StringWriter();
			PrintWriter pw = new PrintWriter(stackTrace);
			pw.println("Error evaluating QuickGrail command:");
			pw.println("------------------------------------------------------------");
			e.printStackTrace(pw);
			pw.println(e.getMessage());
			pw.println("------------------------------------------------------------");

			query.queryFailed(new Exception(stackTrace.toString(), e));
			return query;
		}
	}
	
	// Have to set queryinstruction success
	private SPADEQuery executeInstruction(Instruction instruction, SPADEQuery query, 
			QuickGrailInstruction queryInstruction) throws Exception{
		
		Serializable result = "OK"; // default result
		
		if(instruction.getClass().equals(ExportGraph.class)){
			result = exportGraph((ExportGraph)instruction);
			
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
			instructionExecutor.getLineage(getLineage);
//			if(getLineage.remoteResolve){ TODO
//				RemoteResolver.main(instructionExecutor, getLineage, query);
//			}
			
		}else if(instruction.getClass().equals(GetLink.class)){
			instructionExecutor.getLink((GetLink)instruction);
			
		}else if(instruction.getClass().equals(GetPath.class)){
			instructionExecutor.getPath((GetPath)instruction);
			
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
			
		}else if(instruction.getClass().equals(ListGraphs.class)){
			ResultTable table = listGraphs((ListGraphs)instruction);
			if(table == null){
				result = "No Result!";
			}else{
				result = String.valueOf(table);
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
				result = String.valueOf(stats);
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
			
		}else{
			throw new RuntimeException("Unhandled instruction: " + instruction.getClass());
		}
		
		queryInstruction.instructionSucceeded(result);
		return query;
	}
	
	private Serializable exportGraph(ExportGraph instruction) throws Exception{
		GraphStats stats = instructionExecutor.statGraph(new StatGraph(instruction.targetGraph));
		long verticesAndEdges = stats.vertices + stats.edges;
		if(!instruction.force){
			if(instruction.format == Format.kNormal && verticesAndEdges > exportGraphDumpLimit){
				return "Dump export limit set at '"+exportGraphDumpLimit+"'. Total vertices and edges requested '"+verticesAndEdges+"'."
						+ "Please use 'force dump ...' to force the print.";
			}else if(instruction.format == Format.kDot && verticesAndEdges > exportGraphVisualizeLimit){
				return "Dot export limit set at '"+exportGraphVisualizeLimit+"'. Total vertices and edges requested '"+verticesAndEdges+"'."
						+ "Please use 'force visualize ...' to force the transfer.";
			}
		}
		spade.core.Graph resultGraph = instructionExecutor.exportGraph(instruction);
		if(instruction.format == Format.kNormal){
			return resultGraph.prettyPrint();
		}else{
			return resultGraph.exportGraphUnsafe();
		}
	}
	
	private ResultTable listGraphs(ListGraphs instruction){
		Map<String, GraphStats> graphsMap = instructionExecutor.listGraphs(instruction);
		
		List<String> sortedNonBaseSymbolNames = new ArrayList<String>();
		sortedNonBaseSymbolNames.addAll(graphsMap.keySet());
		sortedNonBaseSymbolNames.remove(queryEnvironment.getBaseSymbolName());
		Collections.sort(sortedNonBaseSymbolNames);
		
		ResultTable table = new ResultTable();
		for(String symbolName : sortedNonBaseSymbolNames){
			GraphStats graphStats = graphsMap.get(symbolName);
			ResultTable.Row row = new ResultTable.Row();
			row.add(symbolName);
			row.add(graphStats.vertices);
			row.add(graphStats.edges);
			table.addRow(row);
		}
		
		// Add base last
		GraphStats graphStats = graphsMap.get(queryEnvironment.getBaseSymbolName());
		ResultTable.Row row = new ResultTable.Row();
		row.add(queryEnvironment.getBaseSymbolName());
		row.add(graphStats.vertices);
		row.add(graphStats.edges);
		table.addRow(row);
		
		Schema schema = new Schema();
		schema.addColumn("Graph Name", StringType.GetInstance());
		if(!instruction.style.equals("name")){
			schema.addColumn("Number of Vertices", LongType.GetInstance());
			schema.addColumn("Number of Edges", LongType.GetInstance());
		}
		table.setSchema(schema);
		
		return table;
	}

	// TODO
	private void getPath(GetPath instruction){
		Graph ancestorsOfFromGraph = queryEnvironment.allocateGraph();
		instructionExecutor.createEmptyGraph(new CreateEmptyGraph(ancestorsOfFromGraph));
		
		Graph descendantsOfToGraph = queryEnvironment.allocateGraph();
		instructionExecutor.createEmptyGraph(new CreateEmptyGraph(descendantsOfToGraph));
		
		getLineage(new GetLineage(ancestorsOfFromGraph, 
					instruction.subjectGraph, instruction.srcGraph, 
					instruction.maxDepth, GetLineage.Direction.kAncestor, false));
		
		getLineage(new GetLineage(descendantsOfToGraph, 
				instruction.subjectGraph, instruction.dstGraph, 
				instruction.maxDepth, GetLineage.Direction.kDescendant, false));
		
		Graph intersectionGraph = queryEnvironment.allocateGraph();
		instructionExecutor.createEmptyGraph(new CreateEmptyGraph(intersectionGraph));
		
		instructionExecutor.intersectGraph(new IntersectGraph(intersectionGraph, ancestorsOfFromGraph, descendantsOfToGraph));
		
		Graph fromGraphInIntersection = queryEnvironment.allocateGraph();
		instructionExecutor.createEmptyGraph(new CreateEmptyGraph(fromGraphInIntersection));
		
		instructionExecutor.intersectGraph(new IntersectGraph(fromGraphInIntersection, intersectionGraph, instruction.srcGraph));
		
		Graph toGraphInIntersection = queryEnvironment.allocateGraph();
		instructionExecutor.createEmptyGraph(new CreateEmptyGraph(toGraphInIntersection));
		
		instructionExecutor.intersectGraph(new IntersectGraph(toGraphInIntersection, intersectionGraph, instruction.dstGraph));
		
		if(!instructionExecutor.statGraph(new StatGraph(fromGraphInIntersection)).isEmpty()
				&& !instructionExecutor.statGraph(new StatGraph(toGraphInIntersection)).isEmpty()){
			instructionExecutor.unionGraph(new UnionGraph(instruction.targetGraph, intersectionGraph)); // means we found a path
		}
	}
	
	private void getLineage(GetLineage instruction){
		Graph startingGraph = queryEnvironment.allocateGraph();
		instructionExecutor.createEmptyGraph(new CreateEmptyGraph(startingGraph));
		
		instructionExecutor.distinctifyGraph(new DistinctifyGraph(startingGraph, instruction.startGraph));
		
		int maxDepth = instruction.depth;
		
		Graph tempGraph = null;
		
		Graph distinctTempStartingGraph = queryEnvironment.allocateGraph(); // temp variable
		instructionExecutor.createEmptyGraph(new CreateEmptyGraph(distinctTempStartingGraph)); // create the tables for temp
		
		GraphStats graphStats = instructionExecutor.statGraph(new StatGraph(startingGraph));
		while(!graphStats.isEmpty()){
			if(maxDepth <= 0){
				break;
			}else{
				if(tempGraph == null){
					tempGraph = queryEnvironment.allocateGraph();
				}
				instructionExecutor.createEmptyGraph(new CreateEmptyGraph(tempGraph));
				
				instructionExecutor.getAdjacentVertex(
						new GetAdjacentVertex(
						tempGraph, instruction.subjectGraph, 
						startingGraph, 
						instruction.direction));
				
				if(instructionExecutor.statGraph(new StatGraph(tempGraph)).isEmpty()){
					break;
				}else{
					instructionExecutor.unionGraph(new UnionGraph(startingGraph, tempGraph));
					maxDepth--;
					// distinctify graph
					
					instructionExecutor.distinctifyGraph(new DistinctifyGraph(distinctTempStartingGraph, startingGraph)); // get uniq in the temp variable
					instructionExecutor.createEmptyGraph(new CreateEmptyGraph(startingGraph)); // clear the starting graph
					instructionExecutor.unionGraph(new UnionGraph(startingGraph, distinctTempStartingGraph)); // have the updated starting graph
					instructionExecutor.createEmptyGraph(new CreateEmptyGraph(distinctTempStartingGraph)); // create the tables for temp
				}
			}
		}
		// Don't need to distinctify since going to happen afterwards anyway
		instructionExecutor.unionGraph(new UnionGraph(instruction.targetGraph, startingGraph));
	}
}
