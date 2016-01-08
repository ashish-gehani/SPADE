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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.io.FileUtils;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.vertex.opm.Artifact;
import spade.vertex.prov.Entity;

public class FileFilter extends AbstractFilter{
	
	private static final Logger logger = Logger.getLogger(FileFilter.class.getName());
	
	private Pattern fileExclusionPattern;
	
	public boolean initialize(String arguments){
		
		try{
			fileExclusionPattern = Pattern.compile(FileUtils.readLines(new File(Settings.getProperty("filefilter_config_filepath"))).get(0));
			return true;
		}catch(Exception e){
			logger.log(Level.WARNING, null, e);
			return false;
		}
	}
	
	private boolean isVertexInExclusionPattern(AbstractVertex incomingVertex){
		if(incomingVertex instanceof Artifact || incomingVertex instanceof Entity){
			String path = incomingVertex.getAnnotation("path");
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
			if(!isVertexInExclusionPattern(incomingEdge.getSourceVertex()) 
					&& !isVertexInExclusionPattern(incomingEdge.getDestinationVertex())){
				super.putInNextFilter(incomingEdge);
			}
		}
	}
	
}
