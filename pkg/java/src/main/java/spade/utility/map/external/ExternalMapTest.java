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

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;

import spade.utility.HelperFunctions;
import spade.utility.FileUtility;
import spade.utility.Result;
import spade.utility.map.external.cache.CacheArgument;
import spade.utility.map.external.cache.LRUCacheArgument;
import spade.utility.map.external.screen.BloomFilterArgument;
import spade.utility.map.external.screen.ScreenArgument;
import spade.utility.map.external.store.StoreArgument;
import spade.utility.map.external.store.db.berkeleydb.BerkeleyDBArgument;
import spade.utility.map.external.store.db.leveldb.LevelDBArgument;

public class ExternalMapTest{

	protected static void testMain(String testDirPath) throws Exception{
		final String testMapid = "testMapId";
		final int lruSizeInt = 100;
		
		final String bloomFilterSavePath = testDirPath + File.separator + "savedBloomFilter";
		final String bloomFilterLoadPath = bloomFilterSavePath;
		final String bloomFilterExpected = "1000000";
		final String bloomFilterProb = "0.000001";
		final String newScreenSavedArgument = String.format("%s=%s %s=%s %s=%s", 
				BloomFilterArgument.keyExpectedElements, bloomFilterExpected,
				BloomFilterArgument.keyFalsePositiveProbability, bloomFilterProb,
				BloomFilterArgument.keySavePath, bloomFilterSavePath);
		final String loadScreenArgument = String.format("%s=%s",
				BloomFilterArgument.keyLoadPath, bloomFilterLoadPath);
		
		final String lruSize = String.valueOf(lruSizeInt);
		final String cacheArgument = String.format("%s=%s", 
				LRUCacheArgument.keySize, lruSize);
		
		final String berkeleyEnvPath = testDirPath + File.separator + "bdbtest";
		final String berkeleyDbName = "testdbname";
		final String bdb_deleteOnClose = "false";
		final String berkeleyArgument = String.format("%s=%s %s=%s %s=%s", 
				BerkeleyDBArgument.keyEnvironmentPath, berkeleyEnvPath,
				BerkeleyDBArgument.keyDatabaseName, berkeleyDbName,
				BerkeleyDBArgument.keyDeleteDbOnClose, bdb_deleteOnClose);
		
		final String levelDbPath = testDirPath + File.separator + "ldbtest";
		final String ldb_deleteOnClose = "false";
		final String leveldbArgument = String.format("%s=%s %s=%s", 
				LevelDBArgument.keyDatabasePath, levelDbPath,
				LevelDBArgument.keyDeleteDbOnClose, ldb_deleteOnClose);
		
		final String flushOnClose = "true";
		final String mapArgument = String.format("%s=%s", 
				ExternalMapArgument.keyMapFlushOnClose, flushOnClose);
		
		System.out.println(new File("").getCanonicalPath());
		System.out.println(newScreenSavedArgument);
		System.out.println(loadScreenArgument);
		System.out.println(cacheArgument);
		System.out.println(berkeleyArgument);
		System.out.println(leveldbArgument);
		System.out.println(mapArgument);
		
		System.out.println();
		
		final Result<ExternalMapArgument> parseResult = ExternalMapManager.parseArgument(
				testMapid, 
				"BloomFilter", newScreenSavedArgument, 
				"LRU", cacheArgument, 
//				"BerkeleyDB", berkeleyArgument,
				"LevelDB", leveldbArgument,
				mapArgument);
		
		if(parseResult.error){
			System.out.println(parseResult.toErrorString());
		}else{
			final ExternalMapArgument argument = parseResult.result;
			
			final Result<ExternalMap<Integer, Integer>> mapResult = ExternalMapManager.create(argument);
			
			if(mapResult.error){
				System.out.println(mapResult.toErrorString());
			}else{
				final ExternalMap<Integer, Integer> map = mapResult.result;
				
				final Result<ExternalMap<Integer, Integer>> recreateMapResult = ExternalMapManager.create(argument);
				
				if(!recreateMapResult.error){
					System.out.println("Should not have been able to create a map with same arguments again without close");
				}else{
					System.out.println();
					System.out.println("Successfully failed to recreate map with the same arguments");
					System.out.println(recreateMapResult.toErrorString());
					System.out.println();
					
					final int total = lruSizeInt * 2;
					
					// NULL should not be allowed
					map.put(null, (total * 2) + 1);
					map.put((total * 2), null);
					map.put(null, null);
					
					// Clean get. All should be null.
					for(int i = 0; i < total; i++){
						Integer j = null;
						if((j = map.get(i)) != null){
							System.err.println("Non-null for '"+i+"': '"+j+"'");
						}
					}
					
					// Clean remove. There should be no error.
					for(int i = 0; i < total; i++){
						map.remove(i);
					}
					
					// Clean contains. There should be no error and nothing should be present.
					for(int i = 0; i < total; i++){
						if(map.contains(i)){
							System.err.println("Map should not have contained '"+i+"'");
						}
					}
										
					// Put data
					for(int i = 0; i < total; i++){
						map.put(i, total - i);
					}
					
					// Should contain all values
					for(int i = 0; i < total; i++){
						if(!map.contains(i)){
							System.err.println("Map should have contained '"+i+"'");
						}
					}
					
					// Should get all values
					for(int i = 0; i < total; i++){
						Integer j = map.get(i);
						if(!HelperFunctions.objectsEqual(j, (total - i))){
							System.err.println("'"+j+"' != '"+(total - i)+"' for '"+i+"'");
						}
					}
					
					// Remove all values
					for(int i = 0; i < total; i++){
						map.remove(i);
					}
					
					// Clear
					map.clear();
					
					// Put different data
					for(int i = 0; i < total; i++){
						map.put(i, (-1 * (total - i)));
					}
					
					// Close for reopening
					map.close();
					
					final Result<ExternalMapArgument> reopenParseResult = ExternalMapManager.parseArgument(
							testMapid, 
							"BloomFilter", loadScreenArgument, 
							"LRU", cacheArgument, 
//							"BerkeleyDB", berkeleyArgument,
							"LevelDB", leveldbArgument,
							mapArgument);
					
					if(reopenParseResult.error){
						System.out.println(reopenParseResult.toErrorString());
					}else{
						final ExternalMapArgument reopenArgument = reopenParseResult.result;
						
						final Result<ExternalMap<Integer, Integer>> reopenMapResult = ExternalMapManager.create(reopenArgument);
						
						if(reopenMapResult.error){
							System.out.println(reopenMapResult.toErrorString());
						}else{
							final ExternalMap<Integer, Integer> reopenedMap = reopenMapResult.result;
							
							// Get. Should contain all
							// Should get all values
							for(int i = 0; i < total; i++){
								Integer j = reopenedMap.get(i);
								if(!HelperFunctions.objectsEqual(j, (-1 * (total - i)))){
									System.err.println("'"+j+"' != '"+(-1 * (total - i))+"' for '"+i+"'");
								}
							}
							
							reopenedMap.close();
						}
					}
				}
			}
		}
	}
	
