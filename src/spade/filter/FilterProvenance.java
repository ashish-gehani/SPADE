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
 * Source: https://github.com/mathbarre/SPADE/blob/master/src/spade/filter/ExcludeVertex.java
 */
public class FilterProvenance extends AbstractFilter{

	private final Logger logger = Logger.getLogger(this.getClass().getName());
	
	private final String keyAnnotation = "annotation";

	private String annotationName, annotationValue;

	public boolean initialize(String arguments){ // arguments of the form key:value
		try{
			final Map<String, String> configMap = HelperFunctions.parseKeyValuePairsFrom(arguments, 
					new String[]{Settings.getDefaultConfigFilePath(this.getClass())}
			);
			final String annotation = ArgumentFunctions.mustParseNonEmptyString(keyAnnotation, configMap);
			final String[] tokens = annotation.split(":", 2);
			if(tokens.length != 2){
				throw new Exception("Value of '" + keyAnnotation + "' must be in format: 'a:b'");
			}
			annotationName = tokens[0].trim();
			annotationValue = tokens[1].trim();
			logger.log(Level.INFO, "FilterProvenance [annotationName="+annotationName+", annotationValue="+annotationValue+"]");
			return true;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to parse arguments/configuration file", e);
			return false;
		}
	}

	@Override
	public void putVertex(AbstractVertex vertex){
		if(vertex == null){
			return;
		}
		if(!filterVertex(vertex)){
			putInNextFilter(vertex);
		}
	}

	@Override
	public void putEdge(AbstractEdge edge){
		if(edge == null || edge.getChildVertex() == null || edge.getParentVertex() == null){
			return;
		}

		if(!filterEdge(edge)){
			putInNextFilter(edge);
		}
	}

	private boolean filterVertex(final AbstractVertex vertex){
		return annotationValue.equals(vertex.getAnnotation(annotationName));
	}

	private boolean filterEdge(final AbstractEdge edge){
		return filterVertex(edge.getChildVertex())
				|| filterVertex(edge.getParentVertex())
				|| annotationValue.equals(edge.getAnnotation(annotationName));
	}

}
