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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;

import spade.core.AbstractTransformer;
import spade.core.Graph;
import spade.core.Settings;
import spade.query.quickgrail.instruction.GetLineage;
import spade.utility.Result;

public class BEEP extends AbstractTransformer{

	private final Logger logger = Logger.getLogger(getClass().getName());

	private List<AbstractTransformer> forwardSearchTransformers = null;
	private List<AbstractTransformer> backwardSearchTransformers = null;

	public List<AbstractTransformer> loadTransformersFromFile(List<String> transformersFileLines){
		try{
			List<AbstractTransformer> transformers = new ArrayList<>();
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

				final Result<AbstractTransformer> createResult = AbstractTransformer.create(transformerClassName);
				if(createResult.error){
					logger.log(Level.SEVERE, createResult.toErrorString());
					return null;
				}

				final Result<Boolean> initResult = AbstractTransformer.init(createResult.result, transformerArguments);
				if(initResult.error){
					logger.log(Level.SEVERE, initResult.toErrorString());
					return null;
				}

				transformers.add(createResult.result);
			}
			return transformers;
		}catch(Exception ex){
			logger.log(Level.SEVERE, null, ex);

			return null;
		}
	}

	public boolean initialize(String arguments){
		String configFile = Settings.getDefaultConfigFilePath(this.getClass());
		try{
			List<String> lines = FileUtils.readLines(new File(configFile));
			List<String> backwardSearchLines = new ArrayList<>();
			List<String> forwardSearchLines = new ArrayList<>();
			List<String> listHandle = null;
			for(int a = 0; a < lines.size(); a++){
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
		}catch(Exception ex){
			logger.log(Level.SEVERE, "Error reading/loading '" + configFile + "'", ex);

			return false;
		}

	}

	@Override
	public LinkedHashSet<ArgumentName> getArgumentNames(){
		return new LinkedHashSet<ArgumentName>(
				Arrays.asList(
						ArgumentName.SOURCE_GRAPH
						, ArgumentName.MAX_DEPTH
						, ArgumentName.DIRECTION
						)
				);
	}

	@Override
	public Graph transform(Graph graph, ExecutionContext context){

		if(context.getDirection() == null){
			return graph;
		}

		List<AbstractTransformer> transformers;

		if(context.getDirection().equals(GetLineage.Direction.kAncestor)){
			transformers = backwardSearchTransformers;
		}else if(context.getDirection().equals(GetLineage.Direction.kDescendant)){
			transformers = forwardSearchTransformers;
		}else{
			return graph;
		}

		for(AbstractTransformer transformer : transformers){
			if(graph != null){
				final Result<Graph> executeResult = AbstractTransformer.execute(transformer, graph, context);
				if(executeResult.error){
					break;
				}
				graph = executeResult.result;
			}else{
				break;
			}
		}

		return graph;
	}

	@Override
	public boolean shutdown(){
		boolean success = true;
		if(forwardSearchTransformers != null){
			for(AbstractTransformer transformer : forwardSearchTransformers){
				if(transformer != null){
					final Result<Boolean> shutdownResult = AbstractTransformer.destroy(transformer);
					if(shutdownResult.error){
						logger.log(Level.WARNING, shutdownResult.toErrorString());
						success = false;
						continue;
					}
					if(!shutdownResult.result){
						logger.log(Level.WARNING, "Failed to shutdown transformer '" + transformer.getClass().getSimpleName() + "'");
						success = false;
					}
				}
			}
		}
		if(backwardSearchTransformers != null){
			for(AbstractTransformer transformer : backwardSearchTransformers){
				if(transformer != null){
					final Result<Boolean> shutdownResult = AbstractTransformer.destroy(transformer);
					if(shutdownResult.error){
						logger.log(Level.WARNING, shutdownResult.toErrorString());
						success = false;
						continue;
					}
					if(!shutdownResult.result){
						logger.log(Level.WARNING, "Failed to shutdown transformer '" + transformer.getClass().getSimpleName() + "'");
						success = false;
					}
				}
			}
		}
		return success;
	}
}
