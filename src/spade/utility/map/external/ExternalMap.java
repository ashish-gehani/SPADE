/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2019 SRI International

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
package spade.utility.map.external;

import java.math.BigInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.utility.FileUtility;
import spade.utility.map.external.cache.Cache;
import spade.utility.map.external.cache.Cache.CacheEntry;
import spade.utility.map.external.screen.Screen;
import spade.utility.map.external.store.Store;
import spade.utility.profile.Intervaler;

/**
 * A map backed by a persistent storage
 * 
 * @param <K> key 	Must be Serializable
 * @param <V> value Must be Serializable
 */
public class ExternalMap<K, V>{

	private static final Logger logger = Logger.getLogger(ExternalMap.class.getName());
	
	private BigInteger totalEvictions = BigInteger.ZERO,
			totalFalsePositives = BigInteger.ZERO,
			cacheHits = BigInteger.ZERO,
			cacheMisses = BigInteger.ZERO;
	
	private final Intervaler intervaler;
	
	public final boolean flushCacheOnClose;

	public final String mapId;
	
	private Screen<K> screen;
	private Cache<K, V> cache;
	private Store<K, V> store;
	
	/**
	 * Use ExternalMapArgument for correct initialization
	 * 
	 * @param mapId		id of the map
	 * @param screen	screen to use to check whether element ever put. Might return false positive.
	 * @param cache		cache to keep things in memory
	 * @param store		persistent db to evict data to from cache
	 */
	protected ExternalMap(String mapId, Screen<K> screen, Cache<K, V> cache, Store<K, V> store,
			Long reportingIntervalMillis, boolean flushCacheOnClose){
		this.mapId = mapId;
		this.screen = screen;
		this.cache = cache;
		this.store = store;
		
		if(reportingIntervalMillis != null){
			intervaler = new Intervaler(reportingIntervalMillis);
		}else{
			intervaler = null;
		}
		
		this.flushCacheOnClose = flushCacheOnClose;
	}
	
	/**
	 * Puts the current evictable item (if any) in cache to store
	 * 
	 * @throws Exception exception thrown by store
	 */
	private void _evict() throws Exception{
		CacheEntry<K, V> cacheEntry = cache.evict();
		if(cacheEntry != null){
			totalEvictions = totalEvictions.add(BigInteger.ONE);
			store.put(cacheEntry.key, cacheEntry.getValue());
		}
	}
	
	/**
	 * Put in the screen and cache always because value might be different
	 * Also put in the store if cache limit exceeded
	 * 
	 * @param key
	 * @param value
	 */
	public void put(K key, V value){
		checkInterval();
		// Might be value update
		try{
			screen.add(key);
		}catch(Exception e){
			logger.log(Level.SEVERE, mapId + ": Failed 'add' for screen", e);
		}
		try{
			cache.put(key, value);
		}catch(Exception e){
			logger.log(Level.SEVERE, mapId + ": Failed 'put' for cache", e);
		}
		// Evict if cache size exceeded
		try{
			while(cache.hasExceededMaximumSize()){
				_evict();
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, mapId + ": Failed 'evict' for cache and/or 'put' for store", e);
		}
	}
	
	/**
	 * If key not in screen then return null.
	 * If key is in screen then get from cache. 
	 * If is in cache then return that.
	 * If not in cache then get from store.
	 * If not in store then return null.
	 * If key is in store then add to cache, evict if cache limit exceeded and return the value.
	 * 
	 * @param key
	 * @return value/null
	 */
	public V get(K key){
		checkInterval();
		try{
			if(screen.contains(key)){
				// False positive possible
				try{
					V value = cache.get(key);
					if(value != null){
						// Exists in cache
						cacheHits = cacheHits.add(BigInteger.ONE);
						return value;
					}else{
						// Not in cache. Might have been evicted
						try{
							value = store.get(key);
							if(value != null){
								cacheMisses = cacheMisses.add(BigInteger.ONE);
								try{
									cache.put(key, value);
									try{
										while(cache.hasExceededMaximumSize()){
											_evict();
										}
									}catch(Exception e){
										logger.log(Level.SEVERE, mapId + ": Failed 'evict' for cache and/or 'put' for store", e);
									}
								}catch(Exception e){
									logger.log(Level.SEVERE, mapId + ": Failed 'put' for cache", e);
								}
								return value;
							}else{
								totalFalsePositives = totalFalsePositives.add(BigInteger.ONE);
								return null;
							}
						}catch(Exception e){
							logger.log(Level.SEVERE, mapId + ": Failed 'get' for store", e);
							return null;
						}
					}
				}catch(Exception e){
					logger.log(Level.SEVERE, mapId + ": Failed 'get' for cache", e);
					return null;
				}
			}else{
				// Definitely does not exist
				return null;
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, mapId + ": Failed 'contains' for screen", e);
			return null;
		}
	}
	
