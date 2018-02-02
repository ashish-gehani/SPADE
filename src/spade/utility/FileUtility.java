package spade.utility;

import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class FileUtility {

	public static Pattern constructRegexFromFile(String filepath) throws Exception{
		StringBuilder suffixes = new StringBuilder(), prefixes = new StringBuilder(), inlines = new StringBuilder();
		StringBuilder currentString = new StringBuilder();
		List<String> lines = FileUtils.readLines(new File(filepath));
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
		List<String> lines = FileUtils.readLines(new File(filepath));
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
		List<String> lines = FileUtils.readLines(new File(filepath));
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
		List<String> lines = FileUtils.readLines(new File(filepath));
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
	
	public static boolean fileExists(String filepath){
		if(filepath == null){
			return false;
		}else{
			try{
				return new File(filepath).exists();
			}catch(Exception e){
				return false;
			}
		}
	}
	
	public static boolean mkdirs(String dirPath){
		try{
			FileUtils.forceMkdir(new File(dirPath));
			return true;
		}catch(Exception e){
			return false;
		}
	}
	
	//test
	public static void main(String[] args) throws Exception{
		Pattern p = constructRegexFromFile(args[0]);
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String line = null;
		while((line = br.readLine()) != null){
			if(line.equals("exit")){
				break;
			}
			System.out.println(p.matcher(line).find());
		}
	}
	
}
