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
package spade.screen;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractScreen;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;
import spade.utility.Result;
import spade.utility.map.external.cache.LRUCache;

public final class Deduplicate extends AbstractScreen{

	private final static Logger logger = Logger.getLogger(Deduplicate.class.getName());

	private final String keyLoadSavePathVertex = "vertex.bloomFilter.path";
	private final String keyExpectedElementsVertex = "vertex.bloomFilter.expectedElements";
	private final String keyFalsePositiveProbabilityVertex = "vertex.bloomFilter.falsePositiveProbability";
	private final String keyCacheSizeVertex = "vertex.cache.size";
	private final String keyLoadSavePathEdge = "edge.bloomFilter.path";
	private final String keyExpectedElementsEdge = "edge.bloomFilter.expectedElements";
	private final String keyFalsePositiveProbabilityEdge = "edge.bloomFilter.falsePositiveProbability";
	private final String keyCacheSizeEdge = "edge.cache.size";
	private final String keyReportingIntervalSeconds = "reportingIntervalSeconds";

	private final Object blankObject = new Object();
	private final Object lockObject = new Object();
	
	private String loadSavePathVertex = null;
	private spade.core.BloomFilter<String> bloomFilterVertex = null;
	private LRUCache<String, Object> cacheVertex;
	private String loadSavePathEdge = null;
	private spade.core.BloomFilter<String> bloomFilterEdge = null;
	private LRUCache<String, Object> cacheEdge;
	
	private boolean reportingEnabled;
	private long reportingIntervalMillis;
	private long lastReportedAtMillis;
	private long verticesBlocked = 0;
	private long verticesNotBlocked = 0;
	private long edgeBlocked = 0;
	private long edgesNotBlocked = 0;
	
	@Override
	public boolean initialize(final String argumentsString){
		final String arguments = argumentsString == null ? "" : argumentsString.trim();
		try{
			final Map<String, String> map =  
					HelperFunctions.parseKeyValuePairsFrom(arguments, Settings.getDefaultConfigFilePath(this.getClass()), null);

			final String reportingIntervalSecondsString = map.get(keyReportingIntervalSeconds);
			final String vertexLoadSavePathString = map.get(keyLoadSavePathVertex);
			final String vertexBloomFilterExpectedElementsString = map.get(keyExpectedElementsVertex);
			final String vertexBloomFilterFalsePositiveProbabilityString = map.get(keyFalsePositiveProbabilityVertex);
			final String vertexCacheSizeString = map.get(keyCacheSizeVertex);
			final String edgeLoadSavePathString = map.get(keyLoadSavePathEdge);
			final String edgeBloomFilterExpectedElementsString = map.get(keyExpectedElementsEdge);
			final String edgeBloomFilterFalsePositiveProbabilityString = map.get(keyFalsePositiveProbabilityEdge);
			final String edgeCacheSizeString = map.get(keyCacheSizeEdge);

			initialize(reportingIntervalSecondsString, 
					vertexLoadSavePathString, 
					vertexBloomFilterExpectedElementsString, vertexBloomFilterFalsePositiveProbabilityString, vertexCacheSizeString, 
					edgeLoadSavePathString, 
					edgeBloomFilterExpectedElementsString, edgeBloomFilterFalsePositiveProbabilityString, edgeCacheSizeString);

			printStats(true);

			return true;
		}catch(final Exception e){
			logger.log(Level.SEVERE, "Failed to initialize screen", e);
			return false;
		}
	}
	
	public final void initialize(final String reportingIntervalSecondsString, 
			final String vertexLoadSavePathString, 
			final String vertexBloomFilterExpectedElementsString, final String vertexBloomFilterFalsePositiveProbabilityString, final String vertexCacheSizeString,
			final String edgeLoadSavePathString, 
			final String edgeBloomFilterExpectedElementsString, final String edgeBloomFilterFalsePositiveProbabilityString, final String edgeCacheSizeString)
		throws Exception{
		
		final Result<Long> reportingIntervalSecondsResult = HelperFunctions.parseLong(reportingIntervalSecondsString, 10, Integer.MIN_VALUE, Integer.MAX_VALUE);
		if(reportingIntervalSecondsResult.error){
			throw new Exception("Invalid value for '"+keyReportingIntervalSeconds+"'='"+reportingIntervalSecondsString+"'. "
					+ reportingIntervalSecondsResult.errorMessage);
		}
		if(reportingIntervalSecondsResult.result > 0){
			this.reportingEnabled = true;
			this.reportingIntervalMillis = reportingIntervalSecondsResult.result.intValue() * 1000;
			this.lastReportedAtMillis = System.currentTimeMillis();
		}else{
			this.reportingEnabled = false;
		}
		
		if(vertexLoadSavePathString != null && edgeLoadSavePathString != null){
			if(vertexLoadSavePathString.equals(edgeLoadSavePathString)){
				throw new Exception("The value for '"+keyLoadSavePathVertex+"' and '"+keyLoadSavePathEdge+"' cannot be the same");
			}
		}
		
		loadBloomFilterAndCache(vertexLoadSavePathString, vertexBloomFilterExpectedElementsString, vertexBloomFilterFalsePositiveProbabilityString, 
				vertexCacheSizeString, true);
		loadBloomFilterAndCache(edgeLoadSavePathString, edgeBloomFilterExpectedElementsString, edgeBloomFilterFalsePositiveProbabilityString,
				edgeCacheSizeString, false);
		
	}

