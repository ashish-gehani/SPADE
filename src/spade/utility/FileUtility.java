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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
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
		int lineNumber = 0;
		for(String line : lines){
			lineNumber++;
			line = line.trim();
			if(!line.isEmpty() && !line.startsWith("#")){
				String tokens[] = line.split("=>");
				if(tokens.length == 2){
					String from = tokens[0].trim();
					String to = tokens[1].trim();
					if(map.containsKey(from)){
						throw new Exception("Duplicate key '"+from+"' at line (#"+lineNumber+"): '" + line + "'");
					}
					map.put(from, to);
				}else{
					throw new Exception("Unexpected line (#"+lineNumber+"): '"+line+"'");
				}
			}
		}
		return map;
	}

	public static Map<String, String> readOPM2ProvTCFileReversed(String filepath) throws Exception{
		Map<String, String> map = new HashMap<String, String>();
		List<String> lines = FileUtility.readLines(filepath);
		int lineNumber = 0;
		for(String line : lines){
			lineNumber++;
			line = line.trim();
			if(!line.isEmpty() && !line.startsWith("#")){
				String tokens[] = line.split("=>");
				if(tokens.length == 2){
					String from = tokens[1].trim();
					String to = tokens[0].trim();
					if(map.containsKey(from)){
						throw new Exception("Duplicate key '"+from+"' at line (#"+lineNumber+"): '" + line + "'");
					}
					map.put(from, to);
				}else{
					throw new Exception("Unexpected line (#"+lineNumber+"): '"+line+"'");
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
	
	public static Result<File> getFileResult(String path){
		if(path == null){
			return Result.failed("Failed getFile: NULL path");
		}else{
			try{
				return Result.successful(new File(path).getAbsoluteFile());
			}catch(Exception e){
				return Result.failed("Failed getFile for path: '"+path+"'", e, null);
			}
		}
	}
	
	public static Result<Boolean> doesPathExistResult(String path){
		Result<File> result = getFileResult(path);
		if(result.error){
			return Result.failed(result.errorMessage, result.exception, null);
		}else{
			File file = result.result;
			try{
				return Result.successful(file.exists());
			}catch(SecurityException se){
				return Result.failed("Failed doesPathExist for path: '"+path+"'", se, null);
			}catch(Exception e){
				return Result.failed("Failed doesPathExist for path: '"+path+"'", e, null);
			}
		}
	}
	
	public static Result<String> getCanonicalPathResult(String path){
		Result<File> fileResult = getFileResult(path);
		if(fileResult.error){
			return Result.failed("Failed getCanonicalPath", fileResult);
		}else{
			try{
				return Result.successful(fileResult.result.getCanonicalPath());
			}catch(Exception e){
				return Result.failed("Failed getCanonicalPath for path: '"+path+"'", e, null);
			}
		}
	}
	
	public static Result<Boolean> isDirectoryResult(String path){
		Result<Boolean> result = doesPathExistResult(path);
		if(result.error){
			return result;
		}else{
			try{
				return Result.successful(new File(path).isDirectory());
			}catch(SecurityException se){
				return Result.failed("Failed isDirectory for path: '"+path+"'", se, null);
			}catch(Exception e){
				return Result.failed("Failed isDirectory for path: '"+path+"'", e, null);
			}
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
	
	public static Result<Boolean> createDirectoriesResult(String path){
		Result<File> getFileResult = getFileResult(path);
		if(getFileResult.error){
			return Result.failed("Failed createDirectories", getFileResult);
		}else{
			try{
				File file = getFileResult.result;
				boolean created = file.mkdirs();
				return Result.successful(created);
			}catch(SecurityException se){
				return Result.failed("Failed createDirectories for path: '"+path+"'", se, null);
			}catch(Exception e){
				return Result.failed("Failed createDirectories for path: '"+path+"'", e, null);
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
	
	/**
	 * Returns size in bytes of path whether file or directory
	 * 
	 * @param path filesystem path
	 * @return Size (bytes) in result or error
	 */
	public static Result<BigInteger> getSizeOnDiskInBytes(String path){
		try{
			return Result.successful(FileUtils.sizeOfAsBigInteger(new File(path)));
		}catch(Exception e){
			return Result.failed("Failed to get size of path: '"+path+"'", e, null);
		}
	}
	
	/**
	 * Converts the size in bytes to display size
	 * 
	 * @param sizeInBytes
	 * @return String
	 */
	public static String formatBytesSizeToDisplaySize(BigInteger sizeInBytes){
		if(sizeInBytes == null){
			return null;
		}else{
			return FileUtils.byteCountToDisplaySize(sizeInBytes);
		}
	}
	
	/**
	 * 
	 * @param filepath path of the config file
	 * @param ignoreCommentedLines whether to ignore the lines starting with '#' or not
	 * @return ArrayList in result or error
	 */
	public static Result<ArrayList<String>> readLinesInFile(String filepath, boolean ignoreCommentedLines){
		if(filepath == null){
			return Result.failed("NULL filepath to read lines from");
		}else{
			File file = new File(filepath);
			try(BufferedReader reader = new BufferedReader(new FileReader(file))){
				ArrayList<String> lines = new ArrayList<String>();
				String line = null;
				while((line = reader.readLine()) != null){
					if(ignoreCommentedLines){
						if(!line.trim().startsWith("#")){
							lines.add(line);
						}
					}else{
						lines.add(line);
					}
				}
				return Result.successful(lines);
			}catch(FileNotFoundException fnfe){
				return Result.failed("No file to read lines from at path: '" + filepath + "'");
			}catch(IOException ioe){
				return Result.failed("Failed to read file: '" + filepath + "'", ioe, null);
			}catch(Exception e){
				return Result.failed("Failed to read file:  '"+filepath+"'", e, null);
			}
		}
	}
	
	/**
	 * @param filepath path of the config file
	 * @param separator the string to split the line on
	 * @param ignoreCommentedLines whether to ignore lines starting with '#' or not
	 * @return HashMap in result or error
	 */
	public static Result<HashMap<String, String>> parseKeysValuesInConfigFile(String filepath, String separator, boolean ignoreCommentedLines){
		Result<ArrayList<String>> result = readLinesInFile(filepath, ignoreCommentedLines);
		if(result.error){
			return Result.failed("Failed to parse keys values", result);
		}else{
			HashMap<String, String> map = new HashMap<String, String>();
			for(String line : result.result){
				String tokens[] = line.split(separator, 2);
				if(tokens.length == 2){
					map.put(tokens[0].trim(), tokens[1].trim());
				}
			}
			return Result.successful(map);
		}
	}
	
	/**
	 * Expects the default SPADE config file format:
	 * 	1) One key value pair separated by '=' on each line
	 *  2) Lines started with '#' or empty lines are ignored 
	 * 
	 * @param filepath path of the config file
	 * @return HashMap in result or error
	 */
	public static Result<HashMap<String, String>> parseKeysValuesInConfigFile(String filepath){
		return parseKeysValuesInConfigFile(filepath, "=", true);
	}
	
}
