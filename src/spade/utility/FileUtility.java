package spade.utility;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

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
