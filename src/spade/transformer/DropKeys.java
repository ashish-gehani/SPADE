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

import org.apache.commons.io.FileUtils;
import spade.client.QueryMetaData;
import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.core.Settings;
import spade.utility.CommonFunctions;

import java.io.File;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DropKeys extends AbstractTransformer
{
	
	private final static Logger logger = Logger.getLogger(DropKeys.class.getName());
	
	private String[] annotationsToRemove = null;
	
	//argument can either be a file which contains an annotation per line OR arguments can be comma separated annotation names. If neither then read the default config file
	public boolean initialize(String arguments)
	{
		try
		{
			Map<String, String> argumentsMap = CommonFunctions.parseKeyValPairs(arguments);
			boolean doDefaultAction = true;
			if(argumentsMap != null)
			{
				String filepath = argumentsMap.get("config");
				if(filepath != null)
				{
					if(new File(filepath).exists())
					{
						annotationsToRemove = FileUtils.readLines(new File(filepath)).toArray(new String[]{});
						doDefaultAction = false;
					}
					else
					{
						logger.log(Level.SEVERE, "Must give a valid config filepath");
						return false;
					}
				}
				else
				{
					String commaSeparatedKeys = argumentsMap.get("keys");
					if(commaSeparatedKeys != null)
					{
						annotationsToRemove = commaSeparatedKeys.split(",");
						for(int a = 0; a<annotationsToRemove.length; a++)
						{
							annotationsToRemove[a] = annotationsToRemove[a].trim();
						}
						doDefaultAction = false;
					}
				}
			}
		
			if(doDefaultAction)
			{
				String defaultConfigFilePath = Settings.getDefaultConfigFilePath(this.getClass());
				annotationsToRemove = FileUtils.readLines(new File(defaultConfigFilePath)).toArray(new String[]{});
			}
		
			return true;
		}
		catch(Exception e)
		{
			logger.log(Level.SEVERE, null, e);

			return false;
		}
	}

	public Graph putGraph(Graph graph, QueryMetaData queryMetaData)
	{
		Graph resultGraph = new Graph();
	
		for(AbstractVertex vertex : graph.vertexSet())
		{
			AbstractVertex newVertex = createNewWithoutAnnotations(vertex, annotationsToRemove);
			resultGraph.putVertex(newVertex);
		}
		
		for(AbstractEdge edge : graph.edgeSet())
		{
			AbstractEdge newEdge = createNewWithoutAnnotations(edge, annotationsToRemove);
			resultGraph.putEdge(newEdge);
		}			

		return resultGraph;
	}
}
