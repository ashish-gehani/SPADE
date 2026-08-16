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
import java.util.AbstractMap.SimpleEntry;
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
				String tokens[] = line.split(keyValueSeparator, 2);
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
	
	public static Result<ArrayList<SimpleEntry<String, String>>> parseKeyValueEntriesInConfigFile(
			final String filepath, final String separator, final boolean ignoreCommentedLines){
		final Result<ArrayList<String>> result = readLinesInFile(filepath, ignoreCommentedLines);
		if(result.error){
			return Result.failed("Failed to read keys values entries file", result);
		}else{
			final ArrayList<SimpleEntry<String, String>> entriesList = new ArrayList<SimpleEntry<String, String>>();
			for(final String line : result.result){
				final String tokens[] = line.split(separator, 2);
				if(tokens.length == 2){
					entriesList.add(new SimpleEntry<String, String>(tokens[0].trim(), tokens[1].trim()));
				}
			}
			return Result.successful(entriesList);
		}
	}
	
	/**
	 * @param filepath path of the config file
	 * @param separator the string to split the line on
	 * @param ignoreCommentedLines whether to ignore lines starting with '#' or not
	 * @return HashMap in result or error
	 */
	public static Result<HashMap<String, String>> parseKeysValuesInConfigFile(
			final String filepath, final String separator, final boolean ignoreCommentedLines){
		final Result<ArrayList<SimpleEntry<String, String>>> result = 
				parseKeyValueEntriesInConfigFile(filepath, separator, ignoreCommentedLines);
		if(result.error){
			return Result.failed("Failed to get keys values entries", result);
		}else{
			final HashMap<String, String> map = new HashMap<String, String>();
			for(final SimpleEntry<String, String> entry : result.result){
				map.put(entry.getKey(), entry.getValue());
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
	
	/**
	 * Tests whether the path is writable or not.
	 * 
	 * If the path exists then must be a file and must be writable
	 * If the path doesn't exist then the parent path must be a directory and writable
	 * 
	 * Throws exception with the appropriate message in case the test failed
	 * 
	 * @param path the path to the file
	 * @throws Exception
	 */
	public static void pathMustBeAWritableFile(final String path) throws Exception{
		if(HelperFunctions.isNullOrEmpty(path)){
			throw new Exception("NULL/Empty path");
		}
		final File outputFile = new File(path);
		try{
			if(outputFile.exists()){
				if(outputFile.isDirectory()){
					throw new Exception("The path is a directory but expected a file");
				}else{
					if(!outputFile.canWrite()){
						throw new Exception("The path is not writable");
					}
				}
			}else{
				final File parentFile = outputFile.getParentFile();
				if(parentFile == null){ // This means that the path in 'outputFile' was filesystem root path
					throw new Exception("The path is the filesystem root but expected a file");
				}else{
					if(!parentFile.exists()){
						throw new Exception("Path's parent directory does not exist");
					}else{
						if(!parentFile.isDirectory()){
							throw new Exception("Path's parent directory is not a directory");
						}else{
							if(!parentFile.canWrite()){
								throw new Exception("Path's parent directory is not writable");
							}
						}
					}
				}
			}
		}catch(Exception e){
			throw new Exception("Writable/creatable file test failed for path", e);
		}
	}
	
	/**
	 * Tests whether the path is a readable file or not.
	 * 
	 * Throws exception with the appropriate message in case the test failed
	 * 
	 * @param path the path to the file
	 * @throws Exception
	 */
	public static void pathMustBeAReadableFile(final String path) throws Exception{
		if(HelperFunctions.isNullOrEmpty(path)){
			throw new Exception("NULL/Empty path");
		}
		final File file = new File(path);
		try{
			if(file.exists()){
				if(file.isDirectory()){
					throw new Exception("The path is a directory but expected a file");
				}else{
					if(!file.canRead()){
						throw new Exception("The path is not readable");
					}
				}
			}else{
				throw new Exception("File does not exist");
			}
		}catch(Exception e){
			throw new Exception("Readable file test failed for path", e);
		}
	}
	
	/**
	 * Tests whether the path is a readable directory or not.
	 * 
	 * Throws exception with the appropriate message in case the test failed
	 * 
	 * @param path the path to the directory
	 * @throws Exception
	 */
	public static void pathMustBeAReadableDirectory(final String path) throws Exception{
		if(HelperFunctions.isNullOrEmpty(path)){
			throw new Exception("NULL/Empty path");
		}
		final File file = new File(path);
		try{
			if(file.exists()){
				if(file.isDirectory()){
					if(!file.canRead()){
						throw new Exception("The path is not readable");
					}
				}else{
					throw new Exception("The path is a file but expected a directory");
				}
			}else{
				throw new Exception("Directory does not exist");
			}
		}catch(Exception e){
			throw new Exception("Readable directory test failed for path", e);
		}
	}
	
	/**
	 * Tests whether the path is a readable and writable directory or not.
	 * 
	 * Throws exception with the appropriate message in case the test failed
	 * 
	 * @param path the path to the directory
	 * @throws Exception
	 */
	public static void pathMustBeAReadableWritableDirectory(final String path) throws Exception{
		if(HelperFunctions.isNullOrEmpty(path)){
			throw new Exception("NULL/Empty path");
		}
		final File file = new File(path);
		try{
			if(file.exists()){
				if(file.isDirectory()){
					if(!file.canRead()){
						throw new Exception("The path is not readable");
					}
					if(!file.canWrite()){
						throw new Exception("The path is not writable");
					}
				}else{
					throw new Exception("The path is a file but expected a directory");
				}
			}else{
				throw new Exception("Directory does not exist");
			}
		}catch(Exception e){
			throw new Exception("Readable and writable directory test failed for path", e);
		}
	}
	
	/**
	 * Tests whether the path is a readable and executable file or not.
	 * 
	 * Throws exception with the appropriate message in case the test failed
	 * 
	 * @param path the path to the file
	 * @throws Exception
	 */
	public static void pathMustBeAReadableExecutableFile(final String path) throws Exception{
		if(HelperFunctions.isNullOrEmpty(path)){
			throw new Exception("NULL/Empty path");
		}
		final File file = new File(path);
		try{
			if(file.exists()){
				if(file.isDirectory()){
					throw new Exception("The path is a directory but expected a file");
				}else{
					if(!file.canRead()){
						throw new Exception("The path is not readable");
					}
					if(!file.canExecute()){
						throw new Exception("The path is not executable");
					}
				}
			}else{
				throw new Exception("File does not exist");
			}
		}catch(Exception e){
			throw new Exception("Readable and executable file test failed for path", e);
		}
	}
}
