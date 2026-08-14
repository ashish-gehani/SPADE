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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import spade.utility.HelperFunctions;
import spade.utility.Converter;
import spade.utility.FileUtility;
import spade.utility.Result;
import spade.utility.Serializable2ByteArrayConverter;
import spade.utility.map.external.cache.Cache;
import spade.utility.map.external.cache.CacheArgument;
import spade.utility.map.external.cache.CacheManager;
import spade.utility.map.external.screen.Screen;
import spade.utility.map.external.screen.ScreenArgument;
import spade.utility.map.external.screen.ScreenManager;
import spade.utility.map.external.store.Store;
import spade.utility.map.external.store.StoreArgument;
import spade.utility.map.external.store.StoreManager;

public class ExternalMapManager{

	public static Result<Boolean> validateMapId(String mapId){
		if(HelperFunctions.isNullOrEmpty(mapId)){
			return Result.failed("NULL/Empty external map id");
		}else if(mapId.contains(" ") || mapId.contains(".")){
			return Result.failed("External map id must not contain empty spaces or dot ('.'): '"+mapId+"'");
		}else{
			return Result.successful(true);
		}
	}
	
	public static Result<ExternalMapArgument> parseArgumentFromFile(String mapId, String filePath){
		Result<Boolean> validMapIdResult = validateMapId(mapId);
		if(validMapIdResult.error){
			return Result.failed("Invalid external map id", validMapIdResult);
		}else{
			if(HelperFunctions.isNullOrEmpty(filePath)){
				return Result.failed("NULL/Empty external map argument file path");
			}else{
				Result<HashMap<String, String>> mapResult = FileUtility.parseKeysValuesInConfigFile(filePath);
				if(mapResult.error){
					return Result.failed("Failed to read external map argument file as map", mapResult);
				}else{
					HashMap<String, String> map = mapResult.result;
					return parseArgumentFromMap(mapId, map);
				}
			}
		}
	}
	
	/**
	 * Expected key value:
	 * 
	 * <mapid>.argument=reportingSeconds= flushOnClose=
	 * <mapid>.screenName=
	 * <mapid>.screenArgument=
	 * <mapid>.cacheName=
	 * <mapid>.cacheArgument=
	 * <mapid>.storeName=
	 * <mapid>.storeArgument=
	 * 
	 * 
	 * @param mapId
	 * @param map
	 * @return
	 */
	public static Result<ExternalMapArgument> parseArgumentFromMap(String mapId, Map<String, String> map){
		Result<Boolean> validMapIdResult = validateMapId(mapId);
		if(validMapIdResult.error){
			return Result.failed("Invalid external map id", validMapIdResult);
		}else{
			if(map == null){
				return Result.failed("NULL map");
			}else{
				String mapArgumentString,
						screenNameString, screenArgumentString, 
						cacheNameString, cacheArgumentString,
						storeNameString, storeArgumentString;
				mapArgumentString = null;
				screenNameString = screenArgumentString = null;
				cacheNameString = cacheArgumentString = null;
				storeNameString = storeArgumentString = null;
				for(Map.Entry<String, String> entry : map.entrySet()){
					String key = entry.getKey();
					String value = entry.getValue();
					if(key != null){
						String keyTokens[] = key.split("\\.", 2);
						if(keyTokens.length == 2){
							String idPart = keyTokens[0].trim();
							String argNamePart = keyTokens[1].trim();
							if(idPart.equals(mapId)){
								if(argNamePart.equals(ExternalMapArgument.keyMapArgument)){
									mapArgumentString = value;
								}else if(argNamePart.equals(ExternalMapArgument.keyScreenName)){
									screenNameString = value;
								}else if(argNamePart.equals(ExternalMapArgument.keyScreenArgument)){
									screenArgumentString = value;
								}else if(argNamePart.equals(ExternalMapArgument.keyCacheName)){
									cacheNameString = value;
								}else if(argNamePart.equals(ExternalMapArgument.keyCacheArgument)){
									cacheArgumentString = value;
								}else if(argNamePart.equals(ExternalMapArgument.keyStoreName)){
									storeNameString = value;
								}else if(argNamePart.equals(ExternalMapArgument.keyStoreArgument)){
									storeArgumentString = value;
								}
							}
						}
					}
				}
				return parseArgument(mapId, 
						screenNameString, screenArgumentString, 
						cacheNameString, cacheArgumentString, 
						storeNameString, storeArgumentString,
						mapArgumentString);
			}
		}
	}
	
