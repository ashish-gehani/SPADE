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

import spade.client.QueryMetaData;
import spade.core.AbstractTransformer;
import spade.core.Graph;
import spade.utility.CommonFunctions;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Prune extends AbstractTransformer
{
	
	private static final Logger logger = Logger.getLogger(Prune.class.getName());
	
	private String startingHash;
	
	public boolean initialize(String arguments)
	{
		// startingHash can possibly replace vertexExpression in the new world?
		Map<String, String> argumentsMap = CommonFunctions.parseKeyValPairs(arguments);
		if(argumentsMap.get("startingHash") == null || argumentsMap.get("startingHash").trim().isEmpty())
		{
			logger.log(Level.SEVERE, "Must specify a starting Hash for vertex selection");
			return false;
		}
		startingHash = argumentsMap.get("startingHash");
		return true;
	}

	@Override
	public Graph putGraph(Graph graph, QueryMetaData queryMetaData)
    {
		try
        {
			if(queryMetaData.getMaxLength() == null)
			{
				throw new IllegalArgumentException("Depth cannot be null");
			}
			if(queryMetaData.getDirection() == null)
			{
				throw new IllegalArgumentException("Direction cannot be null");
			}
			
		}
		catch(Exception e)
        {
			logger.log(Level.WARNING, "Missing arguments for the current query", e);

            return graph;
		}
		
		Graph resultGraph = new Graph();
		
		Graph toRemoveGraph = graph.getLineage(this.startingHash, queryMetaData.getDirection(), queryMetaData.getMaxLength());
		
		if(toRemoveGraph != null)
		{
			removeEdges(resultGraph, graph, toRemoveGraph);

            return resultGraph;
		}
		else
        {
			return graph;
		}
	}
}