	protected static void parseArgumentsTest(){
		Set<ExternalMapConfig> configs = getMapConfigs();
		Set<ExternalMapConfig> validConfigs = new HashSet<ExternalMapConfig>();
		int invalidCount = 0;
		
		Set<ScreenArgument> uniqueScreenArgs = new HashSet<ScreenArgument>();
		Set<CacheArgument> uniqueCacheArgs = new HashSet<CacheArgument>();
		Set<StoreArgument> uniqueStoreArgs = new HashSet<StoreArgument>();
		
		for(ExternalMapConfig config : configs){
			Result<ExternalMapArgument> result = ExternalMapManager.parseArgument(
					config.mapId, 
					config.screenName, config.screenArgument, 
					config.cacheName, config.cacheArgument, 
					config.storeName, config.storeArgument, 
					config.mapArgument);
			if(!result.error){
				validConfigs.add(config);
				uniqueScreenArgs.add(result.result.screenArgument);
				uniqueCacheArgs.add(result.result.cacheArgument);
				uniqueStoreArgs.add(result.result.storeArgument);
			}else{
				invalidCount++;
			}
		}
		
		System.out.println("Total possible configs: " + configs.size());
		System.out.println("Total valid configs: " + validConfigs.size());
		System.out.println("Total invalid configs: " + invalidCount);
		
//		for(ExternalMapConfig validConfig : validConfigs){
//			System.out.println(validConfig);
//		}
		System.out.println();		
		
		System.out.println("Unique screen args: " + uniqueScreenArgs.size());
		System.out.println("Unique cache args: " + uniqueCacheArgs.size());
		System.out.println("Unique store args: " + uniqueStoreArgs.size());
		
		System.out.println();
		
		for(ScreenArgument arg : uniqueScreenArgs){
			System.out.println(arg);
		}
		
		System.out.println();
		
		for(CacheArgument arg : uniqueCacheArgs){
			System.out.println(arg);
		}
		
		System.out.println();
		
		for(StoreArgument arg : uniqueStoreArgs){
			System.out.println(arg);
		}
		
		System.out.println();
	}
	