	private final String getLoadSavePathKeyFor(final boolean isForVertex){
		if(isForVertex){
			return keyLoadSavePathVertex;
		}else{
			return keyLoadSavePathEdge;
		}
	}

	private final String getExpectedElementsKeyFor(final boolean isForVertex){
		if(isForVertex){
			return keyExpectedElementsVertex;
		}else{
			return keyExpectedElementsEdge;
		}
	}

	private final String getFalsePositiveKeyFor(final boolean isForVertex){
		if(isForVertex){
			return keyFalsePositiveProbabilityVertex;
		}else{
			return keyFalsePositiveProbabilityEdge;
		}
	}

	private final String getCacheSizeKeyFor(final boolean isForVertex){
		if(isForVertex){
			return keyCacheSizeVertex;
		}else{
			return keyCacheSizeEdge;
		}
	}

	@SuppressWarnings("unchecked")
	private final void loadBloomFilterAndCache(String loadSavePathString,
			final String expectedElementsString, final String falsePositiveString, final String cacheSizeString,
			final boolean isForVertex) throws Exception{
		final String logName = isForVertex ? "Vertex" : "Edge";

		boolean loadFromFile = false;

		if(loadSavePathString != null && loadSavePathString.trim().isEmpty()){
			loadSavePathString = null;
		}
		if(loadSavePathString != null){
			try{
				final File file = new File(loadSavePathString);
				if(file.exists()){
					if(file.isDirectory()){
						throw new Exception("The path is a directory but expected a file");
					}else{
						if(!file.canRead()){
							throw new Exception("The path is not readable");
						}
						loadFromFile = true;
					}
				}else{
					loadFromFile = false;
				}

				FileUtility.pathMustBeAWritableFile(loadSavePathString);
			}catch(Exception e){
				throw new Exception("Invalid path for '"+getLoadSavePathKeyFor(isForVertex)+"': '"+loadSavePathString+"'", e);
			}
		}

		spade.core.BloomFilter<String> bloomFilter = null;
		List<String> cacheEntries = null;
		Integer cacheSize = null;
		
		if(loadFromFile){
			try(final ObjectInputStream objectInputStream = new ObjectInputStream(
					new FileInputStream(new File(loadSavePathString).getAbsolutePath()))){
				bloomFilter = (spade.core.BloomFilter<String>)objectInputStream.readObject();
				if(bloomFilter != null){
					logger.log(Level.INFO,
							logName + " BloomFilter initialized from file: " + loadSavePathString + " [falsePositiveProbability="
									+ bloomFilter.getFalsePositiveProbability() + ", " + "expectedElements="
									+ bloomFilter.getExpectedBitsPerElement() + "]");
					logger.log(Level.INFO, "Keys ignored: ['"+getExpectedElementsKeyFor(isForVertex)+"', '"+getFalsePositiveKeyFor(isForVertex)+"']");
					cacheSize = (Integer)objectInputStream.readObject();
					if(cacheSize != null){
						cacheEntries = (List<String>)objectInputStream.readObject();
						if(cacheEntries != null){
							logger.log(Level.INFO,
									logName + " Cache initialized from file: " + loadSavePathString + " [cacheSize=" + cacheSize + "]");
							logger.log(Level.INFO, "Key ignored: ['"+getCacheSizeKeyFor(isForVertex)+"']");
						}else{
							// Fall back to creating from arguments
						}
					}else{
						// Fall back to creating from arguments
					}
				}else{
					// Fall back to creating from arguments
				}
			}catch(Exception e){
				throw new Exception("Invalid "+logName+" BloomFilter file format: " + loadSavePathString, e);
			}
		}

		if(bloomFilter == null){
			final Result<Long> expectedElementsResult = HelperFunctions.parseLong(expectedElementsString, 10, 1, Integer.MAX_VALUE);
			if(expectedElementsResult.error){
				throw new Exception("Invalid "+logName+" BloomFilter expected elements count value with key '"+getExpectedElementsKeyFor(isForVertex)+"'. "
						+ "Must be a positive integer but is '"+expectedElementsString+"'");
			}

			final Result<Double> falsePositiveResult = HelperFunctions.parseDouble(falsePositiveString, 0, 1);
			if(falsePositiveResult.error){
				throw new Exception("Invalid "+logName+" BloomFilter false positive probability value with key '"+getFalsePositiveKeyFor(isForVertex)+"'. "
						+ "Must be between 0 and 1 (inclusive) but is '"+falsePositiveString+"'");
			}

			bloomFilter = new spade.core.BloomFilter<String>(falsePositiveResult.result, expectedElementsResult.result.intValue());
			logger.log(Level.INFO,
					logName + " BloomFilter initialized from arguments: " + "[falsePositiveProbability="
							+ String.format("%.9f", falsePositiveResult.result) + ", "
							+ "expectedElements=" + expectedElementsResult.result.intValue() + "]");
		}

		LRUCache<String, Object> cache = null;

		if(cacheEntries == null){
			final Result<Long> cacheSizeResult = HelperFunctions.parseLong(cacheSizeString, 10, 0, Integer.MAX_VALUE);
			if(cacheSizeResult.error){
				throw new Exception("Invalid "+logName+" '"+getCacheSizeKeyFor(isForVertex)+"' value. Must be non-negative: " + cacheSizeResult.errorMessage);
			}
			cache = new LRUCache<String, Object>(cacheSizeResult.result.intValue());
		}else{
			cache = new LRUCache<String, Object>(cacheSize);
			for(int x = cacheEntries.size() - 1; x >= 0; x--){
				final String cacheEntryKey = cacheEntries.get(x);
				if(cacheEntryKey != null){
					cache.put(cacheEntryKey, blankObject);
					if(cache.hasExceededMaximumSize()){
						cache.evict();
						break;
					}
				}
			}
		}

		if(isForVertex){
			this.cacheVertex = cache;
			this.loadSavePathVertex = loadSavePathString;
			this.bloomFilterVertex = bloomFilter;
		}else{
			this.cacheEdge = cache;
			this.loadSavePathEdge = loadSavePathString;
			this.bloomFilterEdge = bloomFilter;
		}
		
		if(loadSavePathString != null){
			logger.log(Level.INFO, logName + " BloomFilter would be saved at path '"+loadSavePathString+"' on shutdown");
		}else{
			logger.log(Level.INFO, logName + " BloomFilter would NOT be saved on shutdown");
		}
		
		logger.log(Level.INFO, logName + " cache created with max size '"+cache.getMaximumSize()+"'");
	}