	public static Result<ExternalMapArgument> parseArgument(String mapId, 
			String screenNameString, String screenArgumentString,
			String cacheNameString, String cacheArgumentString,
			String storeNameString, String storeArgumentString,
			String mapArgumentString){
		Result<Boolean> validMapIdResult = validateMapId(mapId);
		if(validMapIdResult.error){
			return Result.failed("Invalid external map id", validMapIdResult);
		}else{
			Result<HashMap<String, String>> mapArgumentParseResult = HelperFunctions.parseKeysValuesInString(mapArgumentString);
			if(mapArgumentParseResult.error){
				return Result.failed("Failed to parse map argument", mapArgumentParseResult);
			}else{
				HashMap<String, String> mapArgumentMap = mapArgumentParseResult.result;
				String reportingSecondsString = mapArgumentMap.get(ExternalMapArgument.keyMapReportingSeconds);
				Long reportingIntervalMillis = null;
				if(reportingSecondsString != null){
					Result<Long> reportingResult = HelperFunctions.parseLong(reportingSecondsString, 10, 1, Integer.MAX_VALUE);
					if(reportingResult.error){
						return Result.failed("Invalid map reporting seconds", reportingResult);
					}else{
						reportingIntervalMillis = reportingResult.result * 1000;
					}
				}
				Result<Boolean> flushResult = HelperFunctions.parseBoolean(mapArgumentMap.get(ExternalMapArgument.keyMapFlushOnClose));
				if(flushResult.error){
					return Result.failed("Failed to parse flush on close value", flushResult);
				}else{
					boolean flushOnClose = flushResult.result;
					Result<ScreenArgument> screenResult = ScreenManager.parseArgument(screenNameString, screenArgumentString);
					if(screenResult.error){
						return Result.failed("Invalid screen argument", screenResult);
					}else{
						Result<CacheArgument> cacheResult = CacheManager.parseArgument(cacheNameString, cacheArgumentString);
						if(cacheResult.error){
							return Result.failed("Invalid cache argument", cacheResult);
						}else{
							Result<StoreArgument> storeResult = StoreManager.parseArgument(storeNameString, storeArgumentString);
							if(storeResult.error){
								return Result.failed("Invalid store argument", storeResult);
							}else{
								return Result.successful(new ExternalMapArgument(mapId, 
										screenResult.result, cacheResult.result, storeResult.result,
										reportingIntervalMillis, flushOnClose));
							}
						}
					}
				}
			}
		}
	}
	
	public static Result<Boolean> validateArgument(ExternalMapArgument argument){
		if(argument == null){
			return Result.failed("NULL argument");
		}else{
			Result<Boolean> validMapIdResult = validateMapId(argument.mapId);
			if(validMapIdResult.error){
				return validMapIdResult;
			}else if(argument.screenArgument == null){
				return Result.failed("NULL screen argument");
			}else if(argument.cacheArgument == null){
				return Result.failed("NULL cache argument");
			}else if(argument.storeArgument == null){
				return Result.failed("NULL store argument");
			}else{
				return Result.successful(true);
			}
		}
	}
	
	public static <K extends Serializable, V extends Serializable> Result<ExternalMap<K, V>> create(ExternalMapArgument argument){
		return create(argument, new Serializable2ByteArrayConverter<K>(), new Serializable2ByteArrayConverter<V>());
	}
	
	public static <K, V> Result<ExternalMap<K, V>> create(ExternalMapArgument argument,
			Converter<K, byte[]> keyConverter, Converter<V, byte[]> valueConverter){
		if(keyConverter == null){
			return Result.failed("NULL key converter");
		}else if(valueConverter == null){
			return Result.failed("NULL value converter");
		}else{
			Result<Boolean> argumentResult = validateArgument(argument);
			if(argumentResult.error){
				return Result.failed("Invalid external map argument", argumentResult);
			}else{
				Result<? extends Screen<K>> screenResult = ScreenManager.createScreen(argument.screenArgument);
				if(screenResult.error){
					return Result.failed("Failed to create screen", screenResult);
				}else{
					Screen<K> screen = screenResult.result;
					Result<? extends Cache<K, V>> cacheResult = CacheManager.createCache(argument.cacheArgument);
					if(cacheResult.error){
						return Result.failed("Failed to create cache", cacheResult);
					}else{
						Cache<K, V> cache = cacheResult.result;
						Result<? extends Store<K, V>> storeResult = 
								StoreManager.createStore(argument.storeArgument, keyConverter, valueConverter);
						if(storeResult.error){
							return Result.failed("Failed to create store", storeResult);
						}else{
							Store<K, V> store = storeResult.result;
							return Result.successful(new ExternalMap<K, V>(argument.mapId, screen, cache, store, 
									argument.reportingIntervalMillis, argument.flushCacheOnClose));
						}
					}
				}
			}
		}
	}
}
