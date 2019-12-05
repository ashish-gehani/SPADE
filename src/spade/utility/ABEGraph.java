package spade.utility;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.core.Vertex;
import spade.core.Edge;

public class ABEGraph extends Graph
{
	private String level;
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

	public void setLevel(String level)
	{
		this.level = level;
	}

	public String getLevel()
	{
		return level;
	}

	public static ABEGraph copy(Graph graph)
	{
		ABEGraph newGraph = new ABEGraph();
		for(AbstractVertex vertex : graph.vertexSet())
		{
			newGraph.putVertex(copyVertex(vertex));
		}
		for(AbstractEdge edge : graph.edgeSet())
		{
			newGraph.putEdge(copyEdge(edge));
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
		newEdge.setChildVertex(copyVertex(edge.getChildVertex()));
		newEdge.setParentVertex(copyVertex(edge.getParentVertex()));
		newEdge.addAnnotations(edge.getAnnotations());

		return newEdge;
	}
}
