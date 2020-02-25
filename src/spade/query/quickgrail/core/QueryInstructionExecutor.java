package spade.query.quickgrail.core;

import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.instruction.CollapseEdge;
import spade.query.quickgrail.instruction.CreateEmptyGraph;
import spade.query.quickgrail.instruction.CreateEmptyGraphMetadata;
import spade.query.quickgrail.instruction.DistinctifyGraph;
import spade.query.quickgrail.instruction.EraseSymbols;
import spade.query.quickgrail.instruction.EvaluateQuery;
import spade.query.quickgrail.instruction.ExportGraph;
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
import spade.query.quickgrail.instruction.IntersectGraph;
import spade.query.quickgrail.instruction.LimitGraph;
import spade.query.quickgrail.instruction.ListGraphs;
import spade.query.quickgrail.instruction.OverwriteGraphMetadata;
import spade.query.quickgrail.instruction.SetGraphMetadata;
import spade.query.quickgrail.instruction.StatGraph;
import spade.query.quickgrail.instruction.SubtractGraph;
import spade.query.quickgrail.instruction.UnionGraph;
import spade.query.quickgrail.utility.ResultTable;

public abstract class QueryInstructionExecutor{
	
	public abstract QueryEnvironment getQueryEnvironment();
	
	//////////////////////////////////////////////////////////////
	public abstract void insertLiteralEdge(InsertLiteralEdge instruction);
	public abstract void insertLiteralVertex(InsertLiteralVertex instruction);
	//////////////////////////////////////////////////////////////
	
	//////////////////////////////////////////////////////////////
	// METADATA
	public abstract void createEmptyGraphMetadata(CreateEmptyGraphMetadata instruction);
	public abstract void overwriteGraphMetadata(OverwriteGraphMetadata instruction);
	public abstract void setGraphMetadata(SetGraphMetadata instruction);
	// METADATA
	//////////////////////////////////////////////////////////////
	
	//////////////////////////////////////////////////////////////
	// MUST-HAVES
	public abstract void createEmptyGraph(CreateEmptyGraph instruction);
	public abstract void distinctifyGraph(DistinctifyGraph instruction);
	public abstract void eraseSymbols(EraseSymbols instruction);
	public abstract void getVertex(GetVertex instruction);
	public abstract ResultTable evaluateQuery(EvaluateQuery instruction);
	public abstract void getEdge(GetEdge instruction);
	public abstract void getEdgeEndpoint(GetEdgeEndpoint instruction);
	public abstract void intersectGraph(IntersectGraph instruction);
	public abstract void limitGraph(LimitGraph instruction);
	public abstract ResultTable listGraphs(ListGraphs instruction);
	public abstract GraphStats statGraph(StatGraph instruction);
	public abstract void subtractGraph(SubtractGraph instruction);
	public abstract void unionGraph(UnionGraph instruction);
	public abstract void getAdjacentVertex(GetAdjacentVertex instruction);
	
	// MUST-HAVES
	//////////////////////////////////////////////////////////////
	
	private final GraphStats safeStatGraph(Graph g){
		GraphStats stats = statGraph(new StatGraph(g));
		if(stats == null){
			stats = new GraphStats(0, 0);
		}
		return stats;
	}
	
	//////////////////////////////////////////////////////////////
	// special
	public abstract spade.core.Graph exportGraph(ExportGraph instruction);
	public void collapseEdge(CollapseEdge instruction){} // TODO
	//////////////////////////////////////////////////////////////
	// composite operations
	public abstract void getLink(GetLink instruction);
	public abstract void getShortestPath(GetShortestPath instruction);
	public abstract void getSubgraph(GetSubgraph instruction);
	// composite operations
	//////////////////////////////////////////////////////////////
	
