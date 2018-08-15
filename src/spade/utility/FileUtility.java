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
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

public class FileUtility{

	public static Pattern constructRegexFromFile(String filepath) throws Exception{
		StringBuilder suffixes = new StringBuilder(), prefixes = new StringBuilder(), inlines = new StringBuilder();
		StringBuilder currentString = new StringBuilder();
		List<String> lines = FileUtility.readLines(filepath);
		for(String line : lines){
			line = line.trim();
			if(line.startsWith("#")){
				if(line.contains("prefixes")){
					currentString = prefixes;
				}else if(line.contains("suffixes")){
					currentString = suffixes;
				}else if(line.contains("inlines")){
					currentString = inlines;
				}
			}else{
				if(!line.isEmpty()){
					currentString.append("(").append(line).append(")|");
				}
			}
		}
		String regex = "";
		if(prefixes.length() > 0){
			prefixes.deleteCharAt(prefixes.length() - 1);
			regex += "(^("+prefixes.toString()+"))|";
		}
		if(inlines.length() > 0){
			inlines.deleteCharAt(inlines.length() - 1);
			regex += "(^("+inlines.toString()+")$)|";
		}
		if(suffixes.length() > 0){
			suffixes.deleteCharAt(suffixes.length() - 1);
			regex += "(("+suffixes.toString()+")$)";
		}
		if(regex.endsWith("|")){
			regex = regex.substring(0, regex.length() - 1);
		}
		return Pattern.compile(regex);
	}
	
	public static Map<String, String> readOPM2ProvTCFile(String filepath) throws Exception{
		Map<String, String> map = new HashMap<String, String>();
		List<String> lines = FileUtility.readLines(filepath);
		for(String line : lines){
			line = line.trim();
			if(!line.isEmpty()){
				String tokens[] = line.split("=>");
				if(tokens.length == 2){
					String from = tokens[0].trim();
					String to = tokens[1].trim();
					map.put(from, to);
				}
			}
		}		
		return map;
	}
	
	public static Map<String, String> readOPM2ProvTCFileReversed(String filepath) throws Exception{
		Map<String, String> map = new HashMap<String, String>();
		List<String> lines = FileUtility.readLines(filepath);
		for(String line : lines){
			line = line.trim();
			if(!line.isEmpty()){
				String tokens[] = line.split("=>");
				if(tokens.length == 2){
					String from = tokens[1].trim();
					String to = tokens[0].trim();
					map.put(from, to);
				}
			}
		}		
		return map;
	}
	
	public static Map<String, String> readConfigFileAsKeyValueMap(String filepath, String keyValueSeparator) throws Exception{
		Map<String, String> map = new HashMap<String, String>();
		List<String> lines = FileUtility.readLines(filepath);
		for(String line : lines){
			line = line.trim();
			if(!line.isEmpty() && !line.startsWith("#")){
				String tokens[] = line.split(keyValueSeparator);
				if(tokens.length == 2){
					String from = tokens[0].trim();
					String to = tokens[1].trim();
					map.put(from, to);
				}
			}
		}		
		return map;
	}
	
	/**
	 * Returns file system path.
	 */
	private static File getFile(String path) throws Exception{
		return new File(path);
	}
	
	/**
	 * Must be valid file system path. 
	 */
	public static boolean isPathValid(String path) throws Exception{
		getFile(path);
		return true;
	}
	
	/**
	 * Path must be valid. True if exists else false. Otherwise exception.
	 */
	public static boolean doesPathExist(String path) throws Exception{
		if(isPathValid(path)){
			return getFile(path).exists();
		}else{
			throw new Exception("Invalid path");
		}
	}
	
	/**
	 * Path must be valid, and must exist. True if file else false. Otherwise exception.
	 */
	public static boolean isFile(String path) throws Exception{
		if(doesPathExist(path)){
			return getFile(path).isFile();
		}else{
			throw new Exception("Path does not exist");
		}
	}

	/**
	 * Path must be valid, and must exist. True if directory else false. Otherwise exception.
	 */
	public static boolean isDirectory(String path) throws Exception{
		if(doesPathExist(path)){
			return getFile(path).isDirectory();
		}else{
			throw new Exception("Path does not exist");
		}
	}
	
	/**
	 * Path must be valid, must exist, and must be a file. True if readable else false. Otherwise exception.
	 */
	public static boolean isFileReadable(String path) throws Exception{
		if(isFile(path)){
			return getFile(path).canRead();
		}else{
			throw new Exception("Path is not a file");
		}
	}
	
