/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2012 SRI International

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
package spade.filter;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.utility.HelperFunctions;

/**
 * A filter to drop annotations passed in arguments.
 * 
 * Arguments format: keys=name,version,epoch
 * 
 * Note 1: Filter relies on two things for successful use of Java Reflection API:
 * 1) The vertex objects passed have an empty constructor
 * 2) The edge objects passed have a constructor with source vertex and a destination vertex (in that order)
 * 
 * Note 2: Creating copy of passed vertices and edges instead of just removing the annotations from the existing ones
 * because the passed vertices and edges might be in use by some other classes specifically the reporter that
 * generated them. In future, maybe shift the responsibility of creating a copy to Kernel before it sends out
 * the vertices and edges to filters.
 */
public class DropKeys extends AbstractFilter{

	private Logger logger = Logger.getLogger(this.getClass().getName());
	
	private Set<String> keysToDrop = new HashSet<String>(); 
	
	public boolean initialize(String arguments){
		
		//Must not be null or empty
		if(arguments == null || arguments.trim().isEmpty()){
			logger.log(Level.WARNING, "Must specify 'keys' argument");
			return false;
		}else{
				
			Map<String, String> argsMap = HelperFunctions.parseKeyValPairs(arguments);
			
			//Must have 'keys' argument
			if(argsMap.get("keys") == null){
				logger.log(Level.WARNING, "Must specify 'keys' argument. Invalid arguments");
				return false;
			}else{
				String keys = argsMap.get("keys");
				//'keys' argument must not be empty
				if(keys == null || keys.trim().isEmpty()){
					logger.log(Level.WARNING, "Must specify valid 'keys' argument value. Invalid arguments");
					return false;
				}else{
					String [] keyTokens = keys.split(",");
					for(String keyToken : keyTokens){
						if(keyToken.trim().isEmpty()){
							//Must be non-empty annotation key name
							logger.log(Level.WARNING, "Empty key in 'keys' argument. Invalid arguments");
							return false;
						}else{
							keyToken = keyToken.trim();
							//Must not be 'type'
							if(keyToken.equals("type")){
								logger.log(Level.WARNING, "Cannot remove 'type' key. Invalid arguments");
								return false;
							}else{
								keysToDrop.add(keyToken);
							}
						}
					}
				}
			}
		}
		
		return true;
		
	}
	
	@Override
	public void putVertex(AbstractVertex incomingVertex) {
		if(incomingVertex != null){
			AbstractVertex vertexCopy = createCopyWithoutKeys(incomingVertex, keysToDrop);
			if(vertexCopy != null){
				putInNextFilter(vertexCopy);
			}
		}else{
			logger.log(Level.WARNING, "Null vertex");
		}
	}

	@Override
	public void putEdge(AbstractEdge incomingEdge) {
		if(incomingEdge != null && incomingEdge.getChildVertex() != null && incomingEdge.getParentVertex() != null){
			AbstractEdge edgeCopy = createCopyWithoutKeys(incomingEdge, keysToDrop);
			if(edgeCopy != null){
				putInNextFilter(edgeCopy);
			}
		}else{
			logger.log(Level.WARNING, "Invalid edge: {0}, source: {1}, destination: {2}", new Object[]{
					incomingEdge, 
					incomingEdge == null ? null : incomingEdge.getChildVertex(),
					incomingEdge == null ? null : incomingEdge.getParentVertex()
			});
		}
	}
	
	/**
	 * Creates copy of vertex using Reflection API and removes the given keys afterwards
	 * 
	 * @param vertex vertex to create a copy of
	 * @param dropAnnotations annotations to remove
	 * @return copy of the vertex without the given annotations
	 */
	private AbstractVertex createCopyWithoutKeys(AbstractVertex vertex, Set<String> dropAnnotations){
		try{
			AbstractVertex vertexCopy = vertex.copyAsVertex();
			for(String dropAnnotation : dropAnnotations){
				vertexCopy.removeAnnotation(dropAnnotation);
			}
			return vertexCopy;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to initialize vertex: " + vertex, e);
			return null;
		}
	}
	
	/**
	 * Creates a deep copy of the edge (i.e. of source and destination vertices too) using Reflection API 
	 * and removes the given keys afterwards
	 * 
	 * @param edge edge to create a copy of
	 * @param dropAnnotations annotations to remove
	 * @return copy of the edge without the given annotations
	 */
	private AbstractEdge createCopyWithoutKeys(AbstractEdge edge, Set<String> dropAnnotations){
		try{
			AbstractVertex source = createCopyWithoutKeys(edge.getChildVertex(), dropAnnotations);
			AbstractVertex destination = createCopyWithoutKeys(edge.getParentVertex(), dropAnnotations);
			if(source == null){
				throw new Exception("Failed to create copy of source vertex");
			}
			if(destination == null){
				throw new Exception("Failed to create copy of destination vertex");
			}
			AbstractEdge edgeCopy = edge.getClass().getConstructor(edge.getChildVertex().getClass(),
					edge.getParentVertex().getClass()).newInstance(source, destination);
			edgeCopy.addAnnotations(edge.getCopyOfAnnotations());
			for(String dropAnnotation : dropAnnotations){
				edgeCopy.removeAnnotation(dropAnnotation);
			}
			return edgeCopy;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to initialize edge: " + edge, e);
			return null;
		}
	}
}
