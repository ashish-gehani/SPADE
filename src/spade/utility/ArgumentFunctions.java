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
		return value;
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
}