	public static void main(String[] args) throws Exception{
//		args = new String[]{"/tmp/externalmaptest"};
		if(args.length != 1){
			System.err.println("Invalid arguments");
			System.err.println("ExternalMapTest <testDirectoryPath> [must not exist]");
		}else{
			String testDirPath = args[0];
			Result<Boolean> existsResult = FileUtility.doesPathExistResult(testDirPath);
			if(existsResult.error){
				System.err.println("Failed to check if test directory exist");
				System.err.println(existsResult.toErrorString());
			}else{
				if(existsResult.result){
					System.err.println("Test directory already exists. Must not exist.");
				}else{
					Result<Boolean> createResult = FileUtility.createDirectoriesResult(testDirPath);
					if(createResult.error){
						System.err.println("Failed to create test directory");
						System.err.println(createResult.toErrorString());
					}else{
						if(!createResult.result){
							System.err.println("Failed to create directory: '"+testDirPath+"'");
						}else{
							try{
								testMain(testDirPath);
							}catch(Exception e){
								System.err.println("Failed test execution");
								e.printStackTrace(System.err);
							}finally{
								try{
									FileUtils.forceDelete(new File(testDirPath));
								}catch(Exception e){
									System.err.println("Failed to delete directory: '"+testDirPath+"'");
									e.printStackTrace(System.err);
								}
							}
						}
					}
				}
			}
		}
	}
	
	/////////////////////////////////////////////////////////////////////////////////////////////////
	
	private static Set<ExternalMapConfig> getMapConfigs(){
		Set<ExternalMapConfig> set = new HashSet<ExternalMapConfig>();
		String[] mapIds = {"validMapId", null, "", " ", "invalid space id", "invalid.dot.id"};
		String[] screenNames = {null, "", " ", "BloomFilter"};
		String[] cacheNames = {null, "", " ", "LRU"};
		String[] storeNames = {null, "", " ", "BerkeleyDB", "LevelDB"};
		
		Map<String, Set<String>> screenArgs = new HashMap<String, Set<String>>();
		for(String screenName : screenNames){
			screenArgs.put(screenName, getScreenArguments(screenName));
		}
		
		Map<String, Set<String>> cacheArgs = new HashMap<String, Set<String>>();
		for(String cacheName : cacheNames){
			cacheArgs.put(cacheName, getCacheArguments(cacheName));
		}
		
		Map<String, Set<String>> storeArgs = new HashMap<String, Set<String>>();
		for(String storeName : storeNames){
			storeArgs.put(storeName, getStoreArguments(storeName));
		}
		
		for(String mapId : mapIds){
			for(String screenName : screenNames){
				for(String cacheName : cacheNames){
					for(String storeName : storeNames){
						Set<String> mapArgsSet = getMapArguments();
						Set<String> screenArgsSet = screenArgs.get(screenName);
						Set<String> cacheArgsSet = cacheArgs.get(cacheName);
						Set<String> storeArgsSet = storeArgs.get(storeName);
						for(String mapArg : mapArgsSet){
							for(String screenArg : screenArgsSet){
								for(String cacheArg : cacheArgsSet){
									for(String storeArg : storeArgsSet){
										set.add(new ExternalMapConfig(mapId, mapArg, 
												screenName, screenArg, 
												cacheName, cacheArg, 
												storeName, storeArg)
												);
									}
								}
							}
						}
					}
				}
			}
		}
		return set;
	}
	
	private static Set<String> getScreenArguments(String screenName){
		if("BloomFilter".equals(screenName)){
			return getBloomFilterArguments();
		}else{
			Set<String> args = new HashSet<String>();
			args.add(null);
			return args;
		}
	}
	