	public void getPath(GetPath instruction){
		QueryEnvironment queryEnvironment = getQueryEnvironment();
		
		Graph ancestorsOfFromGraph = queryEnvironment.allocateGraph();
		createEmptyGraph(new CreateEmptyGraph(ancestorsOfFromGraph));
		
		Graph descendantsOfToGraph = queryEnvironment.allocateGraph();
		createEmptyGraph(new CreateEmptyGraph(descendantsOfToGraph));
		
		getLineage(new GetLineage(ancestorsOfFromGraph, 
					instruction.subjectGraph, instruction.srcGraph, 
					instruction.maxDepth, GetLineage.Direction.kAncestor));
		
		getLineage(new GetLineage(descendantsOfToGraph, 
				instruction.subjectGraph, instruction.dstGraph, 
				instruction.maxDepth, GetLineage.Direction.kDescendant));
		
		Graph intersectionGraph = queryEnvironment.allocateGraph();
		createEmptyGraph(new CreateEmptyGraph(intersectionGraph));
		
		intersectGraph(new IntersectGraph(intersectionGraph, ancestorsOfFromGraph, descendantsOfToGraph));
		
		Graph fromGraphInIntersection = queryEnvironment.allocateGraph();
		createEmptyGraph(new CreateEmptyGraph(fromGraphInIntersection));
		
		intersectGraph(new IntersectGraph(fromGraphInIntersection, intersectionGraph, instruction.srcGraph));
		
		Graph toGraphInIntersection = queryEnvironment.allocateGraph();
		createEmptyGraph(new CreateEmptyGraph(toGraphInIntersection));
		
		intersectGraph(new IntersectGraph(toGraphInIntersection, intersectionGraph, instruction.dstGraph));
		
		if(!queryEnvironment.getGraphStats(fromGraphInIntersection).isEmpty()
				&& !queryEnvironment.getGraphStats(toGraphInIntersection).isEmpty()){
			unionGraph(new UnionGraph(instruction.targetGraph, intersectionGraph)); // means we found a path
		}
	}
	
	public void getLineage(GetLineage instruction){
		Graph startingGraph = getQueryEnvironment().allocateGraph();
		createEmptyGraph(new CreateEmptyGraph(startingGraph));
		
		distinctifyGraph(new DistinctifyGraph(startingGraph, instruction.startGraph));
		
		int maxDepth = instruction.depth;
		
		Graph tempGraph = null;
		
		Graph distinctTempStartingGraph = getQueryEnvironment().allocateGraph(); // temp variable
		createEmptyGraph(new CreateEmptyGraph(distinctTempStartingGraph)); // create the tables for temp
		
		GraphStats graphStats = safeStatGraph(startingGraph);
		while(!graphStats.isEmpty()){
			if(maxDepth <= 0){
				break;
			}else{
				if(tempGraph == null){
					tempGraph = getQueryEnvironment().allocateGraph();
				}
				createEmptyGraph(new CreateEmptyGraph(tempGraph));
				
				getAdjacentVertex(
						new GetAdjacentVertex(
						tempGraph, instruction.subjectGraph, 
						startingGraph, 
						instruction.direction));
				
				if(safeStatGraph(tempGraph).isEmpty()){
					break;
				}else{
					unionGraph(new UnionGraph(startingGraph, tempGraph));
					maxDepth--;
					// distinctify graph
					
					distinctifyGraph(new DistinctifyGraph(distinctTempStartingGraph, startingGraph)); // get uniq in the temp variable
					createEmptyGraph(new CreateEmptyGraph(startingGraph)); // clear the starting graph
					unionGraph(new UnionGraph(startingGraph, distinctTempStartingGraph)); // have the updated starting graph
					createEmptyGraph(new CreateEmptyGraph(distinctTempStartingGraph)); // create the tables for temp
				}
			}
		}
		// Don't need to distinctify since going to happen afterwards anyway
		unionGraph(new UnionGraph(instruction.targetGraph, startingGraph));
	}
	
	//////////////////////////////////////////////////////////////
	public static final class GraphStats{
		public final long vertices, edges;
		public GraphStats(long vertices, long edges){
			this.vertices = vertices;
			this.edges = edges;
		}
		public boolean isEmpty(){
			return vertices == 0 && edges == 0;
		}
		@Override
		public int hashCode(){
			final int prime = 31;
			int result = 1;
			result = prime * result + (int)(edges ^ (edges >>> 32));
			result = prime * result + (int)(vertices ^ (vertices >>> 32));
			return result;
		}
		@Override
		public boolean equals(Object obj){
			if(this == obj) return true;
			if(obj == null) return false;
			if(getClass() != obj.getClass()) return false;
			GraphStats other = (GraphStats)obj;
			if(edges != other.edges) return false;
			if(vertices != other.vertices) return false;
			return true;
		}
		@Override
		public String toString(){
			return "GraphStats [vertices=" + vertices + ", edges=" + edges + "]";
		}
	}
	//////////////////////////////////////////////////////////////
}
