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
package spade.utility;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommonFunctions {

	// Group 1: key
    // Group 2: value
    private static final Pattern pattern_key_value = Pattern.compile("(\\w+)=\"*((?<=\")[^\"]+(?=\")|([^\\s]+))\"*");
	/*
     * Takes a string with keyvalue pairs and returns a Map Input e.g.
     * "key1=val1 key2=val2" etc. Input string validation is callee's
     * responsibility
     */
    public static Map<String, String> parseKeyValPairs(String messageData) {
    	Map<String, String> keyValPairs = new HashMap<String, String>();
    	if(messageData == null || messageData.trim().isEmpty()){
    		return keyValPairs;
    	}
        Matcher key_value_matcher = pattern_key_value.matcher(messageData);
        while (key_value_matcher.find()) {
            keyValPairs.put(key_value_matcher.group(1).trim(), key_value_matcher.group(2).trim());
        }
        return keyValPairs;
    }
    
    public static Integer parseInt(String string, int defaultValue){
    	try{
    		return Integer.parseInt(string);
    	}catch(Exception e){
    		return defaultValue;
    	}
    }
    
}