	private static Set<String> getCacheArguments(String cacheName){
		if("LRU".equals(cacheName)){
			return getLRUArguments();
		}else{
			Set<String> args = new HashSet<String>();
			args.add(null);
			return args;
		}
	}
	
	private static Set<String> getStoreArguments(String storeName){
		if("LevelDB".equals(storeName)){
			return getLevelDBArguments();
		}else if("BerkeleyDB".equals(storeName)){
			return getBerkeleyDBArguments();
		}else{
			Set<String> args = new HashSet<String>();
			args.add(null);
			return args;
		}
	}
	
	private static Set<String> getBloomFilterArguments(){
		Set<String> args = new HashSet<String>();
		String[] expectedElements = {null, "", " ", "-1", "0", String.valueOf(Long.MAX_VALUE), "100000"};
		String[] falsePositives = {null, "", " ", "-1", "1.01", "0.00001"};
		String[] savePaths = {null, "", " ", "bloomfiltersavepath"};
		String[] loadPaths = {null, "", " ", "bloomfilterloadpath"};
		for(String expected : expectedElements){
			for(String prob : falsePositives){
				for(String spath : savePaths){
					for(String lpath : loadPaths){
						String arg = null;
						if(expected != null){
							String kv = BloomFilterArgument.keyExpectedElements + "=" + expected + " ";
							arg = arg == null ? (kv) : (arg + kv);
						}
						
						if(prob != null){
							String kv = BloomFilterArgument.keyFalsePositiveProbability + "=" + prob + " ";
							arg = arg == null ? (kv) : (arg + kv);
						}
						
						if(lpath != null){
							String kv = BloomFilterArgument.keyLoadPath + "=" + lpath + " ";
							arg = arg == null ? (kv) : (arg + kv);
						}
						
						if(spath != null){
							String kv = BloomFilterArgument.keySavePath + "=" + spath + " ";
							arg = arg == null ? (kv) : (arg + kv);
						}
						
						if(arg != null && arg.length() > 0){
							arg = arg.substring(0, arg.length() - 1);
						}
						
						args.add(arg);
					}
				}
			}
		}	
		return args;
	}
	
	private static Set<String> getLRUArguments(){
		Set<String> args = new HashSet<String>();
		String[] sizes = {null, "", " ", "-1", "0", String.valueOf(Long.MAX_VALUE), "1000"};
		for(String size : sizes){
			String arg = null;
			if(size != null){
				String kv = LRUCacheArgument.keySize + "=" + size + " ";
				arg = arg == null ? (kv) : (arg + kv);
			}
			
			if(arg != null && arg.length() > 0){
				arg = arg.substring(0, arg.length() - 1);
			}
			
			args.add(arg);
		}	
		return args;
	}
	
	private static Set<String> getBerkeleyDBArguments(){
		Set<String> args = new HashSet<String>();
		String[] dbPaths = {null, "", " ", "berkeleydatabasedir"};
		String[] dbNames = {null, "", " ", "berkeleydatabasename"};
		String[] deletes = {null, "", " ", "true", "false"};
		for(String dbPath : dbPaths){
			for(String dbName : dbNames){
				for(String delete : deletes){
					String arg = null;
					if(dbPath != null){
						String kv = BerkeleyDBArgument.keyEnvironmentPath + "=" + dbPath + " ";
						arg = arg == null ? (kv) : (arg + kv);
					}
					
					if(dbName != null){
						String kv = BerkeleyDBArgument.keyDatabaseName + "=" + dbName + " ";
						arg = arg == null ? (kv) : (arg + kv);
					}
					
					if(delete != null){
						String kv = BerkeleyDBArgument.keyDeleteDbOnClose + "=" + delete + " ";
						arg = arg == null ? (kv) : (arg + kv);
					}
										
					if(arg != null && arg.length() > 0){
						arg = arg.substring(0, arg.length() - 1);
					}
					
					args.add(arg);
				}
			}
		}
		return args;
	}
	
