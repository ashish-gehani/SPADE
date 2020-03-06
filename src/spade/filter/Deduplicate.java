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

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.somethingsimilar.opposite_of_a_bloom_filter.ByteArrayFilter;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;

/**
 * A filter to remove 'most' of the duplicate vertices and edges.
 * 
 * Using 'https://github.com/jmhodges/opposite_of_a_bloom_filter' to find out
 * if we have seen a vertex or an edge before. The data structure can return false negatives but 
 * never false positives.
 * 
 * So, a vertex or an edge is put into the next filter ONLY IF it is not contained in the data structure
 * mentioned above. Since there can be false negatives there can be some duplicates but there would
 * never be a case where we don't put something in the next filter which we haven't seen before.
 *
 */
public class Deduplicate extends AbstractFilter{

	private Logger logger = Logger.getLogger(this.getClass().getName());
	
	private final String ARG_EXPECTEDNUMBEROFELEMENTS_KEY = "expectedNumberOfElements";
	
	// Source: https://github.com/jmhodges/opposite_of_a_bloom_filter
	// Source copyright below:
	// Copyright 2012 Jeff Hodges. All rights reserved.
	// Use of this source code is governed by a BSD-style
	// license that can be found in the LICENSE file (in the above-mentioned git).
	private ByteArrayFilter negativeBloomFilter;
	
	/**
	 * Read expectedNumberOfElements from argument, if not found then read from config file. 
	 * 
	 * @param arguments empty or 'expectedNumberOfElements=100000'
	 * @return true if internal data structure initialized successfully
	 */
	public boolean initialize(String arguments){
		String expectedNumberOfElementsString = null;
		Map<String, String> argsMap = HelperFunctions.parseKeyValPairs(arguments);
		expectedNumberOfElementsString = argsMap.get(ARG_EXPECTEDNUMBEROFELEMENTS_KEY);
		if(expectedNumberOfElementsString == null){
			try{
				Map<String, String> configMap = FileUtility.readConfigFileAsKeyValueMap(Settings.getDefaultConfigFilePath(this.getClass()), "=");
				expectedNumberOfElementsString = configMap.get(ARG_EXPECTEDNUMBEROFELEMENTS_KEY);
				logger.log(Level.INFO, "Argument => expectedNumberOfElements: {0}", new Object[]{expectedNumberOfElementsString});
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to read arguments", e);
			}
		}
		if(expectedNumberOfElementsString == null){
			logger.log(Level.SEVERE, "Must specify '"+ARG_EXPECTEDNUMBEROFELEMENTS_KEY+"' in either argument or in config file");
			return false;
		}else{
			try{
				Integer expectedNumberOfElements = Integer.parseInt(expectedNumberOfElementsString);
				negativeBloomFilter = new ByteArrayFilter(expectedNumberOfElements);
				return true;
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to initialize internal data structure", e);
				return false;
			}catch(OutOfMemoryError error){
				logger.log(Level.SEVERE, "Must specify a smaller number of expected elements or increase SPADE memory", error);
				throw error;
			}
		}
	}
	
	@Override
	public void putVertex(AbstractVertex incomingVertex) {
        boolean contained = false;
		contained = negativeBloomFilter.containsAndAdd(incomingVertex.bigHashCodeBytes());
		if(!contained){
			putInNextFilter(incomingVertex);
		}
	}

	@Override
	public void putEdge(AbstractEdge incomingEdge) {
        boolean contained = false;
		contained = negativeBloomFilter.containsAndAdd(incomingEdge.bigHashCodeBytes());
		if(!contained){
			putInNextFilter(incomingEdge);
		}
	}
	
}
