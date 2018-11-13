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

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.utility.CommonFunctions;
import spade.utility.ExternalMemoryMap;
import spade.utility.FileUtility;
import spade.utility.Hasher;

/**
 * The filter drops repeat edges between any two vertices by the values of keys provided i.e.
 * all consecutive edges between any two vertices with the same value are represented
 * by the first edge of that group.
 * 
 * Example command:
 * 	add filter DropRepeatEdges position=1 by=operation,type
 * 
 * The above mentioned command adds the filter where all the consecutive edges between any two vertices 
 * by the combination of values of 'operation', and 'type' are dropped except the first one.
 * 
 * NOTE: Assumes the data is coming in order that it is executed.
 */
public class DropRepeatEdges extends AbstractFilter{

	private final static Logger logger = Logger.getLogger(DropRepeatEdges.class.getName());

	private final static String CONFIG_KEY_TEMP_DIR = "tempDir",
			CONFIG_KEY_DBNAME = "dbName",
			CONFIG_KEY_CACHESIZE = "cacheSize",
			CONFIG_KEY_FALSEPOSITIONPROBABILITY = "bloomfilterFalsePositiveProbability",
			CONFIG_KEY_EXPECTEDNUMBEROFELEMENTS = "bloomFilterExpectedNumberOfElements",
			CONFIG_KEY_REPORTINGINTERVALSECONDS = "reportingIntervalSeconds";
	private final static String dropByArgName = "by";

	private Set<String> dropBy = new HashSet<String>();

	private String vertex2VertexMapId = "DropRepeatEdgesMap";
	private ExternalMemoryMap<String, HashMap<String, String>> vertex2VertexAndLastDropByValue;

	private BigInteger totalDropped = BigInteger.ZERO;
	
	private boolean parseArguments(String arguments){
		Map<String, String> argsMap = CommonFunctions.parseKeyValPairs(arguments);
		String byArgValue = argsMap.get(dropByArgName);
		if(CommonFunctions.isNullOrEmpty(byArgValue)){
			logger.log(Level.SEVERE, "NULL/Empty '"+dropByArgName+"' arg value");
			return false;
		}else{
			String tokens [] = byArgValue.split(",");
			for(String token : tokens){
				token = token.trim();
				if(token.isEmpty()){
					logger.log(Level.SEVERE, "NULL/Empty token in '"+dropByArgName+"' arg value: " + byArgValue);
					return false;
				}
				dropBy.add(token);
			}
			logger.log(Level.INFO, String.format("Arguments: ['%s'='%s']", dropByArgName, dropBy));
			return true;
		}
	}

	@Override
	public boolean initialize(String arguments){
		if(parseArguments(arguments)){
			String defaultConfigFilePath = Settings.getDefaultConfigFilePath(this.getClass());
			Map<String, String> configMap = null;
			try{
				configMap = FileUtility.readConfigFileAsKeyValueMap(defaultConfigFilePath, "=");
			}catch(Throwable t){
				logger.log(Level.SEVERE, "Failed to read default config file: " + defaultConfigFilePath, t);
				return false;
			}

			try{
				vertex2VertexAndLastDropByValue = CommonFunctions.createExternalMemoryMapInstance(vertex2VertexMapId, 
						configMap.get(CONFIG_KEY_CACHESIZE), configMap.get(CONFIG_KEY_FALSEPOSITIONPROBABILITY), 
						configMap.get(CONFIG_KEY_EXPECTEDNUMBEROFELEMENTS), configMap.get(CONFIG_KEY_TEMP_DIR), 
						configMap.get(CONFIG_KEY_DBNAME), configMap.get(CONFIG_KEY_REPORTINGINTERVALSECONDS), 
						new Hasher<String>(){
							@Override
							public String getHash(String t){
								return String.valueOf(t);
							}
						});
				
				return true;
			}catch(Throwable t){
				return false;
			}
		}else{
			return false;
		}		
	}

	@Override
	public boolean shutdown(){
		try{
			CommonFunctions.closePrintSizeAndDeleteExternalMemoryMap(vertex2VertexMapId, vertex2VertexAndLastDropByValue);
		}catch(Throwable t){
			logger.log(Level.SEVERE, null, t);
		}
		
		logger.log(Level.INFO, "Total grouped edges: " + totalDropped);
		
		return true;
	}

	/**
	 * Searches for given keys in the edge and returns the combined value.
	 * NULL returned otherwise
	 * 
	 * @param edge
	 * @param dropByKeySet
	 * @return
	 */
	private String getDropByValue(AbstractEdge edge, Set<String> dropByKeySet){
		// Return 'null' if not able to get any value
		if(edge != null && dropByKeySet != null){
			String dropByValue = "";
			for(String dropByKey : dropByKeySet){
				String dropByKeyValue = edge.getAnnotation(dropByKey);
				dropByValue += dropByKeyValue + ",";
			}
			if(!dropByValue.isEmpty()){
				return dropByValue;
			}else{
				return null;
			}
		}else{
			return null;
		}
	}

	@Override
	public void putVertex(AbstractVertex incomingVertex){
		super.putInNextFilter(incomingVertex);
	}

	@Override
	public void putEdge(AbstractEdge incomingEdge){
		/*
		 * Checks if the edge is the same as the last one (between these two vertices) based 
		 * on the aggregated value.
		 */
		if(incomingEdge != null){
			AbstractVertex parentVertex = incomingEdge.getParentVertex();
			AbstractVertex childVertex = incomingEdge.getChildVertex();
			if(parentVertex != null && childVertex != null){
				String newDropValue = getDropByValue(incomingEdge, dropBy);
				boolean put = false;
				String parentHash = parentVertex.bigHashCode();
				String childHash = childVertex.bigHashCode();
				HashMap<String, String> vertex2LastDropValueMap = vertex2VertexAndLastDropByValue.get(parentHash);
				if(vertex2LastDropValueMap == null){
					vertex2LastDropValueMap = new HashMap<String, String>();
					vertex2VertexAndLastDropByValue.put(parentHash, vertex2LastDropValueMap);
					vertex2LastDropValueMap.put(childHash, newDropValue);
					put = true;
				}else{
					String existingDropValue = vertex2LastDropValueMap.get(childHash);
					if(!StringUtils.equals(existingDropValue, newDropValue)){
						vertex2LastDropValueMap.put(childHash, newDropValue);
						put = true;
					}
				}

				if(put){
					super.putInNextFilter(incomingEdge);
				}else{
					totalDropped = totalDropped.add(BigInteger.ONE);
				}
			}
		}
	}

}