	private static Set<String> getLevelDBArguments(){
		Set<String> args = new HashSet<String>();
		String[] dbPaths = {null, "", " ", "leveldatabasedir"};
		String[] deletes = {null, "", " ", "true", "false"};
		for(String dbPath : dbPaths){
			for(String delete : deletes){
				String arg = null;
				if(dbPath != null){
					String kv = LevelDBArgument.keyDatabasePath + "=" + dbPath + " ";
					arg = arg == null ? (kv) : (arg + kv);
				}
				
				if(delete != null){
					String kv = BerkeleyDBArgument.keyDeleteDbOnClose + "=" + delete + " ";
					arg = arg == null ? (kv) : (arg + kv);
				}
									
				if(arg != null && arg.length() > 0){
					arg = arg.substring(0, arg.length() - 1);
				}
				
				args.add(arg);
			}
		}
		return args;
	}
	
	private static Set<String> getMapArguments(){
		Set<String> args = new HashSet<String>();
		String[] flushes = {null, "", " ", "true", "false"};
		for(String flush : flushes){
			String arg = null;
			if(flush != null){
				String kv = ExternalMapArgument.keyMapFlushOnClose + "=" + flush + " ";
				arg = arg == null ? (kv) : (arg + kv);
			}
								
			if(arg != null && arg.length() > 0){
				arg = arg.substring(0, arg.length() - 1);
			}
			
			args.add(arg);
		}
		return args;
	}
		
	private static class ExternalMapConfig{
		private final String mapId, mapArgument;
		private final String screenName, screenArgument;
		private final String cacheName, cacheArgument;
		private final String storeName, storeArgument;
		private ExternalMapConfig(String mapId, String mapArgument,
				String screenName, String screenArgument,
				String cacheName, String cacheArgument,
				String storeName, String storeArgument){
			this.mapId = mapId; this.mapArgument = mapArgument;
			this.screenName = screenName; this.screenArgument = screenArgument;
			this.cacheName = cacheName; this.cacheArgument = cacheArgument;
			this.storeName = storeName; this.storeArgument = storeArgument;
		}
		@Override
		public String toString(){
			return "ExternalMapConfig [mapId=" + mapId + ", mapArgument=" + mapArgument + ", screenName=" + screenName
					+ ", screenArgument=" + screenArgument + ", cacheName=" + cacheName + ", cacheArgument="
					+ cacheArgument + ", storeName=" + storeName + ", storeArgument=" + storeArgument + "]";
		}
		@Override
		public int hashCode(){
			final int prime = 31;
			int result = 1;
			result = prime * result + ((cacheArgument == null) ? 0 : cacheArgument.hashCode());
			result = prime * result + ((cacheName == null) ? 0 : cacheName.hashCode());
			result = prime * result + ((mapArgument == null) ? 0 : mapArgument.hashCode());
			result = prime * result + ((mapId == null) ? 0 : mapId.hashCode());
			result = prime * result + ((screenArgument == null) ? 0 : screenArgument.hashCode());
			result = prime * result + ((screenName == null) ? 0 : screenName.hashCode());
			result = prime * result + ((storeArgument == null) ? 0 : storeArgument.hashCode());
			result = prime * result + ((storeName == null) ? 0 : storeName.hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj){
			if(this == obj)
				return true;
			if(obj == null)
				return false;
			if(getClass() != obj.getClass())
				return false;
			ExternalMapConfig other = (ExternalMapConfig)obj;
			if(cacheArgument == null){
				if(other.cacheArgument != null)
					return false;
			}else if(!cacheArgument.equals(other.cacheArgument))
				return false;
			if(cacheName == null){
				if(other.cacheName != null)
					return false;
			}else if(!cacheName.equals(other.cacheName))
				return false;
			if(mapArgument == null){
				if(other.mapArgument != null)
					return false;
			}else if(!mapArgument.equals(other.mapArgument))
				return false;
			if(mapId == null){
				if(other.mapId != null)
					return false;
			}else if(!mapId.equals(other.mapId))
				return false;
			if(screenArgument == null){
				if(other.screenArgument != null)
					return false;
			}else if(!screenArgument.equals(other.screenArgument))
				return false;
			if(screenName == null){
				if(other.screenName != null)
					return false;
			}else if(!screenName.equals(other.screenName))
				return false;
			if(storeArgument == null){
				if(other.storeArgument != null)
					return false;
			}else if(!storeArgument.equals(other.storeArgument))
				return false;
			if(storeName == null){
				if(other.storeName != null)
					return false;
			}else if(!storeName.equals(other.storeName))
				return false;
			return true;
		}
	}
}
