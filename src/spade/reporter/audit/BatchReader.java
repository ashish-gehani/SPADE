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

package spade.reporter.audit;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//all records of any event are assumed to be placed contiguously in the file
public class BatchReader{
    	private final Pattern event_line_pattern = Pattern.compile("msg=(audit[()0-9.:]+:)\\s*");
    	private BufferedReader br = null;
    	
    	private boolean EOF = false;
    	private String nextLine = null;
    	private String lastMsg = null;
    	
    	public BatchReader(BufferedReader br){
    		this.br = br;
    	}
    	
    	public String readLine() throws IOException{
    		if(EOF || nextLine != null){
    			String temp = nextLine;
    			nextLine = null;
    			return temp;
    		}
    		String line = br.readLine();
    		if(line == null){
    			EOF = true;
    			nextLine = null;
    			if(lastMsg != null){
    				return "type=EOE msg="+lastMsg;
    			}else{
    				return null;
    			}    			
    		} else {
    			Matcher matcher = event_line_pattern.matcher(line);
    			if(matcher.find()){
    				String msg = matcher.group(1);
    				
    				if(lastMsg == null){
    					lastMsg = msg;
    				}
    				
    				if(!msg.equals(lastMsg)){
    					String tempMsg = lastMsg;
    					lastMsg = msg;
    					nextLine = line;
    					return "type=EOE msg="+tempMsg;
    				}else{
    					return line;
    				}
    			}else{
    				return line;
    			}
    		}
    	}
    	
    	public void close() throws IOException{
    		br.close();
    	}
    
    }