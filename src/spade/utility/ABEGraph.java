package spade.utility;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.core.Vertex;
import spade.core.Edge;
import spade.reporter.audit.OPMConstants;

import java.util.Calendar;
import java.util.Date;
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
