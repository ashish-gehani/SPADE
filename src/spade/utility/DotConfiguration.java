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
package spade.utility;

import java.io.File;
import java.io.FileReader;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Settings;

public class DotConfiguration{

	private static final String 
		keyDefaultVertexShape = "defaultVertexShape",
		keyDefaultVertexColor = "defaultVertexColor",
		keyVertexMappings = "vertexMappings",
		keyRequiredKeyValuePairs = "requiredKeyValuePairs",
		keyShape = "shape",
		keyColor = "color",
		keyStyle = "style",
		keyDefaultEdgeStyle = "defaultEdgeStyle",
		keyDefaultEdgeColor = "defaultEdgeColor",
		keyEdgeStyleMappings = "edgeStyleMappings",
		keyEdgeColorMappings = "edgeColorMappings";
	
	private final ShapeColor defaultVertexShapeColor;
	private final List<VertexRequiredKeyValuePairsToShapeAndColor> vertexMappings;
	private final String defaultEdgeStyle;
	private final String defaultEdgeColor;
	private final List<EdgeRequiredKeyValuePairsToStyle> edgeStyleMappings;
	private final List<EdgeRequiredKeyValuePairsToColor> edgeColorMappings;
	
	private DotConfiguration(ShapeColor defaultVertexShapeColor,
			List<VertexRequiredKeyValuePairsToShapeAndColor> vertexMappings, String defaultEdgeStyle,
			String defaultEdgeColor, List<EdgeRequiredKeyValuePairsToStyle> edgeStyleMappings,
			List<EdgeRequiredKeyValuePairsToColor> edgeColorMappings){
		this.defaultVertexShapeColor = defaultVertexShapeColor;
		this.vertexMappings = vertexMappings;
		this.defaultEdgeStyle = defaultEdgeStyle;
		this.defaultEdgeColor = defaultEdgeColor;
		this.edgeStyleMappings = edgeStyleMappings;
		this.edgeColorMappings = edgeColorMappings;
	}
	
	public final static String getDefaultConfigFilePath(){
		return Settings.getDefaultConfigFilePath(DotConfiguration.class);
	}

	public final ShapeColor getVertexShapeColor(final AbstractVertex vertex){
		if(vertex != null){
			for(VertexRequiredKeyValuePairsToShapeAndColor vertexMapping : vertexMappings){
				boolean allMatched = true;
				for(Map.Entry<String, String> requiredKeyValuePairsMapEntry : vertexMapping.requiredKeyValuePairsMap.entrySet()){
					if(!requiredKeyValuePairsMapEntry.getValue().equals(vertex.getAnnotation(requiredKeyValuePairsMapEntry.getKey()))){
						allMatched = false;
						break;
					}
				}
				if(allMatched){
					return vertexMapping.shapeColor;
				}
			}
		}
		return defaultVertexShapeColor;
	}
	
	public final String getEdgeColor(final AbstractEdge edge){
		if(edge != null){
			for(EdgeRequiredKeyValuePairsToColor edgeColorMapping : edgeColorMappings){
				boolean allMatched = true;
				for(Map.Entry<String, String> requiredKeyValuePairsMapEntry : edgeColorMapping.requiredKeyValuePairsMap.entrySet()){
					if(!requiredKeyValuePairsMapEntry.getValue().equals(edge.getAnnotation(requiredKeyValuePairsMapEntry.getKey()))){
						allMatched = false;
						break;
					}
				}
				if(allMatched){
					return edgeColorMapping.color;
				}
			}
		}
		return defaultEdgeColor;
	}
	
	public final String getEdgeStyle(final AbstractEdge edge){
		if(edge != null){
			for(EdgeRequiredKeyValuePairsToStyle edgeStyleMapping : edgeStyleMappings){
				boolean allMatched = true;
				for(Map.Entry<String, String> requiredKeyValuePairsMapEntry : edgeStyleMapping.requiredKeyValuePairsMap.entrySet()){
					if(!requiredKeyValuePairsMapEntry.getValue().equals(edge.getAnnotation(requiredKeyValuePairsMapEntry.getKey()))){
						allMatched = false;
						break;
					}
				}
				if(allMatched){
					return edgeStyleMapping.style;
				}
			}
		}
		return defaultEdgeStyle;
	}
	
	@Override
	public final String toString(){
		try{
			return toJSONString(2);
		}catch(Exception e){
			return HelperFunctions.formatExceptionStackTrace(e);
		}
	}
	
