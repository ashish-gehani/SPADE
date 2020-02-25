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
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.query.quickgrail.core.Program;
import spade.query.quickgrail.core.QueryEnvironment;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.QueryInstructionExecutor.GraphStats;
import spade.query.quickgrail.core.Resolver;
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
import spade.query.quickgrail.instruction.SetGraphMetadata;
import spade.query.quickgrail.instruction.StatGraph;
import spade.query.quickgrail.instruction.SubtractGraph;
import spade.query.quickgrail.instruction.UnionGraph;
import spade.query.quickgrail.parser.DSLParserWrapper;
import spade.query.quickgrail.parser.ParseProgram;

/**
 * Top level class for the QuickGrail graph query executor.
 */
public class QuickGrailExecutor{

	private final Logger logger = Logger.getLogger(this.getClass().getName());
	
	private final static long exportGraphDumpLimit = 1024,
			exportGraphVisualizeLimit = 2048;
	
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

	public String execute(String query){
		ArrayList<Object> responses = new ArrayList<Object>();
		try{
			DSLParserWrapper parserWrapper = new DSLParserWrapper();
			ParseProgram parseProgram = parserWrapper.fromText(query);

			logger.log(Level.INFO, "Parse tree:\n" + parseProgram.toString());

			Resolver resolver = new Resolver();
			Program program = resolver.resolveProgram(parseProgram, queryEnvironment);

			logger.log(Level.INFO, "Execution plan:\n" + program.toString());

			try{
				int instructionsSize = program.getInstructionsSize();
				for(int i = 0; i < instructionsSize; i++){
					Object result = executeInstruction(program.getInstruction(i));
					if(result != null){
						responses.add(result);
					}
				}
			}finally{
				queryEnvironment.gc();
			}
		}catch(Exception e){
			responses = new ArrayList<Object>();
			StringWriter stackTrace = new StringWriter();
			PrintWriter pw = new PrintWriter(stackTrace);
			pw.println("Error evaluating QuickGrail command:");
			pw.println("------------------------------------------------------------");
			e.printStackTrace(pw);
			pw.println(e.getMessage());
			pw.println("------------------------------------------------------------");
			responses.add(stackTrace.toString());
			logger.log(Level.SEVERE, null, e);
		}

		if(responses == null || responses.isEmpty()){
			return "OK";
		}else{
			// Currently only return the last response.
			Object response = responses.get(responses.size() - 1);
			return response == null ? "" : response.toString();
		}
	}
	
	private Object executeInstruction(Instruction instruction){
		if(instruction.getClass().equals(ExportGraph.class)){
			return exportGraph((ExportGraph)instruction);
			
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
			return instructionExecutor.evaluateQuery((EvaluateQuery)instruction);
			
		}else if(instruction.getClass().equals(GetAdjacentVertex.class)){
			instructionExecutor.getAdjacentVertex((GetAdjacentVertex)instruction);
			
		}else if(instruction.getClass().equals(GetEdge.class)){
			instructionExecutor.getEdge((GetEdge)instruction);
			
		}else if(instruction.getClass().equals(GetEdgeEndpoint.class)){
			instructionExecutor.getEdgeEndpoint((GetEdgeEndpoint)instruction);
			
		}else if(instruction.getClass().equals(GetLineage.class)){
			instructionExecutor.getLineage((GetLineage)instruction);
			
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
			return instructionExecutor.listGraphs((ListGraphs)instruction);
			
		}else if(instruction.getClass().equals(OverwriteGraphMetadata.class)){
			instructionExecutor.overwriteGraphMetadata((OverwriteGraphMetadata)instruction);
			
		}else if(instruction.getClass().equals(SetGraphMetadata.class)){
			instructionExecutor.setGraphMetadata((SetGraphMetadata)instruction);
			
		}else if(instruction.getClass().equals(StatGraph.class)){
			return instructionExecutor.statGraph((StatGraph)instruction);
			
		}else if(instruction.getClass().equals(SubtractGraph.class)){
			instructionExecutor.subtractGraph((SubtractGraph)instruction);
			
		}else if(instruction.getClass().equals(UnionGraph.class)){
			instructionExecutor.unionGraph((UnionGraph)instruction);
			
		}else{
			throw new RuntimeException("Unhandled instruction: " + instruction.getClass());
		}
		return null;
	}
	
	private Object exportGraph(ExportGraph instruction){
		GraphStats stats = queryEnvironment.getGraphStats(instruction.targetGraph);
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
			return resultGraph.toString();//prettyPrint(); // TODO
		}else{
			return resultGraph.exportGraph();
		}
	}
}
