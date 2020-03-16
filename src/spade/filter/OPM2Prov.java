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

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Settings;
import spade.core.Vertex;
import spade.utility.FileUtility;

public class OPM2Prov extends OPM2ProvVertexEdge{

	private final static Logger logger = Logger.getLogger(OPM2Prov.class.getName());

	private Map<String, String> annotationConversionMap = null;

	public boolean initialize(String arguments){
		String filepath = Settings.getDefaultConfigFilePath(this.getClass());
		try{
			annotationConversionMap = FileUtility.readOPM2ProvTCFile(filepath);
			return true;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to read the file: " + filepath, e);
			return false;
		}
	}

	@Override
	public void putVertex(AbstractVertex incomingVertex) {
		if(incomingVertex != null){
			AbstractVertex vertexCopy = new Vertex();
			Map<String, String> annotationsCopy = incomingVertex.getCopyOfAnnotations();
			convertAnnotationsInMap(annotationsCopy);
			vertexCopy.addAnnotations(annotationsCopy);
			super.putVertex(vertexCopy);
		}else{
			super.putVertex(incomingVertex);
		}
	}

	@Override
	public void putEdge(AbstractEdge incomingEdge) {
		if(incomingEdge != null){
			AbstractEdge edgeCopy = new Edge(incomingEdge.getChildVertex(), incomingEdge.getParentVertex());
			Map<String, String> annotationsCopy = incomingEdge.getCopyOfAnnotations();
			convertAnnotationsInMap(annotationsCopy);
			edgeCopy.addAnnotations(annotationsCopy);
			super.putEdge(incomingEdge);
		}else{
			super.putEdge(incomingEdge);
		}
	}

	private void convertAnnotationsInMap(Map<String, String> map){
		for(String fromKey : annotationConversionMap.keySet()){
			String toKey = annotationConversionMap.get(fromKey);
			if(map.containsKey(fromKey)){
				String value = map.get(fromKey);
				map.remove(fromKey);
				map.put(toKey, value);
			}
		}
	}

}
