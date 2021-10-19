/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2017 SRI International

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

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.utility.ArgumentFunctions;
import spade.utility.HelperFunctions;

/*
 * Source: https://github.com/mathbarre/SPADE/blob/master/src/spade/filter/AttributeLabel.java
 */
public class AttributeLabel extends AbstractFilter{

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private final String
		keyMaliciousProcessNames = "malicious",
		keyProcessNameAnnotation = "processNameAnnotation",
		keyLabelAnnotation = "labelAnnotation",
		keyMaliciousLabel = "maliciousLabel",
		keyBenignLabel = "benignLabel";

	private List<String> maliciousProcessNames;
	private String processNameAnnotation;
	private String labelAnnotation;
	private String maliciousLabel;
	private String benignLabel;

	public boolean initialize(String arguments){
		try{
			final Map<String, String> configMap = HelperFunctions.parseKeyValuePairsFrom(arguments, 
					new String[]{Settings.getDefaultConfigFilePath(this.getClass())}
			);
			maliciousProcessNames = ArgumentFunctions.mustParseCommaSeparatedValues(keyMaliciousProcessNames, configMap);
			maliciousProcessNames = ArgumentFunctions.allValuesMustBeNonEmpty(maliciousProcessNames, keyMaliciousProcessNames);

			processNameAnnotation = ArgumentFunctions.mustParseNonEmptyString(keyProcessNameAnnotation, configMap);
			labelAnnotation = ArgumentFunctions.mustParseNonEmptyString(keyLabelAnnotation, configMap);
			maliciousLabel = ArgumentFunctions.mustParseNonEmptyString(keyMaliciousLabel, configMap);
			benignLabel = ArgumentFunctions.mustParseNonEmptyString(keyBenignLabel, configMap);

			logger.log(Level.INFO, 
					"AttributeLabel ["
					+ "maliciousProcessNames="+maliciousProcessNames+""
					+ ", processNameAnnotation=" + processNameAnnotation
					+ ", labelAnnotation=" + labelAnnotation
					+ ", maliciousLabel=" + maliciousLabel
					+ ", benignLabel=" + benignLabel
					+ "]");
			return true;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to parse arguments/configuration file", e);
			return false;
		}
	}

	private void updateVertexConditionally(final AbstractVertex vertex){
		if(vertex == null){
			return;
		}
		if(!spade.vertex.opm.Process.typeValue.equals(vertex.type())){
			return;
		}
		final String vertexProcessName = vertex.getAnnotation(processNameAnnotation);
		if(vertexProcessName == null){
			return;
		}
		final String vertexLabel;
		if(maliciousProcessNames.contains(vertexProcessName)){
			vertexLabel = maliciousLabel;
		}else{
			vertexLabel = benignLabel;
		}
		vertex.addAnnotation(labelAnnotation, vertexLabel);
	}

	@Override
	public void putVertex(final AbstractVertex vertex){
		putInNextFilter(vertex);
		updateVertexConditionally(vertex);
	}

	@Override
	public void putEdge(AbstractEdge edge){
		putInNextFilter(edge);

		if(edge == null || edge.getChildVertex() == null || edge.getParentVertex() == null){
			return;
		}

		final AbstractVertex parentVertex = edge.getParentVertex();
		updateVertexConditionally(parentVertex);

		// taint the child
		if(maliciousLabel.equals(parentVertex.getAnnotation(labelAnnotation))){
			final AbstractVertex childVertex = edge.getChildVertex();
			childVertex.addAnnotation(labelAnnotation, maliciousLabel);
		}
		
	}

}
