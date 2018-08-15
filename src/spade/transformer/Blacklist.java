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
import spade.utility.FileUtility;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class Blacklist extends AbstractTransformer
{
	
	private Pattern filesToRemovePattern = null;
	
	public boolean initialize(String arguments)
	{
		
		try
		{
			String filepath = Settings.getDefaultConfigFilePath(this.getClass());
			filesToRemovePattern = FileUtility.constructRegexFromFile(filepath);
			if(filesToRemovePattern == null)
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
	
	public Graph putGraph(Graph graph, QueryMetaData queryMetaData)
	{
		Graph resultGraph = new Graph();
		
		AbstractVertex queriedVertex = null;
		
		if(queryMetaData != null)
		{
			queriedVertex = queryMetaData.getRootVertex();
		}
		
		for(AbstractEdge edge : graph.edgeSet())
		{
			String srcFilepath = getAnnotationSafe(edge.getChildVertex(), OPMConstants.ARTIFACT_PATH);
			String dstFilepath = getAnnotationSafe(edge.getParentVertex(), OPMConstants.ARTIFACT_PATH);
			if(!(fileEqualsVertex(srcFilepath, queriedVertex) || fileEqualsVertex(dstFilepath, queriedVertex)))
			{
				if(isFileToBeRemoved(srcFilepath) || isFileToBeRemoved(dstFilepath))
				{
					continue;
				}
			}
			AbstractEdge newEdge = createNewWithoutAnnotations(edge);
			if(newEdge != null && newEdge.getChildVertex() != null && newEdge.getParentVertex() != null)
			{
				resultGraph.putVertex(newEdge.getChildVertex());
				resultGraph.putVertex(newEdge.getParentVertex());
				resultGraph.putEdge(newEdge);
			}
		}

		return resultGraph;
	}
	
	private boolean isFileToBeRemoved(String path)
	{
		if(filesToRemovePattern != null && path != null)
		{
			return filesToRemovePattern.matcher(path).find();
		}

		return false;
	}
	
	private boolean fileEqualsVertex(String path, AbstractVertex vertex)
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
}
