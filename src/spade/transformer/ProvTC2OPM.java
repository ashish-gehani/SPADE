/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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

package spade.transformer;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.client.QueryMetaData;
import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.core.Settings;
import spade.utility.FileUtility;

public class ProvTC2OPM extends Prov2OPM
{
	private static final Logger logger = Logger.getLogger(ProvTC2OPM.class.getName());

	private Map<String, String> provTC2OpmMapping = null;

	public boolean initialize(String arguments)
	{
		String filepath = Settings.getDefaultConfigFilePath(this.getClass());
		try
		{
			provTC2OpmMapping = FileUtility.readOPM2ProvTCFileReversed(filepath);
			return true;
		}
		catch(Exception e)
		{
			logger.log(Level.SEVERE, "Failed to read the file: " + filepath, e);
			return false;
		}
	}

	public Graph transform(Graph graph, QueryMetaData queryMetaData)
	{
		graph = super.transform(graph, queryMetaData);

		Graph resultGraph = new Graph();

		for(AbstractEdge edge : graph.edgeSet())
		{
			if(edge != null && edge.getChildVertex() != null && edge.getParentVertex() != null)
			{
				AbstractEdge newEdge = createNewWithoutAnnotations(edge);
				replaceAnnotations(newEdge, provTC2OpmMapping);
				replaceAnnotations(newEdge.getChildVertex(), provTC2OpmMapping);
				replaceAnnotations(newEdge.getParentVertex(), provTC2OpmMapping);
				resultGraph.putVertex(newEdge.getChildVertex());
				resultGraph.putVertex(newEdge.getParentVertex());
				resultGraph.putEdge(newEdge);
			}
		}

		return resultGraph;
	}

	private void replaceAnnotations(AbstractVertex vertex, Map<String, String> newMapping){
		for(String annotation : vertex.getCopyOfAnnotations().keySet()){
			if(newMapping.get(annotation) != null){
				vertex.addAnnotation(newMapping.get(annotation), vertex.getAnnotation(annotation));
				vertex.removeAnnotation(annotation);
			}
		}
	}

	private void replaceAnnotations(AbstractEdge edge, Map<String, String> newMapping){
		for(String annotation : edge.getCopyOfAnnotations().keySet()){
			if(newMapping.get(annotation) != null){
				edge.addAnnotation(newMapping.get(annotation), edge.getAnnotation(annotation));
				edge.removeAnnotation(annotation);
			}
		}
	}
}