	public final String toJSONString(final int indent) throws Exception{
		final JSONObject wholeObject = new JSONObject();
		
		wholeObject.put(keyDefaultVertexShape, defaultVertexShapeColor.shape);
		wholeObject.put(keyDefaultVertexColor, defaultVertexShapeColor.color);
		int vertexMappingsArrayIndex = 0;
		final JSONArray vertexMappingsArray = new JSONArray();
		for(final VertexRequiredKeyValuePairsToShapeAndColor mapping : vertexMappings){
			final JSONArray requiredKeyValuePairsArray = new JSONArray();
			requiredKeyValuePairsArray.put(0, mapping.requiredKeyValuePairsMap);
			final JSONObject vertexMappingObject = new JSONObject();
			vertexMappingObject.put(keyRequiredKeyValuePairs, requiredKeyValuePairsArray);
			vertexMappingObject.put(keyShape, mapping.shapeColor.shape);
			vertexMappingObject.put(keyColor, mapping.shapeColor.color);
			vertexMappingsArray.put(vertexMappingsArrayIndex++, vertexMappingObject);
		}
		wholeObject.put(keyVertexMappings, vertexMappingsArray);
		
		wholeObject.put(keyDefaultEdgeStyle, defaultEdgeStyle);
		wholeObject.put(keyDefaultEdgeColor, defaultEdgeColor);
		
		int edgeStyleMappingsArrayIndex = 0;
		final JSONArray edgeStyleMappingsArray = new JSONArray();
		for(final EdgeRequiredKeyValuePairsToStyle mapping : edgeStyleMappings){
			final JSONArray requiredKeyValuePairsArray = new JSONArray();
			requiredKeyValuePairsArray.put(0, mapping.requiredKeyValuePairsMap);
			final JSONObject edgeStyleMappingObject = new JSONObject();
			edgeStyleMappingObject.put(keyRequiredKeyValuePairs, requiredKeyValuePairsArray);
			edgeStyleMappingObject.put(keyStyle, mapping.style);
			edgeStyleMappingsArray.put(edgeStyleMappingsArrayIndex++, edgeStyleMappingObject);
		}
		wholeObject.put(keyEdgeStyleMappings, edgeStyleMappingsArray);
		
		int edgeColorMappingsArrayIndex = 0;
		final JSONArray edgeColorMappingsArray = new JSONArray();
		for(final EdgeRequiredKeyValuePairsToColor mapping : edgeColorMappings){
			final JSONArray requiredKeyValuePairsArray = new JSONArray();
			requiredKeyValuePairsArray.put(0, mapping.requiredKeyValuePairsMap);
			final JSONObject edgeColorMappingObject = new JSONObject();
			edgeColorMappingObject.put(keyRequiredKeyValuePairs, requiredKeyValuePairsArray);
			edgeColorMappingObject.put(keyColor, mapping.color);
			edgeColorMappingsArray.put(edgeColorMappingsArrayIndex++, edgeColorMappingObject);
		}
		wholeObject.put(keyEdgeColorMappings, edgeColorMappingsArray);
		
		return wholeObject.toString(indent);
	}
	
	///////////////////////////////
	
	public static final synchronized Result<DotConfiguration> loadFromDefaultConfigFile(){
		final String configFilePath = Settings.getDefaultConfigFilePath(DotConfiguration.class);
		return loadFromFile(configFilePath);
	}
	
