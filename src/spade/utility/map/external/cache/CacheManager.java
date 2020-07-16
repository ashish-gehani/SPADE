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

import java.util.Map;

import spade.utility.HelperFunctions;
import spade.utility.Result;
import spade.utility.profile.ReportingArgument;

/**
 * Cache manager to create the Cache for External map.
 */
public abstract class CacheManager{

	public abstract Result<CacheArgument> parseArgument(String arguments);
	public abstract Result<CacheArgument> parseArgument(Map<String, String> arguments);
	public abstract <K, V> Result<Cache<K, V>> createFromArgument(CacheArgument genericArgument);
	
	/**
	 * Parse Cache arguments
	 * 
	 * @param cacheNameString name of the screen as in the CacheName enum
	 * @param cacheArgumentString arguments for that screen
	 * @return CacheArgument or error
	 */
	public static Result<CacheArgument> parseArgument(String cacheNameString, String cacheArgumentString){
		if(HelperFunctions.isNullOrEmpty(cacheNameString)){
			return Result.failed("NULL/Empty cache name");
		}else{
			Result<ReportingArgument> reportingResult = ReportingArgument.parseReportingArgument(cacheArgumentString);
			if(reportingResult.error){
				return Result.failed("Invalid reporting argument", reportingResult);
			}else{
				ReportingArgument reportingArgument = reportingResult.result;
				Result<CacheName> cacheNameResult = HelperFunctions.parseEnumValue(CacheName.class, cacheNameString, true);
				if(cacheNameResult.error){
					return Result.failed("Failed cache name parsing", cacheNameResult);
				}else{
					CacheName cacheName = cacheNameResult.result;
					CacheManager cacheManager = cacheName.cacheManager;
					if(cacheManager == null){
						return Result.failed("Unhandled cache name: " + cacheName);
					}else{
						Result<CacheArgument> argResult = cacheManager.parseArgument(cacheArgumentString);
						if(argResult.error){
							return argResult;
						}else{
							argResult.result.setReportingArgument(reportingArgument);
							return argResult;
						}
					}
				}
			}
		}
	}
	
	/**
	 * Create cache for the external map
	 * 
	 * @param cacheArgument argument of the cache
	 * @return Cache object or error
	 */
	public static <K, V> Result<? extends Cache<K, V>> createCache(CacheArgument cacheArgument){
		if(cacheArgument == null){
			return Result.failed("NULL cache argument");
		}else{
			CacheName cacheName = cacheArgument.name;
			if(cacheName == null){
				return Result.failed("NULL cache name");
			}else{
				CacheManager cacheManager = cacheName.cacheManager;
				if(cacheManager == null){
					return Result.failed("Unhandled cache name: " + cacheName);
				}else{
					Result<Cache<K, V>> cacheResult = cacheManager.createFromArgument(cacheArgument);
					if(cacheResult.error){
						return cacheResult;
					}else{
						ReportingArgument reportingArgument = cacheArgument.getReportingArgument();
						if(reportingArgument == null){
							return cacheResult;
						}else{
							Cache<K,V> profiledCache = new ProfiledCache<K, V>(cacheResult.result, reportingArgument);
							return Result.successful(profiledCache);
						}
					}
				}
			}
		}
	}
}
