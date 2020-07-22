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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractStorage;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.instruction.CollapseEdge;
import spade.query.quickgrail.instruction.CreateEmptyGraph;
import spade.query.quickgrail.instruction.CreateEmptyGraphMetadata;
import spade.query.quickgrail.instruction.DescribeGraph;
import spade.query.quickgrail.instruction.DistinctifyGraph;
import spade.query.quickgrail.instruction.EraseSymbols;
import spade.query.quickgrail.instruction.EvaluateQuery;
import spade.query.quickgrail.instruction.ExportGraph;
import spade.query.quickgrail.instruction.GetAdjacentVertex;
import spade.query.quickgrail.instruction.GetEdge;
import spade.query.quickgrail.instruction.GetEdgeEndpoint;
import spade.query.quickgrail.instruction.GetLineage;
import spade.query.quickgrail.instruction.GetLink;
import spade.query.quickgrail.instruction.GetMatch;
import spade.query.quickgrail.instruction.GetShortestPath;
import spade.query.quickgrail.instruction.GetSimplePath;
import spade.query.quickgrail.instruction.GetSubgraph;
import spade.query.quickgrail.instruction.GetVertex;
import spade.query.quickgrail.instruction.GetWhereAnnotationsExist;
import spade.query.quickgrail.instruction.InsertLiteralEdge;
import spade.query.quickgrail.instruction.InsertLiteralVertex;
import spade.query.quickgrail.instruction.IntersectGraph;
import spade.query.quickgrail.instruction.LimitGraph;
import spade.query.quickgrail.instruction.OverwriteGraphMetadata;
import spade.query.quickgrail.instruction.PrintPredicate;
import spade.query.quickgrail.instruction.SetGraphMetadata;
import spade.query.quickgrail.instruction.StatGraph;
import spade.query.quickgrail.instruction.SubtractGraph;
import spade.query.quickgrail.instruction.UnionGraph;
import spade.query.quickgrail.utility.QuickGrailPredicateTree.PredicateNode;
import spade.query.quickgrail.utility.ResultTable;

public abstract class QueryInstructionExecutor{
	
	private static final Logger logger = Logger.getLogger(QueryInstructionExecutor.class.getName());
	
	public abstract AbstractQueryEnvironment getQueryEnvironment();
	public abstract AbstractStorage getStorage();
	
	//////////////////////////////////////////////////////////////
	// METADATA
	public abstract void createEmptyGraphMetadata(CreateEmptyGraphMetadata instruction);
	public abstract void overwriteGraphMetadata(OverwriteGraphMetadata instruction);
	public abstract void setGraphMetadata(SetGraphMetadata instruction);
	// METADATA
	//////////////////////////////////////////////////////////////
	
	//////////////////////////////////////////////////////////////
	// MUST-HAVES
	public abstract void insertLiteralEdge(InsertLiteralEdge instruction);
	public abstract void insertLiteralVertex(InsertLiteralVertex instruction);
	public abstract void createEmptyGraph(CreateEmptyGraph instruction);
	public abstract void distinctifyGraph(DistinctifyGraph instruction);
	public abstract void getVertex(GetVertex instruction);
	public abstract ResultTable evaluateQuery(EvaluateQuery instruction);
	public abstract void getEdge(GetEdge instruction);
	public abstract void getEdgeEndpoint(GetEdgeEndpoint instruction);
	public abstract void intersectGraph(IntersectGraph instruction);
	public abstract void limitGraph(LimitGraph instruction);
	
	public abstract void getMatch(GetMatch instruction);
	
	public final void eraseSymbols(EraseSymbols instruction){
		for(String symbol : instruction.getSymbols()){
			getQueryEnvironment().removeSymbol(symbol);
		}
	}
	
	public final Map<String, GraphStats> listGraphs(spade.query.quickgrail.instruction.List instruction){
		Map<String, GraphStats> allGraphStats = new HashMap<String, GraphStats>();
		Set<String> allGraphSymbolNames = getQueryEnvironment().getCurrentGraphSymbolsStringMap().keySet();
		for(String graphSymbol : allGraphSymbolNames){
			Graph graph = getQueryEnvironment().getGraphSymbol(graphSymbol);
			try{
				GraphStats stats = statGraph(new StatGraph(graph));
				allGraphStats.put(graphSymbol, stats);
			}catch(RuntimeException e){
				logger.log(Level.SEVERE, "Failed to stat graph: " + graphSymbol + ". Skipped.", e);
			}	
		}
		String baseSymbol = getQueryEnvironment().getBaseGraphSymbol();
		Graph baseGraph = getQueryEnvironment().getBaseGraph();
		GraphStats stats = statGraph(new StatGraph(baseGraph));
		allGraphStats.put(baseSymbol, stats);
		return allGraphStats;
	}
	
	public final PredicateNode printPredicate(PrintPredicate instruction){
		return instruction.predicateRoot;
	} 
	
	public abstract void getWhereAnnotationsExist(GetWhereAnnotationsExist instruction);
	public abstract GraphDescription describeGraph(DescribeGraph instruction);
	public abstract GraphStats statGraph(StatGraph instruction);
	public abstract void subtractGraph(SubtractGraph instruction);
	public abstract void unionGraph(UnionGraph instruction);
	public abstract void getAdjacentVertex(GetAdjacentVertex instruction);
	// MUST-HAVES
	//////////////////////////////////////////////////////////////
	
	//////////////////////////////////////////////////////////////
	// special
	public abstract Map<String, Map<String, String>> exportVertices(ExportGraph instruction);
	public abstract Set<QueriedEdge> exportEdges(ExportGraph instruction);
	
	public abstract void collapseEdge(CollapseEdge instruction);
	//////////////////////////////////////////////////////////////
	// composite operations
	public abstract void getLink(GetLink instruction);
	public abstract void getShortestPath(GetShortestPath instruction);
	public abstract void getSubgraph(GetSubgraph instruction);
	public abstract void getLineage(GetLineage instruction);
	public abstract void getPath(GetSimplePath instruction);
	// composite operations
	//////////////////////////////////////////////////////////////

	public final Graph createNewGraph(){
		Graph newGraph = getQueryEnvironment().allocateGraph();
		createEmptyGraph(new CreateEmptyGraph(newGraph));
		return newGraph;
	}

}
