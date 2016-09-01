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
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import spade.core.Settings;
import spade.utility.CommonFunctions;
import spade.utility.FileUtility;

/**
 * Audit log reader which reads the log and sorts it in a sliding window of 'x' audit records
 * 
 */
public class AuditEventReader {

	private Logger logger = Logger.getLogger(this.getClass().getName());

	//Reporting variables
	private boolean reportingEnabled = false;
	private long reportEveryMs;
	private long startTime, lastReportedTime;
	private long lastReportedRecordCount, recordCount;

	// Group 1: key
	// Group 2: value
	private static final Pattern pattern_key_value = Pattern.compile("(\\w+)=\"*((?<=\")[^\"]+(?=\")|([^\\s]+))\"*");

	// Group 1: node
	// Group 2: type
	// Group 3: time
	// Group 4: recordid
	private static final Pattern pattern_message_start = Pattern.compile("(?:node=(\\S+) )?type=(.+) msg=audit\\(([0-9\\.]+)\\:([0-9]+)\\):\\s*");

	// Group 1: cwd
	//cwd is either a quoted string or an unquoted string in which case it is in hex format
	private static final Pattern pattern_cwd = Pattern.compile("cwd=(\".+\"|[a-zA-Z0-9]+)");

	// Group 1: item number
	// Group 2: name
	// Group 3: nametype
	//name is either a quoted string or an unquoted string in which case it is in hex format
	private static final Pattern pattern_path = Pattern.compile("item=([0-9]*) name=(\".+\"|[a-zA-Z0-9]+) .*nametype=([a-zA-Z]*)");

	// Group 1: eventid
	private static final Pattern pattern_eventid = Pattern.compile("msg=audit\\([0-9\\.]+\\:([0-9]+)\\):");

	/**
	 * Reference to the current input stream entry alone with key
	 * which is being read. Null means that either it has not been 
	 * initialized yet (constructor ensures that this doesn't happen) 
	 * or all streams have been read completely.
	 */
	private SimpleEntry<String, BufferedReader> currentInputStreamReaderEntry;	

	/**
	 * List of key value pairs of <stream identifier, input streams> to read from in the order in the list.
	 * In case of files the stream identifier is the path of the file
	 */
	private LinkedList<SimpleEntry<String, InputStream>> inputStreamEntries = new LinkedList<SimpleEntry<String, InputStream>>();

	/**
	 * Sorted event ids in the current window
	 */
	private TreeSet<Long> eventIds = new TreeSet<Long>();

	/**
	 * List of audit records for event ids received in the current window
	 */
	private Map<Long, Set<String>> eventIdToEventRecords = new HashMap<Long, Set<String>>();

	/**
	 * Number of audit records read so far out of the window size
	 */
	private long currentlyBufferedRecords = 0;

	/**
	 * Window size to buffer and sort at a time
	 */
	private long maxRecordBufferSize = 0;

	/**
	 * Write output log to this file (for debugging or later use of the log)
	 */
	private PrintWriter outputLogWriter;

	/**
	 * Id of the last event that was output. Used to discard out of order event
	 * records across window size. So, if event with id 'x' has been sent out
	 * then if any event with id 'y' is read, where y < x, then 'y' is discarded
	 */
	private long lastEventId = -1;

	/**
	 * Create instance of the class that reads the given list of files in the given order
	 * 
	 * @param maxRecordBufferSize window size (cannot be less than 1, bigger the better)
	 * @param logFiles files to read (cannot be empty or null and all files must exist)
	 * @throws Exception IllegalArgumentException or IOException
	 */
	public AuditEventReader(long maxRecordBufferSize, String... logFiles) throws Exception{

		if(maxRecordBufferSize < 1){
			throw new IllegalArgumentException("Buffer size for audit records must be greater than 0");
		}
		if(logFiles == null){
			throw new IllegalArgumentException("Null audit log files specified");
		}else if(logFiles.length == 0){
			throw new IllegalArgumentException("No audit log files specified");
		}

		for(String logFile : logFiles){
			if(logFile != null){
				File file = new File(logFile);
				if(file.exists()){
					this.inputStreamEntries.addLast(new SimpleEntry<String, InputStream>(logFile, new FileInputStream(file)));
				}else{
					throw new IllegalArgumentException("Log file " + file.getAbsolutePath() + " doesn't exist");
				}	
			}else{
				throw new IllegalArgumentException("All file paths must be non-null");
			}
		}

		// Shouldn't happen but just in case
		if(this.inputStreamEntries.size() == 0){
			throw new IllegalArgumentException("No valid log files specified");
		}

		// Making sure that the current inputstream reader is non-null when readEventData is called afterwards
		initializeCurrentStreamReader();
		this.maxRecordBufferSize = maxRecordBufferSize;

		setGlobalsFromConfig();
	}

