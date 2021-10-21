/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2021 SRI International

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
package spade.reporter.procmon;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CSVEventReader extends EventReader{

	private static final String 
		COLUMN_TIME = "Time of Day"
		, COLUMN_PROCESS_NAME = "Process Name"
		, COLUMN_PID = "PID"
		, COLUMN_OPERATION = "Operation"
		, COLUMN_PATH = "Path"
		, COLUMN_RESULT = "Result"
		, COLUMN_DETAIL = "Detail"
		, COLUMN_DURATION = "Duration"
		, COLUMN_EVENT_CLASS = "Event Class"
		, COLUMN_IMAGE_PATH = "Image Path"
		, COLUMN_COMPANY = "Company"
		, COLUMN_DESCRIPTION = "Description"
		, COLUMN_VERSION = "Version"
		, COLUMN_USER = "User"
		, COLUMN_COMMAND_LINE = "Command Line"
		, COLUMN_PARENT_PID = "Parent PID"
		, COLUMN_ARCHITECTURE = "Architecture"
		, COLUMN_CATEGORY = "Category"
		, COLUMN_DATE_AND_TIME = "Date & Time"
		, COLUMN_TID = "TID";

	private final List<String> mandatoryColumns = new ArrayList<String>(
			Arrays.asList(
					COLUMN_TIME
					, COLUMN_PROCESS_NAME
					, COLUMN_PID
					, COLUMN_OPERATION
					, COLUMN_PATH
					, COLUMN_RESULT
					, COLUMN_DETAIL
					, COLUMN_DURATION
					, COLUMN_EVENT_CLASS
					, COLUMN_IMAGE_PATH
					, COLUMN_COMPANY
					, COLUMN_DESCRIPTION
					, COLUMN_VERSION
					, COLUMN_USER
					, COLUMN_COMMAND_LINE
					, COLUMN_PARENT_PID
					, COLUMN_ARCHITECTURE
					, COLUMN_CATEGORY
					)
			);

    private final Integer 
    	INDEX_TIME
    	, INDEX_PROCESS_NAME
    	, INDEX_PID
    	, INDEX_OPERATION
    	, INDEX_PATH
    	, INDEX_RESULT
    	, INDEX_DETAIL
    	, INDEX_DURATION
    	, INDEX_EVENT_CLASS
    	, INDEX_IMAGE_PATH
    	, INDEX_COMPANY
    	, INDEX_DESCRIPTION
    	, INDEX_VERSION
    	, INDEX_USER
    	, INDEX_COMMAND_LINE
    	, INDEX_PARENT_PID
    	, INDEX_ARCHITECTURE
    	, INDEX_CATEGORY
    	, INDEX_DATE_AND_TIME
    	, INDEX_TID;

	private BufferedReader reader = null;

	public CSVEventReader(final String filePath) throws Exception{
		super(filePath);
		try{
			final java.io.FileInputStream fileInputStream = new java.io.FileInputStream(new java.io.File(filePath));
			fileInputStream.read(new byte[3]); // consume BOM bytes
			final java.io.InputStreamReader inputStreamReader = new java.io.InputStreamReader(fileInputStream, java.nio.charset.StandardCharsets.UTF_8);
			reader = new BufferedReader(inputStreamReader);
			final boolean isHeader = true;
			final String[] headerValues = parseCSVLine(isHeader);
			if(headerValues == null){
				throw new Exception("No header line in file");
			}
			if(headerValues.length == 0){
				throw new Exception(
						"First does not contain name of columns selected in Windows Process Monitor");
			}
			final Map<String, Integer> columnMap = new HashMap<String, Integer>();
			for(int i = 0; i < headerValues.length; i++){
				final String headerValue = headerValues[i];
				columnMap.put(headerValue, i);
			}
			for(final String mandatoryColumn : mandatoryColumns){
				if(columnMap.get(mandatoryColumn) == null){
					throw new Exception("Missing mandatory column '" + mandatoryColumn + "' in file");
				}
			}

			// Mandatory
            INDEX_TIME = columnMap.get(COLUMN_TIME);
            INDEX_PROCESS_NAME = columnMap.get(COLUMN_PROCESS_NAME);
            INDEX_PID = columnMap.get(COLUMN_PID);
            INDEX_OPERATION = columnMap.get(COLUMN_OPERATION);
            INDEX_PATH = columnMap.get(COLUMN_PATH);
            INDEX_RESULT = columnMap.get(COLUMN_RESULT);
            INDEX_DETAIL = columnMap.get(COLUMN_DETAIL);
            INDEX_DURATION = columnMap.get(COLUMN_DURATION);
            INDEX_EVENT_CLASS = columnMap.get(COLUMN_EVENT_CLASS);
            INDEX_IMAGE_PATH = columnMap.get(COLUMN_IMAGE_PATH);
            INDEX_COMPANY = columnMap.get(COLUMN_COMPANY);
            INDEX_DESCRIPTION = columnMap.get(COLUMN_DESCRIPTION);
            INDEX_VERSION = columnMap.get(COLUMN_VERSION);
            INDEX_USER = columnMap.get(COLUMN_USER);
            INDEX_COMMAND_LINE = columnMap.get(COLUMN_COMMAND_LINE);
            INDEX_PARENT_PID = columnMap.get(COLUMN_PARENT_PID);
            INDEX_ARCHITECTURE = columnMap.get(COLUMN_ARCHITECTURE);
            INDEX_CATEGORY = columnMap.get(COLUMN_CATEGORY);

            // Optional
            INDEX_DATE_AND_TIME = columnMap.get(COLUMN_DATE_AND_TIME);
            INDEX_TID = columnMap.get(COLUMN_TID);

		}catch(Exception e){
			if(reader != null){
				try{
					reader.close();
				}catch(Exception closeException){
					
				}
			}
			throw e;
		}
	}

	@Override
	public Event read() throws Exception{
		final boolean isHeader = false;
		final String[] tokens = parseCSVLine(isHeader);
		if(tokens == null){
			return null;
		}

        final String timeOfDay = getToken(tokens, INDEX_TIME);
        final String processName = getToken(tokens, INDEX_PROCESS_NAME);
        final String pid = getToken(tokens, INDEX_PID);
        final String operation = getToken(tokens, INDEX_OPERATION);
        final String path = getToken(tokens, INDEX_PATH);
        final String result = getToken(tokens, INDEX_RESULT);
        final String detail = getToken(tokens, INDEX_DETAIL);
        final String duration = getToken(tokens, INDEX_DURATION);
        final String eventClass = getToken(tokens, INDEX_EVENT_CLASS);
        final String imagePath = getToken(tokens, INDEX_IMAGE_PATH);
        final String company = getToken(tokens, INDEX_COMPANY);
        final String description = getToken(tokens, INDEX_DESCRIPTION);
        final String version = getToken(tokens, INDEX_VERSION);
        final String user = getToken(tokens, INDEX_USER);
        final String commandLine = getToken(tokens, INDEX_COMMAND_LINE);
        final String parentPid = getToken(tokens, INDEX_PARENT_PID);
        final String architecture = getToken(tokens, INDEX_ARCHITECTURE);
        final String category = getToken(tokens, INDEX_CATEGORY);

        // Optional
        final String dateAndTime = optToken(tokens, INDEX_DATE_AND_TIME);
        final String tid = optToken(tokens, INDEX_TID);

        return new Event(timeOfDay, processName, pid, operation, path, result, detail, duration, 
        		eventClass, imagePath, company, description, version, user, commandLine, parentPid, 
        		architecture, category, dateAndTime, tid);
	}

	@Override
	public void close(){
		if(this.reader != null){
			try{
				this.reader.close();
			}catch(Exception e){
				
			}
		}
	}

	private String[] parseCSVLine(final boolean isHeader) throws Exception{
		String line = reader.readLine();
		if(line == null){
			return null;
		}
		line = line.trim();
		if(isHeader){
			// strip the leading characters before the first '"'
			final int index = line.indexOf('"');
			if(index > -1){
				line = line.substring(index);
			}else{
				throw new Exception("Malformed CSV header line. Missing '\"': " + line);
			}
		}
		if(!(line.startsWith("\"") && line.endsWith("\""))){
			throw new Exception("Malformed CSV line. Missing surrounding '\"': " + line);
		}
		line = line.substring(1, line.length() - 1);
		final String[] tokens = line.split("\",\""); 
		return tokens;
	}

	private String _getToken(final String[] tokens, final Integer index, final boolean optional) throws Exception{
		if(index == null){
			if(optional){
				return null;
			}else{
				throw new Exception("NULL index to get column for in: " + Arrays.asList(tokens));
			}
		}
		if(index < 0 || index >= tokens.length){
			if(optional){
				return null;
			}else{
				throw new Exception("Index (" + index + ") not in range (0-" + tokens.length + ") to get column for in: "
						+ Arrays.asList(tokens));
			}
		}
		return tokens[index];
	}

	private String getToken(final String[] tokens, final Integer index) throws Exception{
		return _getToken(tokens, index, false);
	}

	private String optToken(final String[] tokens, final Integer index) throws Exception{
		return _getToken(tokens, index, true);
	}
}