	public static final synchronized Result<DotConfiguration> loadFromFile(final String configFilePath){
		try{
			final File file = new File(configFilePath);
			if(!file.exists()){
				throw new Exception("File does not exist");
			}else{
				if(file.isDirectory()){
					throw new Exception("File is a directory but file is required");
				}
			}
		}catch(Exception e){
			return Result.failed("Failed to validate config file: '"+configFilePath+"'", e, null);
		}
		
		final JSONObject wholeObject;
		try{
			wholeObject = new JSONObject(new JSONTokener(new FileReader(new File(configFilePath))));
		}catch(Exception e){
			return Result.failed("Failed to parse json object from file: '"+configFilePath+"'", e, null);
		}

		try{
			final String defaultVertexShape = wholeObject.getString(keyDefaultVertexShape);
			if(HelperFunctions.isNullOrEmpty(defaultVertexShape)){
				throw new Exception("NULL/Empty string value for key '"+keyDefaultVertexShape+"'");
			}
			
			final String defaultVertexColor = wholeObject.getString(keyDefaultVertexColor);
			if(HelperFunctions.isNullOrEmpty(defaultVertexColor)){
				throw new Exception("NULL/Empty string value for key '"+keyDefaultVertexColor+"'");
			}
			
			final ShapeColor defaultVertexShapeColor = new ShapeColor(defaultVertexShape, defaultVertexColor);

			final List<VertexRequiredKeyValuePairsToShapeAndColor> vertexMappings = new ArrayList<VertexRequiredKeyValuePairsToShapeAndColor>();
			final JSONArray vertexMappingsArray = wholeObject.getJSONArray(keyVertexMappings);
			for(int i = 0; i < vertexMappingsArray.length(); i++){
				final JSONObject vertexMappingObject = vertexMappingsArray.getJSONObject(i);
				
				final String requiredKeyValuePairsShape = vertexMappingObject.getString(keyShape);
				if(HelperFunctions.isNullOrEmpty(requiredKeyValuePairsShape)){
					throw new Exception("NULL/Empty string value for key '"+keyShape+"' for vertex");
				}
				
				final String requiredKeyValuePairsColor = vertexMappingObject.getString(keyColor);
				if(HelperFunctions.isNullOrEmpty(requiredKeyValuePairsColor)){
					throw new Exception("NULL/Empty string value for key '"+keyColor+"' for vertex");
				}
				
				final ShapeColor shapeColor = new ShapeColor(requiredKeyValuePairsShape, requiredKeyValuePairsColor);
				
				final JSONArray requiredKeyValuePairsArray = vertexMappingObject.getJSONArray(keyRequiredKeyValuePairs);
				for(int j = 0; j < requiredKeyValuePairsArray.length(); j++){
					final JSONObject requiredKeyValuePairObject = requiredKeyValuePairsArray.getJSONObject(j);
					final Map<String, String> requiredKeyValuePairsMap = HelperFunctions.convertJSONObjectToMap(requiredKeyValuePairObject);
					if(requiredKeyValuePairsMap.isEmpty()){
						throw new Exception("Empty map in '"+keyRequiredKeyValuePairs+"' for '"+keyVertexMappings+"'");
					}
					vertexMappings.add(new VertexRequiredKeyValuePairsToShapeAndColor(requiredKeyValuePairsMap, shapeColor));
				}
			}
			
			final String defaultEdgeStyle = wholeObject.getString(keyDefaultEdgeStyle);
			if(HelperFunctions.isNullOrEmpty(defaultEdgeStyle)){
				throw new Exception("NULL/Empty string value for key '"+keyDefaultEdgeStyle+"'");
			}
			
			final String defaultEdgeColor = wholeObject.getString(keyDefaultEdgeColor);
			if(HelperFunctions.isNullOrEmpty(defaultEdgeColor)){
				throw new Exception("NULL/Empty string value for key '"+keyDefaultEdgeColor+"'");
			}
			
			final List<EdgeRequiredKeyValuePairsToStyle> edgeStyleMappings = new ArrayList<EdgeRequiredKeyValuePairsToStyle>();
			final JSONArray edgeStyleMappingsArray = wholeObject.getJSONArray(keyEdgeStyleMappings);
			for(int i = 0; i < edgeStyleMappingsArray.length(); i++){
				final JSONObject edgeStyleMappingObject = edgeStyleMappingsArray.getJSONObject(i);
				
				final String requiredKeyValuePairsStyle = edgeStyleMappingObject.getString(keyStyle);
				if(HelperFunctions.isNullOrEmpty(requiredKeyValuePairsStyle)){
					throw new Exception("NULL/Empty string value for key '"+keyStyle+"' for edge");
				}

				final JSONArray requiredKeyValuePairsArray = edgeStyleMappingObject.getJSONArray(keyRequiredKeyValuePairs);
				for(int j = 0; j < requiredKeyValuePairsArray.length(); j++){
					final JSONObject requiredKeyValuePairObject = requiredKeyValuePairsArray.getJSONObject(j);
					final Map<String, String> requiredKeyValuePairsMap = HelperFunctions.convertJSONObjectToMap(requiredKeyValuePairObject);
					if(requiredKeyValuePairsMap.isEmpty()){
						throw new Exception("Empty map in '"+keyRequiredKeyValuePairs+"' for '"+keyEdgeStyleMappings+"'");
					}
					edgeStyleMappings.add(new EdgeRequiredKeyValuePairsToStyle(requiredKeyValuePairsMap, 
							requiredKeyValuePairsStyle));
				}
			}
			
			final List<EdgeRequiredKeyValuePairsToColor> edgeColorMappings = new ArrayList<EdgeRequiredKeyValuePairsToColor>();
			final JSONArray edgeColorMappingsArray = wholeObject.getJSONArray(keyEdgeColorMappings);
			for(int i = 0; i < edgeColorMappingsArray.length(); i++){
				final JSONObject edgeColorMappingObject = edgeColorMappingsArray.getJSONObject(i);
				
				final String requiredKeyValuePairsColor = edgeColorMappingObject.getString(keyColor);
				if(HelperFunctions.isNullOrEmpty(requiredKeyValuePairsColor)){
					throw new Exception("NULL/Empty string value for key '"+keyColor+"' for edge");
				}

				final JSONArray requiredKeyValuePairsArray = edgeColorMappingObject.getJSONArray(keyRequiredKeyValuePairs);
				for(int j = 0; j < requiredKeyValuePairsArray.length(); j++){
					final JSONObject requiredKeyValuePairObject = requiredKeyValuePairsArray.getJSONObject(j);
					final Map<String, String> requiredKeyValuePairsMap = HelperFunctions.convertJSONObjectToMap(requiredKeyValuePairObject);
					if(requiredKeyValuePairsMap.isEmpty()){
						throw new Exception("Empty map in '"+keyRequiredKeyValuePairs+"' for '"+keyEdgeColorMappings+"'");
					}
					edgeColorMappings.add(new EdgeRequiredKeyValuePairsToColor(requiredKeyValuePairsMap, 
							requiredKeyValuePairsColor));
				}
			}
			
			return Result.successful(
					new DotConfiguration(
							defaultVertexShapeColor,
							vertexMappings, 
							defaultEdgeStyle, defaultEdgeColor, 
							edgeStyleMappings, 
							edgeColorMappings
							)
					);
			
		}catch(Exception e){
			return Result.failed("Invalid json configuration file", e, null);
		}
	}
	