	@Override
	public boolean blockVertex(final AbstractVertex vertex){
		if(vertex != null){
			final String hashCode = vertex.bigHashCode();
			if(hashCode != null){
				final boolean block;
				synchronized(lockObject){
					block = block(hashCode, this.bloomFilterVertex, this.cacheVertex);
				}
				if(block){
					verticesBlocked++;
				}else{
					verticesNotBlocked++;
				}
				return block;
			}
		}
		return true;
	}

	@Override
	public boolean blockEdge(final AbstractEdge edge){
		if(edge != null){
			final String hashCode = edge.bigHashCode();
			if(hashCode != null){
				final boolean block;
				synchronized(lockObject){
					block = block(hashCode, this.bloomFilterEdge, this.cacheEdge);
				}
				if(block){
					edgeBlocked++;
				}else{
					edgesNotBlocked++;
				}
				return block;
			}
		}
		return true;
	}
	
	private final synchronized boolean block(final String hashCode, 
			final spade.core.BloomFilter<String> bloomFilter, final LRUCache<String, Object> cache){
		printStats(false);
		
		if(bloomFilter.contains(hashCode)){
			if(cache.get(hashCode) == null){
				cache.put(hashCode, blankObject);
				while(cache.hasExceededMaximumSize()){
					cache.evict();
				}
				return false;
			}else{
				return true;
			}
		}else{
			// Not in bloomfilter. Must put so do NOT block
			bloomFilter.add(hashCode);
			cache.put(hashCode, blankObject);
			while(cache.hasExceededMaximumSize()){
				cache.evict();
			}
			return false;
		}
	}
	
