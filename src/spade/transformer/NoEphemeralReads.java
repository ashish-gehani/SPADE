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
import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.core.Settings;
import spade.reporter.audit.OPMConstants;
import spade.utility.CommonFunctions;
import spade.utility.FileUtility;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NoEphemeralReads extends AbstractTransformer
{
	
	private Pattern ignoreFilesPattern = null;
	
	// limited = true means that only matching files in the graph should be checked for ephemeral reads.
	// limited = false means that all files in the graph should be checked for ephemeral reads.
	public boolean initialize(String arguments)
	{
		Map<String, String> argumentsMap = CommonFunctions.parseKeyValPairs(arguments);
		if("false".equals(argumentsMap.get("limited")))
		{
			return true;
		}
		else
		{
			try
			{
				String filepath = Settings.getDefaultConfigFilePath(this.getClass());
				ignoreFilesPattern = FileUtility.constructRegexFromFile(filepath);
				if(ignoreFilesPattern == null)
				{
					throw new Exception("Regex read from file '"+filepath+"' cannot be null");
				}
				return true;
			}
			catch(Exception e)
			{
				Logger.getLogger(getClass().getName()).log(Level.WARNING, null, e);
				return false;
			}
		}
	}

	public Graph putGraph(Graph graph, QueryMetaData queryMetaData)
	{
		AbstractVertex queriedVertex = null;
		if(queryMetaData != null)
		{
			queriedVertex = queryMetaData.getRootVertex();
		}
		
		Map<AbstractVertex, Set<String>> fileWrittenBy = new HashMap<>();
		
		for(AbstractEdge edge : graph.edgeSet())
		{
			AbstractEdge newEdge = createNewWithoutAnnotations(edge);
			if(OPMConstants.isPathBasedArtifact(newEdge.getChildVertex())
					|| OPMConstants.isPathBasedArtifact(newEdge.getParentVertex()))
			{
				String operation = getAnnotationSafe(newEdge, OPMConstants.EDGE_OPERATION);
				if(OPMConstants.isOutgoingDataOperation(operation))
				{
					if(fileWrittenBy.get(newEdge.getChildVertex()) == null)
					{
						fileWrittenBy.put(newEdge.getChildVertex(), new HashSet<>());
					}
					fileWrittenBy.get(newEdge.getChildVertex()).add(
							getAnnotationSafe(newEdge.getParentVertex(), OPMConstants.PROCESS_PID));
				}
			}
		}
		
		Graph resultGraph = new Graph();
		
		for(AbstractEdge edge : graph.edgeSet())
		{
			AbstractEdge newEdge = createNewWithoutAnnotations(edge);
			if(OPMConstants.isPathBasedArtifact(newEdge.getParentVertex()) &&
					OPMConstants.isIncomingDataOperation(getAnnotationSafe(newEdge, OPMConstants.EDGE_OPERATION)))
			{
				AbstractVertex vertex = newEdge.getParentVertex();
				String path = getAnnotationSafe(vertex, OPMConstants.ARTIFACT_PATH);
				//if file passed as an argument then always log it otherwise check further
				if(!pathEqualsVertex(path, queriedVertex))
				{
					//if file is not in ignore list then always log it otherwise check further
					if(isPathInIgnoreFilesPattern(path))
					{
						if((fileWrittenBy.get(vertex) == null) || (fileWrittenBy.get(vertex).size() == 1
								&& fileWrittenBy.get(vertex).toArray()[0].equals(
										getAnnotationSafe(newEdge.getChildVertex(), OPMConstants.PROCESS_PID))))
						{
							continue;
						}
					}
				}
			}
		
			resultGraph.putVertex(newEdge.getChildVertex());
			resultGraph.putVertex(newEdge.getParentVertex());
			resultGraph.putEdge(newEdge);			
		}
		
		return resultGraph;
		
	}
	
	private boolean pathEqualsVertex(String path, AbstractVertex vertex)
	{
		if(path == null || vertex == null)
		{
			return false;
		}
		if(OPMConstants.isPathBasedArtifact(vertex))
		{
			String vpath = getAnnotationSafe(vertex, OPMConstants.ARTIFACT_PATH);
			if(path.equals(vpath))
			{
				return true;
			}
		}
		return false;
	}
	
	private boolean isPathInIgnoreFilesPattern(String path)
	{
		if(ignoreFilesPattern == null)
		{
			return true;
		}
		if(path != null)
		{
			Matcher filepathMatcher = ignoreFilesPattern.matcher(path);
			return filepathMatcher.find();
		}

		return false;
	}
}