	/**
	 * Create instance of the class that reads the given list of streams in the given order
	 * 
	 * @param maxRecordBufferSize window size (cannot be less than 1, bigger the better)
	 * @param inputStreamEntries streams to read (cannot be empty or null and all streams must be non null along with their keys)
	 * @throws Exception IllegalArgumentException or IOException
	 */
	public AuditEventReader(long maxRecordBufferSize, List<SimpleEntry<String, InputStream>> inputStreamEntries) throws Exception{
		if(maxRecordBufferSize < 1){
			throw new IllegalArgumentException("Buffer size for audit records must be greater than 0");
		}
		if(inputStreamEntries == null){
			throw new IllegalArgumentException("Null input streams specified");
		}else if(inputStreamEntries.size() == 0){
			throw new IllegalArgumentException("No input streams specified");
		}
		for(SimpleEntry<String, InputStream> inputStreamEntry : inputStreamEntries){
			String key = inputStreamEntry.getKey();
			InputStream inputStream = inputStreamEntry.getValue();
			if(key != null && inputStream != null){
				this.inputStreamEntries.addLast(new SimpleEntry<String, InputStream>(key, inputStream));
			}else{
				throw new IllegalArgumentException("Input stream entry -> [" + key + ", " + inputStream + "]. Both must be non-null");
			}
		}
		if(this.inputStreamEntries.size() == 0){
			throw new IllegalArgumentException("No valid input streams");
		}
		initializeCurrentStreamReader();
		this.maxRecordBufferSize = maxRecordBufferSize;

		setGlobalsFromConfig();
	}

