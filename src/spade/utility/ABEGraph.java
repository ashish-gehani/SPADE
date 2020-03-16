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
package spade.utility;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;
import spade.core.Vertex;

public class ABEGraph extends Graph
{
	private static final long serialVersionUID = 616956634821720644L;
	
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
		newVertex.addAnnotations(vertex.getCopyOfAnnotations());
		return newVertex;
	}

	public static AbstractEdge copyEdge(AbstractEdge edge)
	{
		AbstractEdge newEdge = new Edge(null, null);
		newEdge.setChildVertex(copyVertex(edge.getChildVertex()));
		newEdge.setParentVertex(copyVertex(edge.getParentVertex()));
		newEdge.addAnnotations(edge.getCopyOfAnnotations());
		return newEdge;
	}
}
