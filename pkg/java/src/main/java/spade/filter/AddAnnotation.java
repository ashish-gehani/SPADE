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
package spade.filter;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Settings;
import spade.core.Vertex;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;

public class AddAnnotation extends AbstractFilter{

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private Map<String, String> newAnnotations = new HashMap<String, String>();

	// arguments -> anno_key1=anno_value1 anno_key2=anno_value2 ...
	public boolean initialize(String arguments){
		Map<String, String> argsMap = HelperFunctions.parseKeyValPairs(arguments);
		Map<String, String> configMap = null;
		try{
			configMap = FileUtility.readConfigFileAsKeyValueMap(Settings.getDefaultConfigFilePath(this.getClass()),
					"=");
			if(configMap == null){
				throw new Exception("NULL config map read");
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to read config file", e);
			return false;
		}

		newAnnotations.putAll(configMap); // first
		newAnnotations.putAll(argsMap); // overwrite

		if(newAnnotations.isEmpty()){
			logger.log(Level.WARNING, "No key-value pairs specified");
			return false;
		}

		logger.log(Level.INFO, "Arguments: New annotations: [" + newAnnotations + "]");
		return true;
	}

	@Override
	public void putVertex(AbstractVertex incomingVertex){
		if(incomingVertex != null){
			AbstractVertex vertexCopy = new Vertex();
			Map<String, String> annotationsCopy = incomingVertex.getCopyOfAnnotations();
			annotationsCopy.putAll(newAnnotations);
			vertexCopy.addAnnotations(annotationsCopy);
			putInNextFilter(vertexCopy);
		}else{
			logger.log(Level.WARNING, "Null vertex");
		}
	}

	@Override
	public void putEdge(AbstractEdge incomingEdge){
		if(incomingEdge != null && incomingEdge.getChildVertex() != null && incomingEdge.getParentVertex() != null){
			AbstractVertex childCopy = new Vertex();
			Map<String, String> annotationsCopy = incomingEdge.getChildVertex().getCopyOfAnnotations();
			annotationsCopy.putAll(newAnnotations);
			childCopy.addAnnotations(annotationsCopy);

			AbstractVertex parentCopy = new Vertex();
			annotationsCopy = incomingEdge.getParentVertex().getCopyOfAnnotations();
			annotationsCopy.putAll(newAnnotations);
			parentCopy.addAnnotations(annotationsCopy);

			AbstractEdge edgeCopy = new Edge(childCopy, parentCopy);
			annotationsCopy = incomingEdge.getCopyOfAnnotations();
			annotationsCopy.putAll(newAnnotations);
			edgeCopy.addAnnotations(annotationsCopy);

			putInNextFilter(edgeCopy);
		}else{
			logger.log(Level.WARNING, "Invalid edge: {0}, source: {1}, destination: {2}",
					new Object[]{incomingEdge, incomingEdge == null ? null : incomingEdge.getChildVertex(),
							incomingEdge == null ? null : incomingEdge.getParentVertex()});
		}
	}
}
