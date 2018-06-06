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

import java.io.File;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;

public class CommonFunctions {

	private static final Logger logger = Logger.getLogger(CommonFunctions.class.getName());
	// Group 1: key
    // Group 2: value
    private static final Pattern pattern_key_value = Pattern.compile("(\\w+)=\"*((?<=\")[^\"]+(?=\")|([^\\s]+))\"*");
	
    /**
     * Converts a string of the format [a="b" c=d e=f] into a map of key values
     * Any portions of the string not matching the pattern [a="b"] or [c=d] are ignored
     * 
     * Uses the pattern {@link #pattern_key_value pattern_key_value}
     * 
     * @param messageData string to parse
     * @return a hashmap
     */
    public static Map<String, String> parseKeyValPairs(String messageData) {
    	/*
         * Takes a string with keyvalue pairs and returns a Map Input e.g.
         * "key1=val1 key2=val2" etc. Input string validation is callee's
         * responsibility
         */
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
    
    /**
     * Convenience wrapper function for Integer.parseInt. Suppresses the exception and returns
     * the given default value in that case
     * 
     * @param string string to parse
     * @param defaultValue value to return in case of exception
     * @return integer representation of the string
     */
    public static Integer parseInt(String string, Integer defaultValue){
    	try{
    		return Integer.parseInt(string);
    	}catch(Exception e){
    		return defaultValue;
    	}
    }
    
    /**
     * Convenience wrapper function for Long.parseLong. Suppresses the exception and returns
     * the given default value in that case
     * 
     * @param str string to parse
     * @param defaultValue value to return in case of exception
     * @return long representation of the string
     */
    public static Long parseLong(String str, Long defaultValue){
    	try{
    		return Long.parseLong(str);
    	}catch(Exception e){
    		return defaultValue;
    	}
    }
    
    /**
     * Convenience wrapper function for Double.parseDouble. Suppresses the exception and returns
     * the given default value in that case
     * 
     * @param str string to parse
     * @param defaultValue value to return in case of exception
     * @return double representation of the string
     */
    public static Double parseDouble(String str, Double defaultValue){
    	try{
    		return Double.parseDouble(str);
    	}catch(Exception e){
    		return defaultValue;
    	}
    }
    
    /**
     * Convenience function to get a map with keys converted to lowercase.
     * 
     * Responsibility of the caller to make sure that different keys don't 
     * become the same after converted to lowercase.
     * 
     * Throws NPE if the map is the null
     * 
     * @param map map to convert
     * @return a hashmap
     */
    public static <T> Map<String, T> makeKeysLowerCase(Map<String, T> map){
    	Map<String, T> resultMap = new HashMap<String, T>();
    	for(Map.Entry<String, T> entry : map.entrySet()){
    		resultMap.put(entry.getKey().toLowerCase(), entry.getValue());
    	}
    	return resultMap;
    }
    
    /**
     * Convenience function to check if a string is null or is empty
     * 
     * The str argument is trimmed before the empty check
     * 
     * @param str string to check
     * @return true if null or empty, otherwise false
     */
    public static boolean isNullOrEmpty(String str){
    	if(str == null){
    		return true;
    	}else{
    		return str.trim().isEmpty();
    	}
    }
    
    /**
     * Decodes the hex string. Null if failed.
     * 
     * @param hexString string in hex format
     * @return converted ascii string
     */
    public static String decodeHex(String hexString){
		if(hexString == null){
			return null;
		}else{
			try{
				return new String(Hex.decodeHex(hexString.toCharArray()));
			}catch(Exception e){
				// ignore
				return null;
			}
		}
	}
    
    public static boolean bigIntegerEquals(BigInteger a, BigInteger b){
		if(a == null && b == null){
			return true;
		}else if((a == null && b != null) || (a != null && b == null)){
			return false;
		}else{
			return a.equals(b);
		}
	}
    
    /**
     * Create hex string from ascii
     * 
     * @param string
     * @return converted hex string
     */
    public static String encodeHex(String string){
    	return Hex.encodeHexString(String.valueOf(string).getBytes());
    }

    public static void closePrintSizeAndDeleteExternalMemoryMap(String id, ExternalMemoryMap<?, ?> map){
    	if(map != null){
    		try{
    			map.close();
    		}catch(Exception e){
    			logger.log(Level.WARNING, id + ": Failed to close external map", e);
    		}
    		BigInteger sizeBytes = null;
    		try{
    			sizeBytes = map.getSizeOfPersistedDataInBytes();
    			if(sizeBytes == null){
    				logger.log(Level.INFO, id + ": Failed to get size of external map");
    			}
    		}catch(Exception e){
    			logger.log(Level.WARNING, id + ": Failed to get size of external map", e);
    		}
    		if(sizeBytes != null){
    			String displaySize = FileUtils.byteCountToDisplaySize(sizeBytes);
    			logger.log(Level.INFO, id + ": Size of the external map on disk: {0}", displaySize);
    		}
    		try{
    			map.delete();
    		}catch(Exception e){
    			logger.log(Level.WARNING, id + ": Failed to delete external map", e);
    		}
    	}else{
    		logger.log(Level.WARNING, id + ": NULL external map");
    	}
    }

    public static <X, Y extends Serializable> ExternalMemoryMap<X, Y> createExternalMemoryMapInstance(String id,
    		String cacheSizeValue, String bloomfilterFalsePositiveProbValue, String bloomfilterExpectedElementsCountValue,
    		String parentDBDirPathValue, String dbDirAndNameValue, String reportingIntervalSecondsValue,
    		Hasher<X> hasher) throws Exception{

    	String exceptionPrefix = id + ": ExternalMemoryMap creation: ";

    	if(isNullOrEmpty(id)){
    		throw new Exception(exceptionPrefix + 
    				"NULL/Empty map id: "+id+".");
    	}
    	if(isNullOrEmpty(cacheSizeValue)){
    		throw new Exception(exceptionPrefix + 
    				"NULL/Empty cache size: "+cacheSizeValue+".");
    	}
    	if(isNullOrEmpty(bloomfilterFalsePositiveProbValue)){
    		throw new Exception(exceptionPrefix + 
    				"NULL/Empty Bloom filter false positive probability: "+bloomfilterFalsePositiveProbValue+".");
    	}
    	if(isNullOrEmpty(bloomfilterExpectedElementsCountValue)){
    		throw new Exception(exceptionPrefix + 
    				"NULL/Empty Bloom filter expected number of elements: "+bloomfilterExpectedElementsCountValue+".");
    	}
    	if(isNullOrEmpty(parentDBDirPathValue)){
    		throw new Exception(exceptionPrefix + 
    				"NULL/Empty external DB parent path: "+parentDBDirPathValue+".");
    	}
    	if(isNullOrEmpty(dbDirAndNameValue)){
    		throw new Exception(exceptionPrefix + 
    				"NULL/Empty external DB name: "+dbDirAndNameValue+".");
    	}

    	Integer cacheSize = CommonFunctions.parseInt(cacheSizeValue, null);
    	Double falsePositiveProb = CommonFunctions.parseDouble(bloomfilterFalsePositiveProbValue, null);
    	Integer expectedNumberOfElements = CommonFunctions.parseInt(bloomfilterExpectedElementsCountValue, null);
    	Long reporterInterval = CommonFunctions.parseLong(reportingIntervalSecondsValue, null); 

    	if(cacheSize == null){
    		throw new Exception(exceptionPrefix + "Non-integer cache size: "+cacheSizeValue+".");
    	}
    	if(falsePositiveProb == null){
    		throw new Exception(exceptionPrefix + 
    				"Non-double Bloom filter false positive probability: "+bloomfilterFalsePositiveProbValue+".");
    	}
    	if(expectedNumberOfElements == null){
    		throw new Exception(exceptionPrefix + 
    				"Non-integer Bloom filter expected number of elements: "+bloomfilterExpectedElementsCountValue+".");
    	}
    	if(reporterInterval == null && !isNullOrEmpty(reportingIntervalSecondsValue)){
    		throw new Exception(exceptionPrefix + 
    				"Non-integer reporting interval: " + reportingIntervalSecondsValue + ".");
    	}

    	if(cacheSize < 1){
    		throw new Exception(exceptionPrefix + 
    				"Cache size cannot be less than 1: "+cacheSize+".");
    	}
    	if(falsePositiveProb < 0 || falsePositiveProb > 1){
    		throw new Exception(exceptionPrefix + 
    				"False positive probability must be in the range [0-1]: "+falsePositiveProb+".");
    	}
    	if(expectedNumberOfElements < 1){
    		throw new Exception(exceptionPrefix + 
    				"Expected number of elements cannot be less than 1: "+expectedNumberOfElements+".");
    	}
    	if(reporterInterval != null && reporterInterval < 0){
    		throw new Exception(exceptionPrefix + 
    				"Reporting interval cannot be less than 0: "+reporterInterval+".");
    	}
    	if(dbDirAndNameValue.contains(File.separator)){
    		throw new Exception(exceptionPrefix + 
    				"Invalid '"+File.separator+"' character in external DB name: "+dbDirAndNameValue+".");
    	}		

    	try{
    		if(!FileUtility.createDirectories(parentDBDirPathValue)){
    			throw new Exception("");
    		}
    	}catch(Exception e){
    		throw new Exception(exceptionPrefix + e.getMessage() + ". Failed to create dbs parent directory: " + parentDBDirPathValue);
    	}

    	dbDirAndNameValue += "_" + System.currentTimeMillis();
    	String dbPath = parentDBDirPathValue + File.separator + dbDirAndNameValue;

    	try{
    		if(FileUtility.doesPathExist(dbPath)){
    			throw new Exception("DB path already in use");
    		}
    	}catch(Exception e){
    		throw new Exception(exceptionPrefix + e.getMessage() + ". Failed to check if db path exists: " + dbPath);
    	}
    	
    	try{
    		if(!FileUtility.createDirectories(dbPath)){
    			throw new Exception("");
    		}
    	}catch(Exception e){
    		throw new Exception(exceptionPrefix + e.getMessage() + ". Failed to create db directory: " + dbPath);
    	}

    	try{
    		ExternalStore<Y> db = new BerkeleyDB<Y>(dbPath, dbDirAndNameValue);
    		ExternalMemoryMap<X, Y> map = new ExternalMemoryMap<X, Y>(
    				id, cacheSize, db, falsePositiveProb, expectedNumberOfElements);
    		if(reporterInterval != null){
    			reporterInterval *= 1000;
    			map.printStats(reporterInterval);
    		}
    		if(hasher != null){
    			map.setKeyHashFunction(hasher);
    		}
    		logger.log(Level.INFO, id+": ExternalMemoryMap created with params: cache size={0}, "
    				+ "db path={1}, db name={2}, false positive prob={3}, expected number of elements={4}, "
    				+ "reporting interval in millis={5}", new Object[]{
    						cacheSize, dbPath, dbDirAndNameValue, falsePositiveProb, expectedNumberOfElements,
    						reporterInterval
    		});
    		return map;
    	}catch(Exception e){
    		try{
    			if(FileUtility.doesPathExist(dbPath)){
    				FileUtility.deleteFile(dbPath);
    			}
    		}catch(Exception e2){
    			
    		}
    		throw new Exception(exceptionPrefix + "Exception: " + e.getMessage());
    	}
    }
    
    public static List<String> mapToLines(Map<String, String> map, String keyValueSeparator){
    	if(map == null){
    		return null;
    	}else{
    		List<String> lines = new ArrayList<String>();
    		for(Map.Entry<String, String> entry : map.entrySet()){
    			String key = entry.getKey();
    			String value = entry.getValue();
    			lines.add(key + keyValueSeparator + value);
    		}
    		return lines;
    	}
    }
}