	private void setGlobalsFromConfig(){
		String defaultConfigFilePath = Settings.getDefaultConfigFilePath(this.getClass());
		try{
			if(new File(defaultConfigFilePath).exists()){
				Map<String, String> properties = FileUtility.readConfigFileAsKeyValueMap(defaultConfigFilePath, "=");
				if(properties != null && properties.size() > 0){
					Long reportingInterval = CommonFunctions.parseLong(properties.get("reportingIntervalSeconds"), null);
					if(reportingInterval != null){
						if(reportingInterval < 1){ //at least 1 ms
							logger.log(Level.INFO, "Statistics reporting turned off");
						}else{
							reportingEnabled = true;
							reportEveryMs = reportingInterval * 1000;
							startTime = lastReportedTime = System.currentTimeMillis();
							recordCount = lastReportedRecordCount = 0;
						}
					}
				}
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to read config file '"+defaultConfigFilePath+"'");
		}
	}

	/**
	 * Convenience function to get the next stream and to initialize(open) it.
	 * 
	 * It closes the current stream if not null. 
	 * 
	 * @throws Exception IOException
	 */
	private void initializeCurrentStreamReader() throws Exception{
		if(currentInputStreamReaderEntry != null){
			currentInputStreamReaderEntry.getValue().close();
			currentInputStreamReaderEntry = null; //set to null
		}
		if(inputStreamEntries.size() > 0){
			SimpleEntry<String, InputStream> nextEntry = inputStreamEntries.removeFirst();
			currentInputStreamReaderEntry = new SimpleEntry<String, BufferedReader>(
					nextEntry.getKey(), new BufferedReader(new InputStreamReader(nextEntry.getValue())));
		}
	}

	/**
	 * Function to set the output log stream to which the sorted log is written
	 * 
	 * If null value of the argument then nothing done
	 * 
	 * @param outputStream output log stream to write to (can be null)
	 * @throws Exception IOException
	 */
	public void setOutputLog(OutputStream outputStream) throws Exception{
		if(outputStream != null){
			outputLogWriter = new PrintWriter(outputStream);
		}
	}

	private void printStats(){
		long currentTime = System.currentTimeMillis();
		float overallTime = (float) (currentTime - startTime) / 1000; // # in secs
		float intervalTime = (float) (currentTime - lastReportedTime) / 1000; // # in secs
		if(overallTime > 0 && intervalTime > 0){
			float overallRecordVolume = (float) recordCount / overallTime; // # records/sec
			float intervalRecordVolume = (float) (recordCount - lastReportedRecordCount) / intervalTime; // # records/sec
			logger.log(Level.INFO, "Overall rate: {0} records/sec in {1} seconds. Interval rate: {2} records/sec in {3} seconds.", 
					new Object[]{overallRecordVolume, overallTime, intervalRecordVolume, intervalTime});
		}
	}

	/**
	 * Returns a map of key values for the event that is read from the stream(s)
	 * 
	 * Null return value means EOF for all streams
	 * 
	 * @return map of key values of the read audit event
	 * @throws Exception IOException
	 */
	public Map<String, String> readEventData() throws Exception{

		if(reportingEnabled){
			long currentTime = System.currentTimeMillis();
			if((currentTime - lastReportedTime) >= reportEveryMs){
				printStats();
				lastReportedTime = currentTime;
				lastReportedRecordCount = recordCount;
			}
		}

		if(currentInputStreamReaderEntry == null){ //all streams processed
			return getEventData();		
		}else{ // not all streams processed
			while(currentlyBufferedRecords < maxRecordBufferSize){ //read audit records until max amount read
				String line = currentInputStreamReaderEntry.getValue().readLine();
				if(line == null){ //if input stream read completely
					logger.log(Level.INFO, "Reading succeeded of '" + currentInputStreamReaderEntry.getKey() + "'");
					initializeCurrentStreamReader(); //initialize the next stream
					if(currentInputStreamReaderEntry == null){ //if there was no next stream to be initialized
						break;
					}
				}else{ //if input stream not completely read yet
					if(reportingEnabled){
						recordCount++;
					}
					Matcher event_start_matcher = pattern_eventid.matcher(line);
					if (event_start_matcher.find()){ //get the event id
						Long eventId = CommonFunctions.parseLong(event_start_matcher.group(1), null);
						if(eventId == null){ //if event id null then don't process
							logger.log(Level.SEVERE, "Event id null for line -> " + line);
						}else{
							if(eventId < lastEventId){
								logger.log(Level.WARNING, "Out of order event beyond the window size -> " + line);
							}else{
								currentlyBufferedRecords++; //increment the record count
								if(eventIdToEventRecords.get(eventId) == null){
									eventIdToEventRecords.put(eventId, new HashSet<String>());
									eventIds.add(eventId); //add event id
								}
								eventIdToEventRecords.get(eventId).add(line); //add audit record
							}
						}
					}
				}
			}
			//just return the one event
			return getEventData();				
		}
	}

	/**
	 * Sets the current input stream to null and now will start emptying the buffers
	 * instead of going to the next stream, if any.
	 * 
	 */
	public void stopReading(){
		currentInputStreamReaderEntry = null;
	}

	/**
	 * Closes any open input streams and the output stream (if opened)
	 */
	public void close(){
		if(reportingEnabled){
			printStats();
		}
		if(outputLogWriter != null){
			try{
				outputLogWriter.close();
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to close output log writer", e);
			}
		}
		if(inputStreamEntries != null && inputStreamEntries.size() > 0){
			for(SimpleEntry<String, InputStream> inputStreamEntry : inputStreamEntries){
				String key = inputStreamEntry.getKey();
				InputStream inputStream = inputStreamEntry.getValue();
				try{
					inputStream.close();
				}catch(Exception e){
					logger.log(Level.SEVERE, "Failed to close input stream for '"+key+"'", e);
				}
			}
		}
		if(currentInputStreamReaderEntry != null){
			String key = currentInputStreamReaderEntry.getKey();
			try{
				currentInputStreamReaderEntry.getValue().close();
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to close input stream for key '"+key+"'", e);
			}
		}
	}

	/**
	 * Returns the map of key values for the event with the smallest event id
	 * 
	 * @return map of key values for the event. Null if none found.
	 * @throws Exception
	 */
	private Map<String,String> getEventData() throws Exception{
		Long eventId = eventIds.pollFirst();
		if(eventId == null){ //empty
			return null;
		}else{
			lastEventId = eventId;
			Set<String> eventRecords = eventIdToEventRecords.remove(eventId);
			currentlyBufferedRecords -= eventRecords.size();

			Map<String, String> eventData = new HashMap<String, String>();

			if(eventRecords != null){

				for(String eventRecord : eventRecords){

					if(outputLogWriter != null){
						outputLogWriter.println(eventRecord);
					}

					eventData.putAll(parseEventLine(eventRecord));
				}

			}

			return eventData;
		}	
	}

	/**
	 * Creates a map with key values as needed by the Audit reporter from audit records of an event
	 * 
	 * @param line event record to parse
	 * @return map of key values for the argument record
	 */
	private Map<String, String> parseEventLine(String line) {

		Map<String, String> auditRecordKeyValues = new HashMap<String, String>();

		Matcher event_start_matcher = pattern_message_start.matcher(line);
		if (event_start_matcher.find()) {
			String node = event_start_matcher.group(1);
			String type = event_start_matcher.group(2);
			String time = event_start_matcher.group(3);
			String eventId = event_start_matcher.group(4);
			String messageData = line.substring(event_start_matcher.end());

			auditRecordKeyValues.put("eventid", eventId);
			auditRecordKeyValues.put("node", node);

			if (type.equals("SYSCALL")) {
				Map<String, String> eventData = parseKeyValPairs(messageData);
				eventData.put("time", time);
				auditRecordKeyValues.putAll(eventData);
			} else if (type.equals("CWD")) {
				Matcher cwd_matcher = pattern_cwd.matcher(messageData);
				if (cwd_matcher.find()) {
					String cwd = cwd_matcher.group(1);
					cwd = cwd.trim();
					if(cwd.startsWith("\"") && cwd.endsWith("\"")){ //is a string path
						cwd = cwd.substring(1, cwd.length()-1);
					}else{ //is in hex format
						try{
							cwd = parseHexStringToUTF8(cwd);
						}catch(Exception e){
							//failed to parse
						}
					}                    
					auditRecordKeyValues.put("cwd", cwd);
				}
			} else if (type.equals("PATH")) {
				Matcher path_matcher = pattern_path.matcher(messageData);
				if (path_matcher.find()) {
					String item = path_matcher.group(1);
					String name = path_matcher.group(2);
					String nametype = path_matcher.group(3);
					name = name.trim();
					if(name.startsWith("\"") && name.endsWith("\"")){ //is a string path
						name = name.substring(1, name.length()-1);
					}else{ //is in hex format
						try{
							name = parseHexStringToUTF8(name);
						}catch(Exception e){
							//failed to parse
						}
					}
					auditRecordKeyValues.put("path" + item, name);
					auditRecordKeyValues.put("nametype" + item, nametype);
				}
			} else if (type.equals("EXECVE")) {
				Matcher key_value_matcher = pattern_key_value.matcher(messageData);
				while (key_value_matcher.find()) {
					auditRecordKeyValues.put("execve_" + key_value_matcher.group(1), key_value_matcher.group(2));
				}
			} else if (type.equals("FD_PAIR")) {
				Matcher key_value_matcher = pattern_key_value.matcher(messageData);
				while (key_value_matcher.find()) {
					auditRecordKeyValues.put(key_value_matcher.group(1), key_value_matcher.group(2));
				}
			} else if (type.equals("SOCKETCALL")) {
				Matcher key_value_matcher = pattern_key_value.matcher(messageData);
				while (key_value_matcher.find()) {
					auditRecordKeyValues.put("socketcall_" + key_value_matcher.group(1), key_value_matcher.group(2));
				}
			} else if (type.equals("SOCKADDR")) {
				Matcher key_value_matcher = pattern_key_value.matcher(messageData);
				while (key_value_matcher.find()) {
					auditRecordKeyValues.put(key_value_matcher.group(1), key_value_matcher.group(2));
				}
			} else if(type.equals("NETFILTER_PKT")){
				Matcher key_value_matcher = pattern_key_value.matcher(messageData);
				while (key_value_matcher.find()) {
					auditRecordKeyValues.put(key_value_matcher.group(1), key_value_matcher.group(2));
				}
			} else if (type.equals("MMAP")){
				Matcher key_value_matcher = pattern_key_value.matcher(messageData);
				while (key_value_matcher.find()) {
					auditRecordKeyValues.put(key_value_matcher.group(1), key_value_matcher.group(2));
				}
			} else if(type.equals("PROCTITLE")){
				//record type not being handled at the moment. 
			} else {
				//            	if(!seenTypesOfUnsupportedRecords.contains(type)){
				//            		seenTypesOfUnsupportedRecords.add(type);
				//            		logger.log(Level.WARNING, "Unknown type {0} for message: {1}. Won't output to log a message for this type again.", new Object[]{type, line});
				//            	}                
			}

		} else {

		}

		return auditRecordKeyValues;
	}

	/**
	 * Converts hex string as UTF-8
	 * 
	 * @param hexString string to parse
	 * @return parsed string
	 */
	private String parseHexStringToUTF8(String hexString){
		if(hexString == null){
			return null;
		}

		//find the null char i.e. the end of the string
		for(int a = 0; a<=hexString.length()-2; a+=2){
			String hexByte = hexString.substring(a,a+2);
			Integer intByte = Integer.parseInt(hexByte, 16);
			char c = (char)(intByte.intValue());
			if(c == 0){ //null char
				hexString = hexString.substring(0, a);
				break;
			}
		}

		ByteBuffer bytes = ByteBuffer.allocate(hexString.length()/2);
		for(int a = 0; a<=hexString.length()-2; a+=2){
			bytes.put((byte)Integer.parseInt(hexString.substring(a, a+2), 16));

		}
		bytes.rewind();
		Charset cs = Charset.forName("UTF-8");
		CharBuffer cb = cs.decode(bytes);
		return cb.toString();
	}

	/**
	 * Takes a string with keyvalue pairs and returns a Map Input e.g.
	 * "key1=val1 key2=val2" etc. Input string validation is callee's
	 * responsibility
	 */
	private static Map<String, String> parseKeyValPairs(String messageData) {
		Matcher key_value_matcher = pattern_key_value.matcher(messageData);
		Map<String, String> keyValPairs = new HashMap<>();
		while (key_value_matcher.find()) {
			keyValPairs.put(key_value_matcher.group(1), key_value_matcher.group(2));
		}
		return keyValPairs;
	}

}