	public boolean contains(K key){
		checkInterval();
		try{
			if(screen.contains(key)){
				try{
					if(cache.contains(key)){
						return true;
					}else{
						try{
							return store.contains(key);
						}catch(Exception e){
							logger.log(Level.SEVERE, mapId + ": Failed 'contains' for store", e);
							return false;
						}
					}
				}catch(Exception e){
					logger.log(Level.SEVERE, mapId + ": Failed 'contains' for cache", e);
					return false;
				}
			}else{
				return false;
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, mapId + ": Failed 'contains' for screen", e);
			return false;
		}
	}
	
	/**
	 * If present in screen then remove from screen, cache and store indiscriminately
	 * 
	 * @param key
	 */
	public void remove(K key){
		checkInterval();
		try{
			if(screen.contains(key)){
				try{
					screen.remove(key);
				}catch(Exception e){
					logger.log(Level.SEVERE, mapId + ": Failed 'remove' for screen", e);
				}
				try{
					cache.remove(key);
				}catch(Exception e){
					logger.log(Level.SEVERE, mapId + ": Failed 'remove' for cache", e);
				}
				try{
					store.remove(key);
				}catch(Exception e){
					logger.log(Level.SEVERE, mapId + ": Failed 'remove' for store", e);
				}
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, mapId + ": Failed 'contains' for screen", e);
		}
	}
	
	/**
	 * Flush the cache to the store
	 * 
	 * @throws Exception exception thrown by store
	 */
	private void flushToStore() throws Exception{
		while(cache.getCurrentSize() > 0){
			_evict();
		}
	}
	
	/**
	 * Clear the screen, clear the cache and clear the store
	 */
	public void clear(){
		printStats();
		try{
			screen.clear();
		}catch(Exception e){
			logger.log(Level.SEVERE, mapId + ": Failed to clear screen", e);
		}
		try{
			cache.clear();
		}catch(Exception e){
			logger.log(Level.SEVERE, mapId + ": Failed to clear cache", e);
		}
		try{
			store.clear();
		}catch(Exception e){
			logger.log(Level.SEVERE, mapId + ": Failed to clear store", e);
		}
	}
	
	/**
	 * Close the screen, close the cache and close the store
	 */
	public void close(){
		printStats();
		if(flushCacheOnClose){
			try{
				flushToStore();
			}catch(Exception e){
				logger.log(Level.SEVERE, mapId + ": Failed to flush cache to store", e);
			}
		}
		printStats();
		try{
			screen.close();
		}catch(Exception e){
			logger.log(Level.SEVERE, mapId + ": Failed to close screen", e);
		}
		try{
			cache.close();
		}catch(Exception e){
			logger.log(Level.SEVERE, mapId + ": Failed to close cache", e);
		}
		try{
			store.close();
		}catch(Exception e){
			logger.log(Level.SEVERE, mapId + ": Failed to close store", e);
		}
	}
	
	/**
	 * Returns the size on disk as returned by the store
	 * 
	 * @return Result 		object which contains the error if failed or the size if success
	 * @throws Exception	exception thrown by store
	 */
	public BigInteger getSizeOnDiskInBytes() throws Exception{
		return store.getSizeOnDiskInBytes();
	}
	
	private void printStats(){
		BigInteger sizeBytes = null;
		try{
			sizeBytes = store.getSizeOnDiskInBytes();
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to get size of external map store", e);
		}
		
		String str = String.format("%s: evictions=%s, falsePositives=%s, cacheHits=%s, cacheMisses=%s, "
				+ "screenCount=%s, cacheCount=%s, storeSize=(%s)", 
				mapId, totalEvictions, totalFalsePositives, cacheHits, cacheMisses,
				screen.size(), cache.getCurrentSize(), FileUtility.formatBytesSizeToDisplaySize(sizeBytes));
		
		logger.log(Level.INFO, str);
	}
	
	private void checkInterval(){
		if(intervaler != null){
			if(intervaler.check()){
				printStats();
			}
		}
	}
	
}
