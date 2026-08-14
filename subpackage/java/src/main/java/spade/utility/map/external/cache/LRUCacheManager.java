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
package spade.utility.map.external.cache;

import java.util.HashMap;
import java.util.Map;

import spade.utility.HelperFunctions;
import spade.utility.Result;

/**
 * LRU cache manager for external map
 */
public class LRUCacheManager extends CacheManager{

	public static final LRUCacheManager instance = new LRUCacheManager();
	private LRUCacheManager(){}
	
	/**
	 * Create LRUCache.
	 * Sample: "size=[1-n]"
	 * 
	 * @param arguments See above sample
	 */
	@Override
	public Result<CacheArgument> parseArgument(String arguments){
		if(HelperFunctions.isNullOrEmpty(arguments)){
			return Result.failed("NULL/Empty arguments");
		}else{
			Result<HashMap<String, String>> mapResult = HelperFunctions.parseKeysValuesInString(arguments);
			if(mapResult.error){
				return Result.failed("Failed to parse arguments to map", mapResult);
			}else{
				return parseArgument(mapResult.result);
			}
		}
	}

	/**
	 * Create LRUCache.
	 * Must contains valid values for keys: 'size'.
	 * All values must be non-null and non-empty.
	 * 
	 */
	@Override
	public Result<CacheArgument> parseArgument(Map<String, String> arguments){
		if(arguments == null){
			return Result.failed("NULL arguments");
		}else if(arguments.isEmpty()){
			return Result.failed("Empty arguments");
		}else{
			Result<Long> sizeResult = HelperFunctions.parseLong(
					arguments.get(LRUCacheArgument.keySize), 10, 0, Integer.MAX_VALUE);
			if(sizeResult.error){
				return Result.failed("Failed to parse '"+LRUCacheArgument.keySize+"'", sizeResult);
			}else{
				return Result.successful(new LRUCacheArgument(sizeResult.result.intValue()));
			}
		}
	}
	
	/**
	 * Validates the passed argument as the correct argument for this cache manager
	 * 
	 * @param genericArgument CacheArgument must be LRUCacheArgument
	 * @return LRUCacheArgument object otherwise error
	 */
	private Result<LRUCacheArgument> validateArgument(final CacheArgument genericArgument){
		if(genericArgument == null){
			return Result.failed("NULL argument");
		}else if(!genericArgument.getClass().equals(LRUCacheArgument.class)){
			return Result.failed("Cache argument class must be LRUCacheArgument but is '"+genericArgument.getClass()+"'");
		}else{
			LRUCacheArgument argument = (LRUCacheArgument)genericArgument;
			return Result.successful(argument);
		}
	}

	/**
	 * @param CacheArgument must be LRUCacheArgument
	 * @return Cache object or error
	 */
	@Override
	public <K, V> Result<Cache<K, V>> createFromArgument(CacheArgument genericArgument){
		Result<LRUCacheArgument> validResult = validateArgument(genericArgument);
		if(validResult.error){
			return Result.failed("Invalid cache argument", validResult);
		}else{
			LRUCacheArgument argument = validResult.result;
			if(argument == null){
				return Result.failed("NULL argument");
			}else{
				return Result.successful(new LRUCache<K, V>(argument.size));
			}
		}
	}

}
