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

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractTransformer;
import spade.core.Graph;
import spade.core.Settings;
import spade.core.Graph.QueryParams;

public class BEEP extends AbstractTransformer {
	
	private final Logger logger = Logger.getLogger(getClass().getName()); 
	
	private static final String DIRECTION_ANCESTORS = Settings.getProperty("direction_ancestors");
    private static final String DIRECTION_DESCENDANTS = Settings.getProperty("direction_descendants");
	
	private AbstractTransformer[] forwardSearchTransformers = null;
	private AbstractTransformer[] backwardSearchTransformers = null;
	
	public boolean initializeTransformer(AbstractTransformer transformer, String arguments){
		boolean success = transformer.initialize(arguments);
		if(!success){
			logger.log(Level.SEVERE, "Failed to initialize transformer " + transformer.getClass().getName());
		}
		return success;
	}
	
	public boolean initialize(String arguments){
		boolean success = true;
		//always the first for now until the issue is resolved
		AbstractTransformer removeSudoLineage = new RemoveLineage();
		success = success && initializeTransformer(removeSudoLineage, "name:sudo");
		
		AbstractTransformer removeBeepUnits = new RemoveBEEPUnits();
		success = success && initializeTransformer(removeBeepUnits, arguments);
		
		AbstractTransformer removeMemoryVertices = new RemoveMemoryVertices();
		success = success && initializeTransformer(removeMemoryVertices, arguments);
		
		AbstractTransformer removeRenameLinkUpdateEdges = new ReplaceRenameLinkWithWrite();
		success = success && initializeTransformer(removeRenameLinkUpdateEdges, arguments);
		
		AbstractTransformer collapseArtifactVersions = new CollapseArtifactVersions();
		success = success && initializeTransformer(collapseArtifactVersions, arguments);
		
		AbstractTransformer mergeIOEdges = new MergeIOEdges();
		success = success && initializeTransformer(mergeIOEdges, arguments);
		
		AbstractTransformer mergeForkCloneAndExecveEdges = new MergeForkCloneAndExecveEdges(); 
		success = success && initializeTransformer(mergeForkCloneAndExecveEdges, arguments);
		
		AbstractTransformer removeFiles = new RemoveFiles();
		success = success && initializeTransformer(removeFiles, arguments);
		
		AbstractTransformer removeFileReadIfReadOnlyForwardSearch = new RemoveFileReadIfReadOnly();
		success = success && initializeTransformer(removeFileReadIfReadOnlyForwardSearch, arguments);
		
		AbstractTransformer removeFileReadIfReadOnlyBackwardSearch = new RemoveFileReadIfReadOnly();
		success = success && initializeTransformer(removeFileReadIfReadOnlyBackwardSearch, arguments);
		
		AbstractTransformer removeFileWriteIfWriteOnly = new RemoveFileWriteIfWriteOnly();
		success = success && initializeTransformer(removeFileWriteIfWriteOnly, arguments);
		
		if(!success){
			return false;
		}
		
		forwardSearchTransformers = new AbstractTransformer[] {
				removeSudoLineage,
				removeBeepUnits,
				removeMemoryVertices,
				removeRenameLinkUpdateEdges,
				collapseArtifactVersions,
				mergeIOEdges,
				mergeForkCloneAndExecveEdges,
				removeFiles,
				removeFileReadIfReadOnlyForwardSearch,
				//removeFileWriteIfWriteOnly
		};
		
		backwardSearchTransformers = new AbstractTransformer[]{
				removeSudoLineage,
				removeBeepUnits,
				removeMemoryVertices,
				removeRenameLinkUpdateEdges,
				collapseArtifactVersions,
				mergeIOEdges,
				mergeForkCloneAndExecveEdges,
				removeFiles,
				removeFileReadIfReadOnlyBackwardSearch,
				removeFileWriteIfWriteOnly
		};
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
		
		AbstractTransformer[] transformers = null;
		
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
