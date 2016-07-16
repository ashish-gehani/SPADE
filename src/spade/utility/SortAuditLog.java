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

import com.google.code.externalsorting.ExternalSort;

public class SortAuditLog {

	public static void main(String[] args){
		if(args.length < 2){
			//temp directory is optional. if not given then uses the system temp directory
			System.err.println("Invalid arguments. Valid arguments = <inputAuditLog> <sortedOutputAuditLog> [<temp directory for sorting>]");
			return;
		}
		File inputAuditLogFile = new File(args[0]);
		File sortedOutputLogFile = new File(args[1]);
		File tempDirectory = null;
		if(args.length >= 3){
			tempDirectory = new File(args[2]);
		}
		if(!inputAuditLogFile.exists()){
			System.err.println("Input audit log file doesn't exist");
			return;
		}
		if(tempDirectory != null && !tempDirectory.exists()){
			try{
				tempDirectory.mkdir();
			}catch(Exception e){
				System.err.println("Failed to create '"+tempDirectory.getPath()+"' directory. Sorting failed.");
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
		
		//https://github.com/lemire/externalsortinginjava
		
		try{
			List<File> l = null;
			if(tempDirectory != null){
				l = ExternalSort.sortInBatch(inputAuditLogFile, comparator, ExternalSort.DEFAULTMAXTEMPFILES, Charset.defaultCharset(), tempDirectory, true);
			}else{
				l = ExternalSort.sortInBatch(inputAuditLogFile, comparator);
			}			
	        ExternalSort.mergeSortedFiles(l, sortedOutputLogFile, comparator);
		}catch(Exception e){
			System.err.print("Failed to sort log file");
			e.printStackTrace(System.err);
		}
		
		
		/* OLD NAIVE SORTING IMPLEMENTATION */
		
		/*
		
		List<String> auditRecords = new ArrayList<String>();
		
		//Read input audit log file
		BufferedReader inputLogReader = null;
		try{
			inputLogReader = new BufferedReader(new FileReader(inputAuditLogFile));
			String auditRecord = null;
			while((auditRecord = inputLogReader.readLine()) != null){
				if(!auditRecord.trim().isEmpty()){
					auditRecords.add(auditRecord);
				}
			}
		}catch(Exception e){
			System.err.print("Failed to read input audit log file");
			e.printStackTrace(System.err);
			return;
		}finally {
			try{
				if(inputLogReader != null){
					inputLogReader.close();
				}
			}catch(Exception e){
				System.err.print("Failed to close input log reader");
				e.printStackTrace(System.err);
			}
		}
		
		//Sort audit log file based on event id which is between : and ) in the beginning of every line
		Collections.sort(auditRecords, new Comparator<String>(){
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
		});
		
		//Write sorted log file to output file
		PrintWriter sortedLogWriter = null;
		try{
			sortedLogWriter = new PrintWriter(sortedOutputLogFile);
			for(String auditRecord : auditRecords){
				sortedLogWriter.println(auditRecord);
			}
		}catch(Exception e){
			e.printStackTrace(System.err);
		}finally{
			try{
				if(sortedLogWriter != null){
					sortedLogWriter.close();
				}
			}catch(Exception e){
				e.printStackTrace(System.err);
			}
		}
		
		*/
	}
	
}
