/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2022 SRI International

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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Settings;
import spade.utility.ArgumentFunctions;
import spade.utility.HelperFunctions;

public class ConvertTime extends AbstractFilter{

	private static final Logger logger = Logger.getLogger(ConvertTime.class.getName());
	
	private static final String 
		keyNameInVertex = "nameInVertex",
		keyPatternInVertex = "patternInVertex",
		keyNewNameInVertex = "newNameInVertex",
		keyNewPatternInVertex = "newPatternInVertex",
		keyNameInEdge = "nameInEdge",
		keyPatternInEdge = "patternInEdge",
		keyNewNameInEdge = "newNameInEdge",
		keyNewPatternInEdge = "newPatternInEdge";

	private String nameInVertex;
	private DateTimeFormatter patternInVertex;
	private String newNameInVertex;
	private DateTimeFormatter newPatternInVertex;
	private String nameInEdge;
	private DateTimeFormatter patternInEdge;
	private String newNameInEdge;
	private DateTimeFormatter newPatternInEdge;
	
	@Override
	public boolean initialize(final String arguments){
		try{
			final Map<String, String> map = HelperFunctions.parseKeyValuePairsFrom(arguments,
					new String[]{Settings.getDefaultConfigFilePath(this.getClass())});

			String nameInVertex = null;
			DateTimeFormatter patternInVertex = null;
			String newNameInVertex = null;
			DateTimeFormatter newPatternInVertex = null;
			if(map.containsKey(keyNameInVertex)){
				nameInVertex = ArgumentFunctions.mustParseNonEmptyString(keyNameInVertex, map);
				patternInVertex = ArgumentFunctions.mustParseJavaDateTimeFormat(keyPatternInVertex, map);
				newNameInVertex = ArgumentFunctions.mustParseNonEmptyString(keyNewNameInVertex, map);
				newPatternInVertex = ArgumentFunctions.mustParseJavaDateTimeFormat(keyNewPatternInVertex, map);
			}
			String nameInEdge = null;
			DateTimeFormatter patternInEdge = null;
			String newNameInEdge = null;
			DateTimeFormatter newPatternInEdge = null;
			if(map.containsKey(keyNameInEdge)){
				nameInEdge = ArgumentFunctions.mustParseNonEmptyString(keyNameInEdge, map);
				patternInEdge = ArgumentFunctions.mustParseJavaDateTimeFormat(keyPatternInEdge, map);
				newNameInEdge = ArgumentFunctions.mustParseNonEmptyString(keyNewNameInEdge, map);
				newPatternInEdge = ArgumentFunctions.mustParseJavaDateTimeFormat(keyNewPatternInEdge, map);
			}

			return initialize(nameInVertex, patternInVertex, newNameInVertex, newPatternInVertex, nameInEdge,
					patternInEdge, newNameInEdge, newPatternInEdge);
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to add filter", e);
			return false;
		}
	}

	private boolean initialize(final String nameInVertex, final DateTimeFormatter patternInVertex,
			final String newNameInVertex, final DateTimeFormatter newPatternInVertex, final String nameInEdge,
			final DateTimeFormatter patternInEdge, final String newNameInEdge,
			final DateTimeFormatter newPatternInEdge){
		this.nameInVertex = nameInVertex;
		this.patternInVertex = patternInVertex;
		this.newNameInVertex = newNameInVertex;
		this.newPatternInVertex = newPatternInVertex;
		this.nameInEdge = nameInEdge;
		this.patternInEdge = patternInEdge;
		this.newNameInEdge = newNameInEdge;
		this.newPatternInEdge = newPatternInEdge;
		logger.info("Arguments {"
				+ keyNameInVertex + "=" + nameInVertex
				+ ", " + keyPatternInVertex + "=" + patternInVertex
				+ ", " + keyNewNameInVertex + "=" + newNameInVertex
				+ ", " + keyNewPatternInVertex + "=" + newPatternInVertex
				+ ", " + keyNameInEdge + "=" + nameInEdge
				+ ", " + keyPatternInEdge + "=" + patternInEdge
				+ ", " + keyNewNameInEdge + "=" + newNameInEdge
				+ ", " + keyNewPatternInEdge + "=" + newPatternInEdge
				+ "}");
		return true;
	}

	@Override
	public boolean shutdown(){
		return true;
	}

	@Override
	public void putVertex(final AbstractVertex vertex){
		try{
			final AbstractVertex convertedVertex = createConvertedVertex(vertex);
			putInNextFilter(convertedVertex);
		}catch(Exception e){
			logger.log(Level.WARNING, "Failed to convert vertex: " + vertex, e);
		}
	}

	@Override
	public void putEdge(final AbstractEdge edge){
		try{
			final AbstractEdge convertedEdge = createConvertedEdge(edge);
			putInNextFilter(convertedEdge);
		}catch(Exception e){
			logger.log(Level.WARNING, "Failed to convert edge: " + edge, e);
		}
	}

	private AbstractVertex createConvertedVertex(final AbstractVertex vertex){
		if(nameInVertex == null) {
			return vertex;
		}
		final String annotationValue = vertex.getAnnotation(nameInVertex);
		if(annotationValue == null){
			return vertex;
		}
		final String convertedAnnotationValue = convertAnnotationValue(annotationValue, patternInVertex,
				newPatternInVertex);
		final AbstractVertex vertexCopy = vertex.copyAsVertex();
		vertexCopy.addAnnotation(newNameInVertex, convertedAnnotationValue);
		return vertexCopy;
	}

	private AbstractEdge createConvertedEdge(final AbstractEdge edge){
		if(nameInEdge == null){
			return edge;
		}
		final String annotationValue = edge.getAnnotation(nameInEdge);
		if(annotationValue == null){
			return edge;
		}
		final String convertedAnnotationValue = convertAnnotationValue(annotationValue, patternInEdge,
				newPatternInEdge);
		final AbstractVertex convertedChildVertex = createConvertedVertex(edge.getChildVertex());
		final AbstractVertex convertedParentVertex = createConvertedVertex(edge.getParentVertex());
		final AbstractEdge edgeCopy = new Edge(convertedChildVertex, convertedParentVertex);
		edgeCopy.addAnnotations(edge.getCopyOfAnnotations());
		edgeCopy.addAnnotation(newNameInEdge, convertedAnnotationValue);
		return edgeCopy;
	}

	private String convertAnnotationValue(final String value, final DateTimeFormatter pattern,
			final DateTimeFormatter newPattern){
		final LocalDateTime valueAsDateTime = LocalDateTime.parse(value, pattern);
		final String convertedValue = valueAsDateTime.format(newPattern);
		return convertedValue;
	}
}
