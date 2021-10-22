/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2021 SRI International

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
package spade.utility;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import spade.utility.map.external.ExternalMapArgument;
import spade.utility.map.external.ExternalMapManager;

public class ArgumentFunctions{
	
	public static boolean mustParseBoolean(final String key, final Map<String, String> map) throws Exception{
		if(map == null){
			throw new Exception("NULL map to get the value from for key '" + key + "'");
		}
		final String value = map.get(key);
		final Result<Boolean> result = HelperFunctions.parseBoolean(value);
		if(result.error){
			throw new Exception("Failed to parse boolean value for key '" + key + "'. Error: " + result.toErrorString());
		}
		return result.result;
	}
	
	public static int mustParseInteger(final String key, final Map<String, String> map) throws Exception{
		if(map == null){
			throw new Exception("NULL map to get the value from for key '" + key + "'");
		}
		final String value = map.get(key);
		final Result<Long> result = HelperFunctions.parseLong(value, 10, Integer.MIN_VALUE, Integer.MAX_VALUE);
		if(result.error){
			throw new Exception("Failed to parse integer value for key '" + key + "'. Error: " + result.toErrorString());
		}
		return result.result.intValue();
	}

	private static long mustParseLongRanged(final String key, final Map<String, String> map,
			final long min, final long max) throws Exception{
		if(map == null){
			throw new Exception("NULL map to get the value from for key '" + key + "'");
		}
		final String value = map.get(key);
		final Result<Long> result = HelperFunctions.parseLong(value, 10, min, max);
		if(result.error){
			throw new Exception("Failed to parse long value for key '" + key + "'. Error: " + result.toErrorString());
		}
		return result.result.longValue();
	}

	public static long mustParseLong(final String key, final Map<String, String> map) throws Exception{
		return mustParseLongRanged(key, map, Long.MIN_VALUE, Long.MAX_VALUE);
	}

	public static long mustBeGreaterThanZero(final String key, final Map<String, String> map) throws Exception{
		return mustParseLongRanged(key, map, 1L, Long.MAX_VALUE);
	}

	public static List<String> mustParseCommaSeparatedValues(final String key, final Map<String, String> map) throws Exception{
		if(map == null){
			throw new Exception("NULL map to get the value from for key '" + key + "'");
		}
		final String value = map.get(key);
		if(value == null){
			throw new Exception("NULL value for key '" + key + "'"); 
		}
		
		final List<String> result = new ArrayList<String>();
		
		final String[] tokens = value.split(",");
		for(final String token : tokens){
			result.add(token.trim());
		}
		
		return result;
	}

	public static final List<String> allValuesMustBeNonEmpty(final List<String> values, final String key) throws Exception{
		if(values.isEmpty()){
			throw new Exception("Empty value in '"+key+"'");
		}
		final List<String> result = new ArrayList<String>();
		for(final String value : values){
			if(HelperFunctions.isNullOrEmpty(value)){
				throw new Exception("NULL/Empty value in '"+key+"'");
			}
			result.add(value.trim());
		}
		return result;
	}

	public static String mustParseNonEmptyString(final String key, final Map<String, String> map) throws Exception{
		if(map == null){
			throw new Exception("NULL map to get the value from for key '" + key + "'");
		}
		final String value = map.get(key);
		if(value == null){
			throw new Exception("NULL value for key '" + key + "'"); 
		}
		if(value.trim().isEmpty()){
			throw new Exception("Empty value for key '" + key + "'"); 
		}
		return value.trim();
	}

	public static String mustParseNonNullString(final String key, final Map<String, String> map) throws Exception{
		if(map == null){
			throw new Exception("NULL map to get the value from for key '" + key + "'");
		}
		final String value = map.get(key);
		if(value == null){
			throw new Exception("NULL value for key '" + key + "'");
		}
		return value.trim();
	}

	public static String mustParseHost(final String key, final Map<String, String> map) throws Exception{
		if(map == null){
			throw new Exception("NULL map to get the value from for key '" + key + "'");
		}
		final String value = map.get(key);
		if(value == null || value.trim().isEmpty()){
			throw new Exception("NULL/Empty value for key '" + key + "'"); 
		}
		try{
			InetAddress.getByName(value.trim());
			return value.trim();
		}catch(Exception e){
			throw new Exception("Not a valid host name for key '" + key + "'");
		}
	}
	
	public static String mustParseClass(final String key, final Map<String, String> map) throws Exception{
		if(map == null){
			throw new Exception("NULL map to get the value from for key '" + key + "'");
		}
		final String value = map.get(key);
		if(value == null || value.trim().isEmpty()){
			throw new Exception("NULL/Empty value for key '" + key + "'"); 
		}
		try{
			Class.forName(value.trim());
			return value.trim();
		}catch(Exception e){
			throw new Exception("Not a valid class name for key '" + key + "'");
		}
	}

	public static ExternalMapArgument mustParseExternalMapArgument(final String key, final Map<String, String> map) throws Exception{
		String mapId = map.get(key);
		if(HelperFunctions.isNullOrEmpty(mapId)){
			throw new Exception("NULL/Empty value for '"+key+"'");
		}
		mapId = mapId.trim();
		final Result<ExternalMapArgument> artifactToProcessMapArgumentResult =  ExternalMapManager.parseArgumentFromMap(mapId, map);
		if(artifactToProcessMapArgumentResult.error){
			throw new Exception("Invalid arguments for external map with id: " + mapId + ". " 
					+ artifactToProcessMapArgumentResult.toErrorString());
		}
		return artifactToProcessMapArgumentResult.result;
	}

	public static String mustParseWritableFilePath(final String key, final Map<String, String> map) throws Exception{
		final String path = mustParseNonEmptyString(key, map);
		try{
			FileUtility.pathMustBeAWritableFile(path);
			return path;
		}catch(Exception e){
			throw new Exception("Not a writable path value for key '" + key + "'", e);
		}
	}

	public static String mustParseReadableFilePath(final String key, final Map<String, String> map) throws Exception{
		final String path = mustParseNonEmptyString(key, map);
		try{
			FileUtility.pathMustBeAReadableFile(path);
			return path;
		}catch(Exception e){
			throw new Exception("Not a readable path value for key '" + key + "'", e);
		}
	}
}
