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

import org.apache.commons.io.FileUtils;
import spade.client.QueryParameters;
import spade.core.AbstractTransformer;
import spade.core.Graph;
import spade.core.Settings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BEEP extends AbstractTransformer {
	
	private final Logger logger = Logger.getLogger(getClass().getName()); 
	
	private static final String DIRECTION_ANCESTORS = Settings.getProperty("direction_ancestors");
    private static final String DIRECTION_DESCENDANTS = Settings.getProperty("direction_descendants");
	
	private List<AbstractTransformer> forwardSearchTransformers = null;
	private List<AbstractTransformer> backwardSearchTransformers = null;
		
	public List<AbstractTransformer> loadTransformersFromFile(List<String> transformersFileLines){
		try{
			List<AbstractTransformer> transformers = new ArrayList<AbstractTransformer>();
			if(transformersFileLines == null || transformersFileLines.isEmpty()){
				logger.log(Level.SEVERE, "Transformer file list is missing or is malformed");
				return null;
			}
			for(String line : transformersFileLines){
				int blankCharIndex = line.indexOf(' ');
				if(blankCharIndex == -1){
					blankCharIndex = line.length();
				}
				String transformerClassName = line.substring(0, blankCharIndex);
				String transformerArguments = line.substring(blankCharIndex);
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
		String configFile = Settings.getDefaultConfigFilePath(this.getClass());
		try{
			List<String> lines = FileUtils.readLines(new File(configFile));
			List<String> backwardSearchLines = new ArrayList<String>();
			List<String> forwardSearchLines = new ArrayList<String>();
			List<String> listHandle = null;
			for(int a = 0; a<lines.size(); a++){
				String line = lines.get(a);
				if(line != null){
					line = line.trim();
					if(line.startsWith("#")){
						if(line.contains("backward_search")){
							listHandle = backwardSearchLines;
						}else if(line.contains("forward_search")){
							listHandle = forwardSearchLines;
						}
					}else{
						if(!line.isEmpty()){
							listHandle.add(line);
						}
					}
				}
			}
			
			forwardSearchTransformers = loadTransformersFromFile(forwardSearchLines);
			
			if(forwardSearchTransformers == null){
				return false;
			}
			
			backwardSearchTransformers = loadTransformersFromFile(backwardSearchLines);
			
			if(backwardSearchTransformers == null){
				return false;
			}
			
			return true;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Error reading/loading '"+configFile+"'", e);
			return false;
		}
		
	}
	
	@Override
	public Graph putGraph(Graph graph, QueryParameters digQueryParams) {
		
		if(digQueryParams == null || digQueryParams.getDirection() == null){
			return graph;
		}
		
		List<AbstractTransformer> transformers = null;
		
		if(DIRECTION_ANCESTORS.startsWith(digQueryParams.getDirection())){
			transformers = backwardSearchTransformers;
		}else if(DIRECTION_DESCENDANTS.startsWith(digQueryParams.getDirection())){
			transformers = forwardSearchTransformers;
		}else{
			return graph;
		}			
		
		for(AbstractTransformer transformer : transformers){
			if(graph != null){
				graph = transformer.putGraph(graph, digQueryParams);
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
