package spade.utility;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.core.Vertex;
import spade.core.Edge;

import java.util.HashMap;
import java.util.Map;

public class ABEGraph extends Graph
{
	private String lowKey;
	private String mediumKey;
	private String highKey;

	public String getLowKey()
	{
		return lowKey;
	}

	public void setLowKey(String lowKey)
	{
		this.lowKey = lowKey;
	}

	public String getMediumKey()
	{
		return mediumKey;
	}

	public void setMediumKey(String mediumKey)
	{
		this.mediumKey = mediumKey;
	}

	public String getHighKey()
	{
		return highKey;
	}

	public void setHighKey(String highKey)
	{
		this.highKey = highKey;
	}

	public static ABEGraph copy(Graph graph)
	{
		Map<String, AbstractVertex> vertexMap = new HashMap<>();
		ABEGraph newGraph = new ABEGraph();
		for(AbstractVertex vertex : graph.vertexSet())
		{
			AbstractVertex newVertex = copyVertex(vertex);
			newGraph.putVertex(newVertex);
			vertexMap.put(newVertex.bigHashCode(), newVertex);
		}
		for(AbstractEdge edge : graph.edgeSet())
		{
			AbstractEdge newEdge = copyEdge(edge);
			newEdge.setChildVertex(vertexMap.get(edge.getChildVertex().bigHashCode()));
			newEdge.setParentVertex(vertexMap.get(edge.getParentVertex().bigHashCode()));
			newGraph.putEdge(newEdge);
		}
		return newGraph;
	}

	public static AbstractVertex copyVertex(AbstractVertex vertex)
	{
		AbstractVertex newVertex = new Vertex();
		newVertex.addAnnotations(vertex.getAnnotations());
		newVertex.setDepth(vertex.getDepth());
		return newVertex;
	}

	public static AbstractEdge copyEdge(AbstractEdge edge)
	{
		AbstractEdge newEdge = new Edge(null, null);
		newEdge.addAnnotations(edge.getAnnotations());
		return newEdge;
	}
}
