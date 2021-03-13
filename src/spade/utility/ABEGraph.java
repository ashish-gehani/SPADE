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

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;
import spade.core.Vertex;
import spade.reporter.audit.OPMConstants;

public class ABEGraph extends Graph
{
	private static final long serialVersionUID = -6200790370474776849L;
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

	/*
	Converts unix timestamp into human readable time of format
	'yyyy-MM-dd HH:mm:ss.SSS'
	 */
	private static void convertUnixTime(AbstractEdge edge)
	{
		String unixTime = edge.getAnnotation(OPMConstants.EDGE_TIME);
		Date date = new Date(Double.valueOf(Double.parseDouble(unixTime) * 1000).longValue());
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		String year = String.valueOf(calendar.get(Calendar.YEAR));
		String month = String.valueOf(calendar.get(Calendar.MONTH) + 1); // zero-based indexing
		String day = String.valueOf(calendar.get(Calendar.DAY_OF_MONTH));
		String hour = String.valueOf(calendar.get(Calendar.HOUR_OF_DAY));
		String minute = String.valueOf(calendar.get(Calendar.MINUTE));
		String second = String.valueOf(calendar.get(Calendar.SECOND));
		String millisecond = String.valueOf(calendar.get(Calendar.MILLISECOND));

		// stitch time with format is 'yyyy-MM-dd HH:mm:ss.SSS'
		String timestamp = year + "-" + month + "-" + day + " " + hour + ":" +
				minute + ":" + second + "." + millisecond;
		edge.addAnnotation(OPMConstants.EDGE_TIME, timestamp);
	}

	public static ABEGraph copy(Graph graph, boolean convertUnixTime)
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
			if(convertUnixTime)
				convertUnixTime(newEdge);
			newEdge.setChildVertex(vertexMap.get(edge.getChildVertex().bigHashCode()));
			newEdge.setParentVertex(vertexMap.get(edge.getParentVertex().bigHashCode()));
			newGraph.putEdge(newEdge);
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
		newEdge.addAnnotations(edge.getCopyOfAnnotations());
		return newEdge;
	}
}
