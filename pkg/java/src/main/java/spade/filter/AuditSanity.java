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

import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.utility.Result;
import spade.utility.map.external.ExternalMap;
import spade.utility.map.external.ExternalMapArgument;
import spade.utility.map.external.ExternalMapManager;

/**
 * Can be used to check:
 * 1) If Audit reporter outputs a duplicate vertex
 * 2) If Audit reporter outputs an edge whose endpoints haven't been seen
 */
public class AuditSanity extends AbstractFilter{

	private Logger logger = Logger.getLogger(this.getClass().getName());
	
	private final String vertexMapId = "AuditSanityMap";
	private ExternalMap<AbstractVertex, Integer> vertexMap;
	
	public boolean initialize(String arguments){
		String defaultConfigFilePath = Settings.getDefaultConfigFilePath(this.getClass());

		Result<ExternalMapArgument> externalMapArgumentResult = ExternalMapManager.parseArgumentFromFile(vertexMapId, defaultConfigFilePath);
		if(externalMapArgumentResult.error){
			logger.log(Level.SEVERE, "Failed to parse argument for external map: '"+vertexMapId+"'");
			logger.log(Level.SEVERE, externalMapArgumentResult.toErrorString());
			return false;
		}else{
			ExternalMapArgument externalMapArgument = externalMapArgumentResult.result;
			Result<ExternalMap<AbstractVertex, Integer>> externalMapResult = ExternalMapManager.create(externalMapArgument);
			if(externalMapResult.error){
				logger.log(Level.SEVERE, "Failed to create external map '"+vertexMapId+"' from arguments: " + externalMapArgument);
				logger.log(Level.SEVERE, externalMapResult.toErrorString());
				return false;
			}else{
				logger.log(Level.INFO, vertexMapId + ": " + externalMapArgument);
				vertexMap = externalMapResult.result;
				return true;
			}
		}
	}
	
	public boolean shutdown(){
		if(vertexMap != null){
			vertexMap.close();
			vertexMap = null;
		}
		return true;
	}
	
	@Override
	public void putVertex(AbstractVertex incomingVertex) {
		if(vertexMap.get(incomingVertex) != null){
			logger.log(Level.WARNING, "Duplicate vertex: " + incomingVertex);
		}else{
			vertexMap.put(incomingVertex, 1);
		}
		putInNextFilter(incomingVertex);
	}

	@Override
	public void putEdge(AbstractEdge incomingEdge) {
		AbstractVertex source = incomingEdge.getChildVertex();
		AbstractVertex destination = incomingEdge.getParentVertex();
		
		Integer sourceExists = vertexMap.get(source);
		Integer destinationExists = vertexMap.get(destination);
		
		if(sourceExists == null && destinationExists == null){
			logger.log(Level.WARNING, "Missing source and destination vertices: (" + source + ") -> [" + incomingEdge + "] -> (" + destination + ")");
		}else if(sourceExists == null && destinationExists != null){
			logger.log(Level.WARNING, "Missing source vertex: (" + source + ") -> [" + incomingEdge + "] -> (" + destination + ")");
		}else if(sourceExists != null && destinationExists == null){
			logger.log(Level.WARNING, "Missing destination vertex: (" + source + ") -> [" + incomingEdge + "] -> (" + destination + ")");
		}
		putInNextFilter(incomingEdge);
	}
	
}
