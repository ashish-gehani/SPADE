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
package spade.filter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.edge.opm.Used;
import spade.edge.opm.WasGeneratedBy;
import spade.utility.HelperFunctions;
import spade.utility.LoadableField;
import spade.utility.LoadableFieldHelper;
import spade.vertex.opm.Artifact;

/**
 * Takes in argument 'key' whose value can be comma-separated.
 * 
 * 'key' used to tell the filter about the annotations unique to artifacts based on which to merge
 * consecutive reads, and writes.
 * 
 * Example: 'add filter IORuns position=1 key=path'
 * The above-mentioned command merges reads/writes to artifacts (files) which have a unique value for annotation 'path'
 * 
 * Example: 'add filter IORuns position=1 key="remote address,remote port,local address,local port"'
 * The above-mentioned command merges reads/writes to artifacts (network) which have a unique value for annotations
 * 'remote address', 'remote port', 'local address', 'local port'.
 * 
 * The filter is applied to only those artifacts which contain all the above-mentioned annotations specified in arguments
 * as 'key'.
 */
public class IORuns extends AbstractFilter {

	private static final Logger logger = Logger.getLogger(IORuns.class.getName());
	
	private static final String argNameArtifactKey = "key";
	
	@LoadableField(name=argNameArtifactKey, optional=false, splitBy=",")
	private final String[] artifactsKeysArray = null;
	
    private final Map<String, HashSet<String>> writes;
    private final Map<String, HashSet<String>> reads;
    private final Queue<AbstractVertex> vertexBuffer;

    public IORuns() {
        writes = new HashMap<>();
        reads = new HashMap<>();
        vertexBuffer = new LinkedList<>();
    }
    
    private boolean printGlobals(){
		try{
			String globalsString = LoadableFieldHelper.allLoadableFieldsToString(this);
			logger.log(Level.INFO, "Arguments: " + globalsString);
			return true;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to log globals", e);
			return false;
		}
	}
    
    private boolean initGlobals(String arguments){
		Map<String, String> globalsMap = null;
		try{
			globalsMap = HelperFunctions.getGlobalsMapFromConfigAndArguments(this.getClass(), arguments);
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to build globals map", e);
			return false;
		}
		
		try{
			LoadableFieldHelper.loadAllLoadableFieldsFromMap(this, globalsMap);
		}catch(Exception e){
			logger.log(Level.SEVERE, "Exception in initializing globals", e);
			return false;
		}
		
		for(String artifactsKey : artifactsKeysArray){
			if(artifactsKey.trim().isEmpty()){
				logger.log(Level.SEVERE, "Empty value for argument '"+argNameArtifactKey+"' in array: " +
						Arrays.asList(artifactsKeysArray));
				return false;
			}
		}
		
		if(printGlobals()){
			return true;
		}else{
			return false;
		}
	}
    
    @Override
    public boolean initialize(String arguments){
    	if(!initGlobals(arguments)){
			return false;
		}else{
			return true;
		}
    }
    
    private boolean artifactContainsAllArtifactKeys(AbstractVertex artifact){
    	for(String artifactKey : artifactsKeysArray){
    		if(artifact.getAnnotation(artifactKey) == null){
    			return false;
    		}
    	}
    	return true;
    }
    
    private String getArtifactKeysValues(AbstractVertex vertex){
    	String value = "";
    	for(String key : artifactsKeysArray){
    		value += vertex.getAnnotation(key) + ",";
    	}
    	return value;
    }
    
    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        if ((incomingVertex instanceof Artifact) && artifactContainsAllArtifactKeys(incomingVertex)) {
            vertexBuffer.add(incomingVertex);
        } else {
            putInNextFilter(incomingVertex);
        }
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
        if ((incomingEdge instanceof Used) && artifactContainsAllArtifactKeys(incomingEdge.getParentVertex())) {
            Used usedEdge = (Used) incomingEdge;
            String fileVertexHash = getArtifactKeysValues(usedEdge.getParentVertex());
            String processVertexHash = Integer.toString(usedEdge.getChildVertex().hashCode());
            if (!reads.containsKey(fileVertexHash)) {
                HashSet<String> tempSet = new HashSet<>();
                tempSet.add(processVertexHash);
                reads.put(fileVertexHash, tempSet);
            } else {
                HashSet<String> tempSet = reads.get(fileVertexHash);
                if (tempSet.contains(processVertexHash)) {
                    vertexBuffer.remove(usedEdge.getParentVertex());
                    return;
                } else {
                    tempSet.add(processVertexHash);
                }
            }
            vertexBuffer.remove(usedEdge.getParentVertex());
            putInNextFilter(usedEdge.getParentVertex());
            putInNextFilter(usedEdge);
            if (writes.containsKey(fileVertexHash)) {
                HashSet<String> tempSet = writes.get(fileVertexHash);
                tempSet.remove(processVertexHash);
            }
        } else if ((incomingEdge instanceof WasGeneratedBy) && artifactContainsAllArtifactKeys(incomingEdge.getChildVertex())){
            WasGeneratedBy wgb = (WasGeneratedBy) incomingEdge;
            String fileVertexHash = getArtifactKeysValues(wgb.getChildVertex());
            String processVertexHash = Integer.toString(wgb.getParentVertex().hashCode());
            if (!writes.containsKey(fileVertexHash)) {
                HashSet<String> tempSet = new HashSet<>();
                tempSet.add(processVertexHash);
                writes.put(fileVertexHash, tempSet);
            } else {
                HashSet<String> tempSet = writes.get(fileVertexHash);
                if (tempSet.contains(processVertexHash)) {
                    vertexBuffer.remove(wgb.getChildVertex());
                    return;
                } else {
                    tempSet.add(processVertexHash);
                }
            }
            vertexBuffer.remove(wgb.getChildVertex());
            putInNextFilter(wgb.getChildVertex());
            putInNextFilter(wgb);
            if (reads.containsKey(fileVertexHash)) {
                HashSet<String> tempSet = reads.get(fileVertexHash);
                tempSet.remove(processVertexHash);
            }
        } else {
            putInNextFilter(incomingEdge);
        }
    }

    @Override
    public boolean shutdown() {
        return true;
    }
}
