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

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.reporter.audit.OPMConstants;
import spade.utility.FileUtility;
import spade.vertex.opm.Artifact;
import spade.vertex.prov.Entity;

public class Blacklist extends AbstractFilter{
	
	private static final Logger logger = Logger.getLogger(Blacklist.class.getName());
	
	private Pattern fileExclusionPattern;
	
	public boolean initialize(String arguments){
		
		try{
			String filepath = Settings.getDefaultConfigFilePath(this.getClass());
			fileExclusionPattern = FileUtility.constructRegexFromFile(filepath);
			if(fileExclusionPattern == null){
				throw new Exception("Regex read from file '"+filepath+"' cannot be null");
			}
			return true;
		}catch(Exception e){
			logger.log(Level.WARNING, null, e);
			return false;
		}
	}
	
	private boolean isVertexInExclusionPattern(AbstractVertex incomingVertex){
		if(incomingVertex instanceof Artifact || incomingVertex instanceof Entity){
			String path = incomingVertex.getAnnotation(OPMConstants.ARTIFACT_PATH);
			if(path != null){
				Matcher filepathMatcher = fileExclusionPattern.matcher(path);
				return filepathMatcher.find();
			}
		}
		return false;
	}

	@Override
	public void putVertex(AbstractVertex incomingVertex) {
		if(!isVertexInExclusionPattern(incomingVertex)){
			super.putInNextFilter(incomingVertex);
		}
	}

	@Override
	public void putEdge(AbstractEdge incomingEdge) {
		if(incomingEdge != null){
			if(!isVertexInExclusionPattern(incomingEdge.getChildVertex())
					&& !isVertexInExclusionPattern(incomingEdge.getParentVertex())){
				super.putInNextFilter(incomingEdge);
			}
		}
	}
	
}
