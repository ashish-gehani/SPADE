package spade.query.quickgrail.core;

import spade.query.quickgrail.core.QueryInstructionExecutor.GraphStats;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.entities.GraphMetadata;

public interface QueryEnvironment{

	public void gc();
	public void clear();
	public Graph allocateGraph();
	public GraphMetadata allocateGraphMetadata();
	public Graph lookupGraphSymbol(String symbol);
	public GraphMetadata lookupGraphMetadataSymbol(String symbol);
	public void setGraphSymbol(String symbol, Graph graph);
	public void setGraphMetadataSymbol(String symbol, GraphMetadata graphMetadata);
	public void eraseGraphSymbol(String symbol);
	public void eraseGraphMetadataSymbol(String symbol);
	public boolean isBaseGraph(Graph graph);
	public GraphStats getGraphStats(Graph graph);
	
}