	public static void main(String [] args) throws Exception{
//		args = new String[]{"cfg/spade.utility.DotConfiguration.config", "1"};
		
		if(args.length != 2){
			System.err.println("Invalid argument");
			System.err.println("Usage: <program> <json file path> <total number of loops for test>");
			return;
		}
		
		final String filePath = args[0];
		final Integer total = HelperFunctions.parseInt(args[1], null);
		if(total == null){
			System.err.println("Not a number for <total number of loops for test>");
			System.err.println("Usage: <program> <json file path> <total number of loops for test>");
			return;
		}
		
		System.out.println("Arguments: [File=" + filePath + ", total=" + total + "]");
		
		final Result<DotConfiguration> result = loadFromFile(filePath);
		if(result.error){
			System.err.println(result.toErrorString());
		}else{
			DotConfiguration d = result.result;
			final List<SimpleEntry<AbstractVertex, ShapeColor>> vertices = Arrays.asList(
					new SimpleEntry<AbstractVertex, ShapeColor>(testCreateVertex("", ""), new ShapeColor("box", "white")),
					new SimpleEntry<AbstractVertex, ShapeColor>(testCreateVertex("type", "Artifact", "subtype", "network socket"), new ShapeColor("diamond", "palegreen1")),
					new SimpleEntry<AbstractVertex, ShapeColor>(testCreateVertex("type", "Entity", "subtype", "network socket"), new ShapeColor("diamond", "palegreen1")),
					new SimpleEntry<AbstractVertex, ShapeColor>(testCreateVertex("type", "Object", "cdm.type", "NetFlowObject"), new ShapeColor("diamond", "palegreen1")),
					new SimpleEntry<AbstractVertex, ShapeColor>(testCreateVertex("type", "Artifact"), new ShapeColor("ellipse", "khaki1")),
					new SimpleEntry<AbstractVertex, ShapeColor>(testCreateVertex("type", "Entity"), new ShapeColor("ellipse", "khaki1")),
					new SimpleEntry<AbstractVertex, ShapeColor>(testCreateVertex("type", "Object"), new ShapeColor("ellipse", "khaki1")),
					new SimpleEntry<AbstractVertex, ShapeColor>(testCreateVertex("type", "Process"), new ShapeColor("box", "lightsteelblue1")),
					new SimpleEntry<AbstractVertex, ShapeColor>(testCreateVertex("type", "Activity"), new ShapeColor("box", "lightsteelblue1")),
					new SimpleEntry<AbstractVertex, ShapeColor>(testCreateVertex("type", "Subject"), new ShapeColor("box", "lightsteelblue1")),
					new SimpleEntry<AbstractVertex, ShapeColor>(testCreateVertex("type", "Agent"), new ShapeColor("octagon", "rosybrown1")),
					new SimpleEntry<AbstractVertex, ShapeColor>(testCreateVertex("type", "Principal"), new ShapeColor("octagon", "rosybrown1")),
					new SimpleEntry<AbstractVertex, ShapeColor>(testCreateVertex("type", "new"), new ShapeColor("box", "white")),
					new SimpleEntry<AbstractVertex, ShapeColor>(testCreateVertex(), new ShapeColor("box", "white"))
					);
			
			final List<SimpleEntry<AbstractEdge, StyleColor>> edges = Arrays.asList(
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge(), new StyleColor("solid", "black")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("success", "false"), new StyleColor("dashed", "black")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "Used"), new StyleColor("solid", "green")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_INIT_MODULE"), new StyleColor("solid", "green")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_FINIT_MODULE"), new StyleColor("solid", "green")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_LOADLIBRARY"), new StyleColor("solid", "green")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_READ"), new StyleColor("solid", "green")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_RECVMSG"), new StyleColor("solid", "green")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_ACCEPT"), new StyleColor("solid", "green")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "WasGeneratedBy"), new StyleColor("solid", "red")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_VMSPLICE"), new StyleColor("solid", "red")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_UNLINK"), new StyleColor("solid", "red")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_WRITE"), new StyleColor("solid", "red")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_SENDMSG"), new StyleColor("solid", "red")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_MPROTECT"), new StyleColor("solid", "red")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_CONNECT"), new StyleColor("solid", "red")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_TRUNCATE"), new StyleColor("solid", "red")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_MODIFY_FILE_ATTRIBUTES"), new StyleColor("solid", "red")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "WasTriggeredBy"), new StyleColor("solid", "blue")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "WasInformedBy"), new StyleColor("solid", "blue")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "UnitDependency"), new StyleColor("solid", "blue")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_EXIT"), new StyleColor("solid", "blue")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_FORK"), new StyleColor("solid", "blue")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_CLONE"), new StyleColor("solid", "blue")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_EXECUTE"), new StyleColor("solid", "blue")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_CHANGE_PRINCIPAL"), new StyleColor("solid", "blue")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_UNIT"), new StyleColor("solid", "blue")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_MODIFY_PROCESS"), new StyleColor("solid", "blue")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_SIGNAL"), new StyleColor("solid", "blue")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "WasControlledBy"), new StyleColor("solid", "purple")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "WasAssociatedWith"), new StyleColor("solid", "purple")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "ActedOnBehalfOf"),  new StyleColor("solid", "purple")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "WasAttributedTo"), new StyleColor("solid", "purple")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "WasDerivedFrom"), new StyleColor("solid", "orange")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_TEE"), new StyleColor("solid", "violet")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_SPLICE"), new StyleColor("solid", "violet")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_CLOSE"), new StyleColor("solid", "violet")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_OPEN"), new StyleColor("solid", "violet")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_CREATE_OBJECT"), new StyleColor("solid", "violet")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_MMAP"), new StyleColor("solid", "violet")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_RENAME"), new StyleColor("solid", "violet")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_LINK"), new StyleColor("solid", "violet")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "SimpleEdge", "cdm.type", "EVENT_UPDATE"), new StyleColor("solid", "violet")),
					new SimpleEntry<AbstractEdge, StyleColor>(testCreateEdge("type", "new"), new StyleColor("solid", "black"))
					);

			long startMillis = System.currentTimeMillis();
			for(int i = 0; i < total; i++){
				for(SimpleEntry<AbstractVertex, ShapeColor> vertexEntry : vertices){
					ShapeColor shouldBe = vertexEntry.getValue();
					ShapeColor is = d.getVertexShapeColor(vertexEntry.getKey());
					if(!is.equals(shouldBe)){
						System.err.println("Mismatch for vertex: " + vertexEntry.getKey() + ". Should be " + shouldBe + " but is " + is);
					}
				}
				
				for(SimpleEntry<AbstractEdge, StyleColor> edgeEntry : edges){
					final String color = d.getEdgeColor(edgeEntry.getKey());
					final String style = d.getEdgeStyle(edgeEntry.getKey());
					final StyleColor is = new StyleColor(style, color);
					final StyleColor shouldBe = edgeEntry.getValue();
					if(!is.equals(shouldBe)){
						System.err.println("Mismatch for edge: " + edgeEntry.getKey() + ". Should be " + shouldBe + " but is " + is);
					}
				}
			}
			long diffMillis = System.currentTimeMillis() - startMillis;
			int comparisons = total * (vertices.size() + (edges.size() * 2));
			System.out.println(String.format("%.6f milli(s) per comparison", ((double)diffMillis / (double)comparisons)));
		}
	}
	
	private static final AbstractVertex testCreateVertex(final String ... keyValues){
		spade.core.Vertex vertex = new spade.core.Vertex();
		for(int i = 0; i < keyValues.length; i+=2){
			vertex.addAnnotation(keyValues[i], keyValues[i+1]);
		}
		return vertex;
	}
	
	private static final AbstractEdge testCreateEdge(final String ... keyValues){
		spade.core.Edge edge = new spade.core.Edge(null, null);
		for(int i = 0; i < keyValues.length; i+=2){
			edge.addAnnotation(keyValues[i], keyValues[i+1]);
		}
		return edge;
	}
	
	private static final class VertexRequiredKeyValuePairsToShapeAndColor{
		private final Map<String, String> requiredKeyValuePairsMap = new HashMap<String, String>();
		private final ShapeColor shapeColor;
		private VertexRequiredKeyValuePairsToShapeAndColor(final Map<String, String> requiredKeyValuePairsMap,
				final ShapeColor shapeColor){
			this.requiredKeyValuePairsMap.putAll(requiredKeyValuePairsMap);
			this.shapeColor = shapeColor;
		}
	}
	
	public static final class ShapeColor{
		public final String shape;
		public final String color;
		private ShapeColor(final String shape, final String color){
			this.shape = shape;
			this.color = color;
		}
		@Override
		public final String toString(){
			return "ShapeColor [shape=" + shape + ", color=" + color + "]";
		}
		@Override
		public int hashCode(){
			final int prime = 31;
			int result = 1;
			result = prime * result + ((color == null) ? 0 : color.hashCode());
			result = prime * result + ((shape == null) ? 0 : shape.hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj){
			if(this == obj)
				return true;
			if(obj == null)
				return false;
			if(getClass() != obj.getClass())
				return false;
			ShapeColor other = (ShapeColor)obj;
			if(color == null){
				if(other.color != null)
					return false;
			}else if(!color.equals(other.color))
				return false;
			if(shape == null){
				if(other.shape != null)
					return false;
			}else if(!shape.equals(other.shape))
				return false;
			return true;
		}
	}
	
	private static final class StyleColor{
		private final String style;
		private final String color;
		private StyleColor(final String style, final String color){
			this.style = style;
			this.color = color;
		}
		@Override
		public final String toString(){
			return "StyleColor [style=" + style + ", color=" + color + "]";
		}
		@Override
		public int hashCode(){
			final int prime = 31;
			int result = 1;
			result = prime * result + ((color == null) ? 0 : color.hashCode());
			result = prime * result + ((style == null) ? 0 : style.hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj){
			if(this == obj)
				return true;
			if(obj == null)
				return false;
			if(getClass() != obj.getClass())
				return false;
			StyleColor other = (StyleColor)obj;
			if(color == null){
				if(other.color != null)
					return false;
			}else if(!color.equals(other.color))
				return false;
			if(style == null){
				if(other.style != null)
					return false;
			}else if(!style.equals(other.style))
				return false;
			return true;
		}
	}
	
	private static final class EdgeRequiredKeyValuePairsToStyle{
		private final Map<String, String> requiredKeyValuePairsMap = new HashMap<String, String>();
		private final String style;
		private EdgeRequiredKeyValuePairsToStyle(final Map<String, String> requiredKeyValuePairsMap, final String style){
			this.requiredKeyValuePairsMap.putAll(requiredKeyValuePairsMap);
			this.style = style;
		}
	}
	
	private static final class EdgeRequiredKeyValuePairsToColor{
		private final Map<String, String> requiredKeyValuePairsMap = new HashMap<String, String>();
		private final String color;
		private EdgeRequiredKeyValuePairsToColor(final Map<String, String> requiredKeyValuePairsMap, final String color){
			this.requiredKeyValuePairsMap.putAll(requiredKeyValuePairsMap);
			this.color = color;
		}
	}

}
