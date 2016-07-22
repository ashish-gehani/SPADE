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
import java.nio.charset.Charset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.google.code.externalsorting.ExternalSort;

import spade.core.Settings;

public class SortAuditLog {

	public static void main(String[] args){
		if(args.length < 2){
			//temp directory is optional. if not given then read from config file and otherwise use the system temp directory
			System.err.println("Invalid arguments. Valid arguments = <inputAuditLog> <sortedOutputAuditLog> [<temp directory for sorting>]");
			return;
		}
		File inputAuditLogFile = new File(args[0]);
		File sortedOutputLogFile = new File(args[1]);
		File tempDirectory = null;
		if(args.length >= 3){ //passed in temp dir
			if(args[2].trim().isEmpty()){
				System.err.println("Invalid temp directory path in arguments");
				return;
			}else{
				tempDirectory = new File(args[2]);
			}
		}else{ //didn't pass in temp directory. use the one in config file
			try{
				File configFile = new File(Settings.getDefaultConfigFilePath(SortAuditLog.class));
				if(configFile.exists()){
					Map<String, String> configProperties = FileUtility.readConfigFileAsKeyValueMap(configFile.getAbsolutePath(), "=");
					String tempDirectoryPath = configProperties.get("tempSortingDirectory");
					if(tempDirectoryPath != null){
						tempDirectory = new File(tempDirectoryPath);
					}
				}
			}catch(Exception e){
				System.err.println("Failed to read config file. Exited");
				return;
			}finally{
				if(tempDirectory == null){ //failed to read the config file too. Use the system temp 
					String systemTempDirectoryPath = System.getProperty("java.io.tmpdir");
					if(systemTempDirectoryPath != null){
						tempDirectory = new File(systemTempDirectoryPath);
					}else{
						System.err.println("Failed to locate a system temp directory. Exited.");
						return;
					}
				}
			}
		}
		if(!inputAuditLogFile.exists()){
			System.err.println("Input audit log file doesn't exist. Exited.");
			return;
		}
		if(tempDirectory != null && !tempDirectory.exists()){
			try{
				tempDirectory.mkdir();
			}catch(Exception e){
				System.err.println("Failed to create '"+tempDirectory.getAbsolutePath()+"' directory. Exited.");
				return;
			}
		}
		
		Comparator<String> comparator = new Comparator<String>(){
			@Override
			public int compare(String record1, String record2) {
				try{
					//example -> type=DAEMON_START msg=audit(1443402415.959:2435):
					int record1ColonIndex = record1.indexOf(":");
					int record1CloseBracketIndex = record1.indexOf(")");
					int record2ColonIndex = record2.indexOf(":");
					int record2CloseBracketIndex = record2.indexOf(")");
					String record1EventId = record1.substring(record1ColonIndex+1, record1CloseBracketIndex);
					String record2EventId = record2.substring(record2ColonIndex+1, record2CloseBracketIndex);
					Long record1EventIdLong = Long.parseLong(record1EventId);
					Long record2EventIdLong = Long.parseLong(record2EventId);
					return (int)(record1EventIdLong - record2EventIdLong);
				}catch(Exception e){
					e.printStackTrace(System.err);
				}
				return 0;
			}
		};
		
		// https://github.com/lemire/externalsortinginjava
		
		try{
			List<File> l = ExternalSort.sortInBatch(inputAuditLogFile, comparator, ExternalSort.DEFAULTMAXTEMPFILES, Charset.defaultCharset(), tempDirectory, true);
	        ExternalSort.mergeSortedFiles(l, sortedOutputLogFile, comparator);
		}catch(Exception e){
			System.err.print("Failed to sort log file");
			e.printStackTrace(System.err);
		}
	}
	
}