	public final void reset(){
		synchronized(lockObject){
			this.bloomFilterVertex.clear();
			this.bloomFilterEdge.clear();
			this.cacheVertex.clear();
			this.cacheEdge.clear();
		}
	}
	
	public final Object getVertexCacheValueForStorage(final String hashCode){
		synchronized(lockObject){
			return getCacheValueForStorage(cacheVertex, hashCode);
		}
	}
	
	public final Object getEdgeCacheValueForStorage(final String hashCode){
		synchronized(lockObject){
			return getCacheValueForStorage(cacheEdge, hashCode);
		}
	}
	
	public final void setVertexCacheValueForStorage(final String hashCode, final Object value){
		synchronized(lockObject){
			setCacheValueForStorage(bloomFilterVertex, cacheVertex, hashCode, value);
		}
	}
	
	public final void setEdgeCacheValueForStorage(final String hashCode, final Object value){
		synchronized(lockObject){
			setCacheValueForStorage(bloomFilterEdge, cacheEdge, hashCode, value);
		}
	}
	
	public final void unsetAllVertexCacheValuesForStorage(){
		synchronized(lockObject){
			final List<String> keyList = cacheVertex.getKeysInLRUAccessOrder();
			for(int x = keyList.size() - 1; x >= 0; x--){
				final String key = keyList.get(x);
				cacheVertex.put(key, blankObject);
			}
		}
	}
	
	private final Object getCacheValueForStorage(final LRUCache<String, Object> cache, final String hashCode){
		if(hashCode == null){
			return null;
		}
		final Object value = cache.get(hashCode);
		if(value == null){
			return null;
		}
		if(value == blankObject){
			return null;
		}
		return value;
	}
	
	private final void setCacheValueForStorage(
			final spade.core.BloomFilter<String> bloomFilter, final LRUCache<String, Object> cache, final String hashCode, final Object value){
		if(hashCode == null){
			return;
		}
		if(value == null){
			cache.put(hashCode, blankObject);
		}else{
			cache.put(hashCode, value);
		}
		if(!bloomFilter.contains(hashCode)){
			bloomFilter.add(hashCode);
		}
		while(cache.hasExceededMaximumSize()){
			cache.evict();
		}
	}
	
	private final void printStats(final boolean force){
		if(force || (reportingEnabled && (System.currentTimeMillis() - lastReportedAtMillis >= reportingIntervalMillis))){
			logger.log(Level.INFO, "verticesBlocked=" + verticesBlocked + ", verticesNotBlocked=" + verticesNotBlocked + ", "
					+ "edgesBlocked=" + edgeBlocked + ", edgesNotBlocked=" + edgesNotBlocked);
			lastReportedAtMillis = System.currentTimeMillis();
		}
	}

	@Override
	public boolean shutdown(){
		synchronized(lockObject){
			if(this.loadSavePathVertex != null){
				saveBloomFilterAndCache(this.bloomFilterVertex, this.cacheVertex, this.loadSavePathVertex, "Vertex");
				this.bloomFilterVertex.clear();
			}
			if(this.loadSavePathEdge != null){
				saveBloomFilterAndCache(this.bloomFilterEdge, this.cacheEdge, this.loadSavePathEdge, "Edge");
				this.bloomFilterEdge.clear();
			}
			if(this.cacheEdge != null){
				this.cacheEdge.clear();
			}
			if(this.cacheVertex != null){
				this.cacheVertex.clear();
			}
		}
		
		printStats(true);
		
		return true;
	}
	
	private final void saveBloomFilterAndCache(final spade.core.BloomFilter<String> bloomFilter, 
			final LRUCache<String, Object> cache,
			final String path, final String logName){
		ObjectOutputStream objectOutputStream = null;
		try{
			objectOutputStream = new ObjectOutputStream(new FileOutputStream(path));
			objectOutputStream.writeObject(bloomFilter);
			objectOutputStream.writeObject(cache.getMaximumSize());
			objectOutputStream.writeObject(cache.getKeysInLRUAccessOrder());
			logger.log(Level.INFO, logName+" BloomFilter saved to path: " + path);
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to save "+logName+" BloomFilter at path: " + path, e);
		}finally{
			if(objectOutputStream != null){
				try{ objectOutputStream.close(); }catch(Exception e){}
			}
		}
	}

}
