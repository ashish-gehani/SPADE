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
package spade.transformer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;

import spade.core.AbstractTransformer;
import spade.core.Graph;
import spade.core.Graph.QueryParams;
import spade.core.Settings;

public class BEEP extends AbstractTransformer {
	
	private final Logger logger = Logger.getLogger(getClass().getName()); 
	
	private static final String DIRECTION_ANCESTORS = Settings.getProperty("direction_ancestors");
    private static final String DIRECTION_DESCENDANTS = Settings.getProperty("direction_descendants");
	
	private List<AbstractTransformer> forwardSearchTransformers = null;
	private List<AbstractTransformer> backwardSearchTransformers = null;
		
	public List<AbstractTransformer> loadTransformersFromFile(String filepath){
		try{
			List<AbstractTransformer> transformers = new ArrayList<AbstractTransformer>();
			List<String> transformersFileLines = FileUtils.readLines(new File(filepath));
			if(transformersFileLines == null || transformersFileLines.isEmpty()){
				logger.log(Level.SEVERE, "Transformer file list is missing or is malformed");
				return null;
			}
			for(String line : transformersFileLines){
				String tokens[] = line.split("\\s+");
				String transformerClassName = tokens[0];
				String transformerArguments = tokens.length == 2 ? tokens[1] : null;
				AbstractTransformer transformer = (AbstractTransformer) Class.forName("spade.transformer." + transformerClassName).newInstance();
				if(!transformer.initialize(transformerArguments)){
					logger.log(Level.SEVERE, "Failed to initialize transformer " + transformer.getClass().getName());
					return null;
				}
				transformers.add(transformer);
			}
			return transformers;
		}catch(Exception e){
			logger.log(Level.SEVERE, null, e);
			return null;
		}
	}
	
	public boolean initialize(String arguments){
		forwardSearchTransformers = loadTransformersFromFile(Settings.getProperty("forward_search_transformers_list_filepath"));
		
		if(forwardSearchTransformers == null){
			return false;
		}
		
		backwardSearchTransformers = loadTransformersFromFile(Settings.getProperty("backward_search_transformers_list_filepath"));
		
		if(backwardSearchTransformers == null){
			return false;
		}
				
		return true;
	}
	
	@Override
	public Graph putGraph(Graph graph) {
		
		Map<QueryParams, Object> queryParams = null;
		String direction = null;
		if(graph != null){
			queryParams = graph.getQueryParams();
			direction = String.valueOf(graph.getQueryParam(QueryParams.DIRECTION));
		}else{
			return graph;
		}
		
		List<AbstractTransformer> transformers = null;
		
		if(DIRECTION_ANCESTORS.startsWith(direction)){
			transformers = backwardSearchTransformers;
		}else if(DIRECTION_DESCENDANTS.startsWith(direction)){
			transformers = forwardSearchTransformers;
		}else{
			//do nothing
			return graph;
		}			
		
		for(AbstractTransformer transformer : transformers){
			if(graph != null){
				graph.setQueryParams(queryParams);
				graph = transformer.putGraph(graph);
				if(graph != null){
					graph.commitIndex();
				}
			}else{
				break;
			}
		}
		
		return graph;
	}
	
}