	/**
	 * Path must be valid, must exist, and must be a file. True if writable else false. Otherwise exception.
	 */
	public static boolean isFileWritable(String path) throws Exception{
		if(isFile(path)){
			return getFile(path).canWrite();
		}else{
			throw new Exception("Path is not a file");
		}
	}
	
	/**
	 * Path must be valid, must exist, and must be a file. True if deleted else false. Otherwise exception.
	 */
	public static boolean deleteFile(String path) throws Exception{
		if(isFileWritable(path)){
			return getFile(path).delete();
		}else{
			throw new Exception("File is not writable");
		}
	}
	
	/**
	 * Path must be valid, must exist, and must be a directory. True if deleted else false. Otherwise exception.
	 */
	public static boolean deleteDirectory(String path) throws Exception{
		if(isDirectory(path)){
			FileUtils.deleteDirectory(getFile(path));
			return true;
		}else{
			throw new Exception("Path is not a directory");
		}
	}
	
	/**
	 * Path must be valid, if it exists then must be a file, if doesn't exist then it is created. True if created 
	 * else false. Otherwise exception.
	 */
	public static boolean createFile(String path) throws Exception{
		if(!doesPathExist(path)){
			return getFile(path).createNewFile();
		}else{
			// path already exists
			if(isFile(path)){
				return true;
			}else{
				throw new Exception("Directory already exists at path");
			}
		}
	}
	
	/**
	 * Path must be valid, must not exist. True if created else false. Otherwise exception.
	 */
	public static boolean createNewFile(String path) throws Exception{
		if(!doesPathExist(path)){
			return getFile(path).createNewFile();
		}else{
			// path already exists
			if(isFile(path)){
				throw new Exception("File already exists at path");
			}else{
				throw new Exception("Directory already exists at path");
			}
		}
	}
	
	/**
	 * Path must be valid, if it exists then must be a directory, if doesn't exist then it is created. True if created 
	 * else false. Otherwise exception.
	 */
	public static boolean createDirectory(String path) throws Exception{
		if(!doesPathExist(path)){
			return getFile(path).mkdir();
		}else{
			// path already exists
			if(isDirectory(path)){
				return true;
			}else{
				throw new Exception("File already exists at path");
			}
		}
	}
	
	/**
	 * Path must be valid, must not exist. True if created else false. Otherwise exception.
	 */
	public static boolean createNewDirectory(String path) throws Exception{
		if(!doesPathExist(path)){
			return getFile(path).mkdir();
		}else{
			// path already exists
			if(isFile(path)){
				throw new Exception("File already exists at path");
			}else{
				throw new Exception("Directory already exists at path");
			}
		}
	}
	
	/**
	 * Path must be valid, if it exists then must be a directory, if doesn't exist then it is created along with all 
	 * other missing parent directories. True if created else false. Otherwise exception.
	 */
	public static boolean createDirectories(String path) throws Exception{
		if(!doesPathExist(path)){
			return getFile(path).mkdirs();
		}else{
			// path already exists
			if(isDirectory(path)){
				return true;
			}else{
				throw new Exception("File already exists at path");
			}
		}
	}
	
	/**
	 * Path must be valid, must not exist. True if created along with all other missing parent directories else false. 
	 * Otherwise exception.
	 */
	public static boolean createNewDirectories(String path) throws Exception{
		if(!doesPathExist(path)){
			return getFile(path).mkdirs();
		}else{
			// path already exists
			if(isFile(path)){
				throw new Exception("File already exists at path");
			}else{
				throw new Exception("Directory already exists at path");
			}
		}
	}
	
	/**
	 * Path must be valid, exist, be a file, and be readable.
	 */
	public static List<String> readLines(String path) throws Exception{
		if(isFileReadable(path)){
			return FileUtils.readLines(getFile(path));
		}else{
			throw new Exception("Not a readable file");
		}
	}
	
	/**
	 * Path must be valid, exist, be a file, and be writable.
	 */
	public static void writeLines(String path, List<String> lines) throws Exception{
		if(doesPathExist(path)){
			if(!isFileWritable(path)){
				throw new Exception("File is not writable");
			}
		}else{
			if(!createFile(path)){
				throw new Exception("Failed to create file");
			}
		}
		FileUtils.writeLines(getFile(path), lines);
	}
	
	/**
	 * Path must be valid, and exist.
	 */
	public static BigInteger getSizeInBytes(String path) throws Exception{
		if(doesPathExist(path)){
			return FileUtils.sizeOfAsBigInteger(getFile(path));
		}else{
			throw new Exception("Path does not exist");
		}
	}
}
