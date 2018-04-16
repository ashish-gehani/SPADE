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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.exception.ExceptionUtils;

import spade.core.Settings;
import spade.utility.CommonFunctions;
import spade.utility.FileUtility;

/**
 * This class reads and parses the audit logs one event at a time.
 * 
 * Assumes that the all of the records for an event are received 
 * contiguously and are not spread out.
 * 
 */
public class AuditEventReader {

	private Logger logger = Logger.getLogger(this.getClass().getName());

	public static final String ARG0 = "a0",
			ARG1 = "a1",
			ARG2 = "a2",
			ARG3 = "a3",
			COMM = "comm",
			CWD = "cwd",
			DADDR = "daddr",
			DPORT = "dport",
			EGID = "egid",
			EUID = "euid",
			EVENT_ID = "eventid",
			EXECVE_ARGC = "execve_argc",
			EXECVE_PREFIX = "execve_",
			EXIT = "exit",
			FD = "fd",
			FD0 = "fd0",
			FD1 = "fd1",
			FSGID = "fsgid",
			FSUID = "fsuid",
			GID = "gid",
			HOOK = "hook",
			HOOK_INPUT = "1",
			HOOK_OUTPUT = "3",
			ITEMS = "items",
			MODE_PREFIX = "mode",
			NAMETYPE_CREATE = "CREATE",
			NAMETYPE_DELETE = "DELETE",
			NAMETYPE_NORMAL = "NORMAL",
			NAMETYPE_PARENT = "PARENT",
			NAMETYPE_PREFIX = "nametype",
			NAMETYPE_UNKNOWN = "UNKNOWN",
			PATH_PREFIX = "path",
			PID = "pid",
			PPID = "ppid",
			PROTO = "proto",
			RECORD_TYPE_CWD = "CWD",
			RECORD_TYPE_DAEMON_START = "DAEMON_START",
			RECORD_TYPE_EOE = "EOE",
			RECORD_TYPE_EXECVE = "EXECVE",
			RECORD_TYPE_FD_PAIR = "FD_PAIR",
			RECORD_TYPE_MMAP = "MMAP",
			RECORD_TYPE_NETFILTER_PKT = "NETFILTER_PKT",
			RECORD_TYPE_PATH = "PATH",
			RECORD_TYPE_PROCTITLE = "PROCTITLE",
			RECORD_TYPE_SOCKADDR = "SOCKADDR",
			RECORD_TYPE_SOCKETCALL = "SOCKETCALL",
			RECORD_TYPE_SYSCALL = "SYSCALL",
			RECORD_TYPE_UBSI_ENTRY = "UBSI_ENTRY",
			RECORD_TYPE_UBSI_EXIT = "UBSI_EXIT",
			RECORD_TYPE_UBSI_DEP = "UBSI_DEP",
			RECORD_TYPE_UNKNOWN_PREFIX = "UNKNOWN[",
			RECORD_TYPE_USER = "USER",
			RECORD_TYPE_KEY = "type",
			SADDR = "saddr",
			SGID = "sgid",
			SPORT = "sport",
			SUCCESS = "success",
			SUCCESS_NO = "no",
			SUCCESS_YES = "yes",
			SUID = "suid",
			SYSCALL = "syscall",
			TIME = "time",
			UID = "uid",
			UNIT_PID = "unit_pid",
			UNIT_THREAD_START_TIME = "unit_thread_start_time",
			UNIT_UNITID = "unit_unitid",
			UNIT_ITERATION = "unit_iteration",
			UNIT_TIME = "unit_time",
			UNIT_COUNT = "unit_count",
			UNIT_DEPS_COUNT = "unit_deps_count",
			USER_MSG_SPADE_AUDIT_HOST_KEY = "spade_host_msg",
			KMODULE_RECORD_TYPE = "netio_module_record",
			KMODULE_DATA_KEY = "netio_intercepted",
			KMODULE_FD = "fd",
			KMODULE_SOCKTYPE = "sock_type",
			KMODULE_LOCAL_SADDR = "local_saddr",
			KMODULE_REMOTE_SADDR = "remote_saddr";
	
	//Reporting variables
	private boolean reportingEnabled = false;
	private long reportEveryMs;
	private long startTime, lastReportedTime;
	private long lastReportedRecordCount, recordCount;

	// Group 1: pid
	// Group 2: unitid
	// Group 3: iteration
	// Group 4: time
	// Group 5: count
	private final Pattern pattern_unit = 
			Pattern.compile("\\(pid=(\\d+) thread_time=(\\d+\\.\\d+) unitid=(\\d+) iteration=(\\d+) time=(\\d+\\.\\d+) count=(\\d+)\\)");
	
	// Group 1: key
	// Group 2: value
	private final Pattern pattern_key_value = Pattern.compile("(\\w+)=\"*((?<=\")[^\"]+(?=\")|([^\\s]+))\"*");

	// Group 1: node
	// Group 2: type
	// Group 3: time
	// Group 4: recordid
	private final Pattern pattern_message_start = Pattern.compile("(?:node=(\\S+) )?type=(.+) msg=audit\\(([0-9\\.]+)\\:([0-9]+)\\):\\s*");
	
	// Group 1: cwd
	//cwd is either a quoted string or an unquoted string in which case it is in hex format
	private final Pattern pattern_cwd = Pattern.compile("cwd=(\".+\"|[a-zA-Z0-9]+)");

	// Group 1: item number
	// Group 2: name
	// Group 3: mode
	// Group 4: nametype
	//name is either a quoted string or an unquoted string in which case it is in hex format
//	private static final Pattern pattern_path = Pattern.compile("item=([0-9]*) name=(\".+\"|[a-zA-Z0-9]+|\\(null\\)) .*(?:mode=([0-9]+) ).*nametype=([a-zA-Z]*)");

	/**
	 * Buffers all the records for the current event being read
	 */
	private final Set<String> currentEventRecords = new HashSet<String>();
	
	/**
	 * Keeps track of the current event id being buffered
	 */
	private Long currentEventId = -1L;

	/**
	 * Id of the stream that is read by this class
	 */
	private String streamId;
	
	/**
	 *	The stream to read from by this class
	 */
	private BufferedReader stream;
	
	private long rotateAfterRecordCount = 0;
	private String outputLogFile = null;
	private PrintWriter outputLogWriter = null;
	private int currentOutputLogFileCount = 0;
	private long recordsWrittenToOutputLog = 0;

	/**
	 * Used to find out if there is pending UBSI event before reading more 
	 */
	private boolean pendingUBSIEvent = false;
	
	/**
	 * Flag to keep track of end of file/stream
	 */
	private boolean EOF = false;
	
	/**
	 * Flag to tell the reader how to behave in case of unexpected data format.
	 * if true then throw an exception.
	 * if false then do best effort to continue.
	 */
	private boolean failfast;
	
	/**
	 * Create instance of the class that reads from the given stream
	 * 
	 * @param streamId An identifier to read the audit logs from
	 * @param streamToReadFrom The stream to read from
	 * @throws Exception IllegalArgumentException or IOException
	 */
	public AuditEventReader(String streamId, InputStream streamToReadFrom, boolean failfast) throws Exception{
		if(streamId == null){
			throw new IllegalArgumentException("Stream ID cannot be NULL");
		}
		if(streamToReadFrom == null){
			throw new IllegalArgumentException("The stream to read from cannot be NULL");
		}

		stream = new BufferedReader(new InputStreamReader(streamToReadFrom));

		this.failfast = failfast;
		
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
	 * Function to set the output log file to which the log is written
	 * 
	 * If rotateAfterRecordCount is less than 1 then no rotation is done and
	 * everything is written to a single file.
	 * 
	 * @param outputLogFile output log file to write to
	 * @param rotateAfterRecordCount number of records to create a new log after
	 * @throws Exception IOException
	 */
	public void setOutputLog(String outputLogFile, long rotateAfterRecordCount) throws Exception{
		this.outputLogFile = outputLogFile;
		this.rotateAfterRecordCount = rotateAfterRecordCount < 1 ? 0 : rotateAfterRecordCount;
		outputLogWriter = new PrintWriter(outputLogFile);
	}
	
	private void writeToOutputLog(String record){
		if(outputLogWriter != null){
			try{
				outputLogWriter.println(record);
				recordsWrittenToOutputLog++;
				if(rotateAfterRecordCount > 0 && recordsWrittenToOutputLog >= rotateAfterRecordCount){
					recordsWrittenToOutputLog = 0;
					currentOutputLogFileCount++;
					outputLogWriter.flush();
					outputLogWriter.close();
					outputLogWriter = new PrintWriter(outputLogFile + "." + currentOutputLogFileCount);
				}
			}catch(Exception e){
				logger.log(Level.WARNING, "Failed to write out to output log", e);
			}
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
	 * Returns the event id from the audit record.
	 * 
	 * Expected format of line -> "type='TYPE' msg=audit('time':'eventid'):"
	 * 
	 * @param line audit record
	 * @return event id. NULL if not found
	 */
	private Long getEventId(String line){
		try{
			int firstIndexOfColon = line.indexOf(':');
			int firstIndexOfClosingBracket = line.indexOf(')');
			return Long.parseLong(line.substring(firstIndexOfColon+1, firstIndexOfClosingBracket));
		}catch(Exception e){
			logger.log(Level.WARNING, "Failed to get event id from line: " + line, e);
			return null;
		}
	}
	
	/**
	 * Returns the time from the audit record.
	 * 
	 * Expected format of line -> "type='TYPE' msg=audit('time':'eventid'):"
	 * 
	 * @param line audit record
	 * @return time. NULL if not found
	 */
	private String getEventTime(String line){
		try{
			int firstIndexOfOpeningBracket = line.indexOf('(');
			int firstIndexOfColon = line.indexOf(':');
			String timeStr = line.substring(firstIndexOfOpeningBracket+1, firstIndexOfColon);
			Double.parseDouble(timeStr); // if valid double then continues
			return timeStr;
		}catch(Exception e){
			logger.log(Level.WARNING, "Failed to get time from line: " + line, e);
			return null;
		}
	}
	
	/**
	 * Returns a map of key values for the event that is read from the stream
	 * 
	 * Null return value means EOF
	 * 
	 * Reads on the assumption that all records for an event are contiguously placed
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

		/*
		 * Logic:
		 * 
		 * Input -> all records have event ids exception UBSI_EXIT and UBSI_DEP
		 * All UBSI events have only one record per event
		 * 
		 * Before reading further from the stream check if there is a pending UBSI
		 * event. If there is then don't read from the stream yet and return the 
		 * pending event. If no pending UBSI event then read from the stream. If non-
		 * UBSI records being read then read until the event id changes and then return
		 * the event that is read so far. If UBSI event encountered while there is a
		 * non-UBSI event buffered then first return that event (after setting pending-
		 * UBSIEvent to true and buffering that for the next readEventData call.
		 * 
		 * If EOF reached then keep returning NULL on all future calls.
		 * 
		 */
		
		if(EOF){
			return null;
		}else{
			Map<String, String> eventData = null;
			
			if(pendingUBSIEvent){
				Set<String> copy = new HashSet<String>();
				copy.addAll(currentEventRecords);
				currentEventRecords.clear();
				currentEventId = -1L;
				pendingUBSIEvent = false;
				eventData = getEventMap(copy);
			}else{
				String line = null;
				
				while((line = stream.readLine()) != null){
					Long eventId = getEventId(line);
					String eventTime = getEventTime(line);
					
					if(eventId == null || eventTime == null){
						if(failfast){
							throw new Exception("Invalid time '"+eventTime+"' or event-id '"+eventId+"' in record: " + line);
						}else{
							continue;
						}
					}
					
					writeToOutputLog(line);
					if(reportingEnabled){
						recordCount++;
					}
					if(line.contains(RECORD_TYPE_KEY+"="+RECORD_TYPE_PROCTITLE) || 
							line.contains(RECORD_TYPE_KEY+"="+RECORD_TYPE_UNKNOWN_PREFIX) || 
							line.contains(RECORD_TYPE_KEY+"="+RECORD_TYPE_EOE)){
						continue; // ignore these records
					}else{
						String UBSIRecord = null;
						if(line.contains(RECORD_TYPE_KEY+"=" + RECORD_TYPE_UBSI_EXIT) ||
								line.contains(RECORD_TYPE_KEY+"=" + RECORD_TYPE_UBSI_DEP) || 
								line.contains(RECORD_TYPE_KEY+"=" + RECORD_TYPE_UBSI_ENTRY)){
							UBSIRecord = line;
						}
						
						if(UBSIRecord == null){
							
							if(currentEventId.equals(-1L)){
								currentEventId = eventId;
								currentEventRecords.add(line); //add the next event record
								continue;
							}else{
								if(!currentEventId.equals(eventId)){// event id changed hence publish the things in buffer
									currentEventId = eventId;
									Set<String> records = new HashSet<String>(currentEventRecords);
									currentEventRecords.clear();
									currentEventRecords.add(line); //add the next event record
									eventData = getEventMap(records);
									break;
								}else{ //if they are equal
									currentEventRecords.add(line);
									continue;
								}
							}
							
						}else if(UBSIRecord != null && currentEventRecords.isEmpty()){
							// No pending event and only UBSI event then return that
							Set<String> records = new HashSet<String>();
							records.add(UBSIRecord);
							currentEventId = -1L;
							pendingUBSIEvent = false;
							eventData = getEventMap(records);
							break;
						}else if(UBSIRecord != null && !currentEventRecords.isEmpty()){
							// Has a pending event. add the UBSI record to pending and return the existing event
							Set<String> records = new HashSet<String>(currentEventRecords);
							currentEventRecords.clear();
							currentEventRecords.add(UBSIRecord);
							currentEventId = -1L;
							pendingUBSIEvent = true;
							eventData = getEventMap(records);
							break;
						}
					}
				}
				// EOF
				if(line == null){
					EOF = true;
					if(currentEventRecords.isEmpty()){
						return null;
					}else{
						Set<String> records = new HashSet<String>(currentEventRecords);
						currentEventRecords.clear();
						currentEventId = -1L;
						pendingUBSIEvent = false;
						return getEventMap(records);
					}
				}
			}
			return eventData;
		}
	}
	
	/**
	 * Passes all the records through the function {@link #parseEventLine(String) parseEventLine}
	 * and returns a map which contains keys and values for all the records
	 * 
	 * @param records records of a single event
	 * @return map of key values
	 */
	private Map<String, String> getEventMap(Set<String> records) throws Exception{
		try{
			Map<String, String> eventMap = new HashMap<String, String>();
			for(String record : records){
				eventMap.putAll(parseEventLine(record));
			}
			return eventMap;
		}catch(Exception e){
			throw new MalformedAuditDataException(e.getMessage()+ 
					" ["+ExceptionUtils.getStackTrace(e)+"] ", String.valueOf(records));
		}
	}

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
		if(stream != null){
			try{
				stream.close();
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to close the stream '"+streamId+"'", e);
			}
		}
	}

	/**
	 * Parses the line to get unit information out of it.
	 * 
	 * Expected format for unit information ...'(pid=1 unitid=2 iteration=3 time=4 count=5)'...
	 * 
	 * Returns a map which contains the keys in the above-given format but the keys are
	 * defined as constants in this class.
	 * 
	 * @param line audit record with unit information
	 * @return map of key values for a unit
	 */
	private List<Map<String, String>> parseUnitsKeyValues(String line){
		List<Map<String, String>> unitsKeyValues = new ArrayList<Map<String, String>>();
		Matcher matcher2 = pattern_unit.matcher(line);
		while(matcher2.find()){
			String pid = matcher2.group(1);
			String thread_start_time = matcher2.group(2);
			String unitid = matcher2.group(3);
			String iteration = matcher2.group(4);
			String time = matcher2.group(5);
			String count = matcher2.group(6);
			
			Map<String, String> unitKeyValues = new HashMap<String, String>();
			unitKeyValues.put(UNIT_PID, pid);
			unitKeyValues.put(UNIT_THREAD_START_TIME, thread_start_time);
			unitKeyValues.put(UNIT_UNITID, unitid);
			unitKeyValues.put(UNIT_ITERATION, iteration);
			unitKeyValues.put(UNIT_TIME, time);
			unitKeyValues.put(UNIT_COUNT, count);
			unitsKeyValues.add(unitKeyValues);
		}
		return unitsKeyValues;
	}
	
	/**
	 * Creates a map with key values as needed by the Audit reporter from audit records of an event
	 * 
	 * @param line event record to parse
	 * @return map of key values for the argument record
	 */
	private Map<String, String> parseEventLine(String line) {

		Map<String, String> auditRecordKeyValues = new HashMap<String, String>();
		
		if(line.contains(RECORD_TYPE_KEY+"="+RECORD_TYPE_DAEMON_START)){
			auditRecordKeyValues.put(RECORD_TYPE_KEY, RECORD_TYPE_DAEMON_START);
			return auditRecordKeyValues;
		}

		boolean isUBSIEvent = false;
		
		// There will be time and eventid in this one
		if(line.contains(RECORD_TYPE_KEY+"="+RECORD_TYPE_UBSI_ENTRY)){
			
			List<Map<String, String>> unitsKeyValues = parseUnitsKeyValues(line);
			if(unitsKeyValues.size() != 1){ // there should be only one unit's information
				logger.log(Level.WARNING, "Malformed record '"+line+"'");
			}else{
				// Add all the units key values
				auditRecordKeyValues.putAll(unitsKeyValues.get(0));
			}
						
			auditRecordKeyValues.put(AuditEventReader.RECORD_TYPE_KEY, RECORD_TYPE_UBSI_ENTRY);
						
			isUBSIEvent = true;
			
		}else if(line.contains(RECORD_TYPE_KEY+"="+RECORD_TYPE_UBSI_EXIT)){
			// no time and event id
			auditRecordKeyValues.put(AuditEventReader.RECORD_TYPE_KEY, RECORD_TYPE_UBSI_EXIT);
						
			isUBSIEvent = true;
			
		}else if(line.contains(RECORD_TYPE_KEY+"="+RECORD_TYPE_UBSI_DEP)){
			// no time and event id
			List<Map<String, String>> unitsKeyValues = parseUnitsKeyValues(line);
			if(unitsKeyValues.size() == 0){ // there should be only one or more unit's information
				logger.log(Level.WARNING, "Malformed record '"+line+"'");
			}else{
				// Add all the units key values
				
				// Last one is the acting unit
				Map<String, String> actingUnitKeyValues = unitsKeyValues.remove(unitsKeyValues.size() - 1);
				auditRecordKeyValues.putAll(actingUnitKeyValues);
				
				for(int a = 0; a<unitsKeyValues.size(); a++){
					Map<String, String> unitKeyValues = unitsKeyValues.get(a);
					for(Map.Entry<String, String> entry : unitKeyValues.entrySet()){
						auditRecordKeyValues.put(entry.getKey() + a, entry.getValue());
					}
				}
				
				auditRecordKeyValues.put(UNIT_DEPS_COUNT, String.valueOf(unitsKeyValues.size()));
								
			}
			
			auditRecordKeyValues.put(AuditEventReader.RECORD_TYPE_KEY, RECORD_TYPE_UBSI_DEP);
			isUBSIEvent = true;
			
		}
		
		if(isUBSIEvent){
			
			Long UBSIEntryEventId = getEventId(line);
			String UBSIEntryTime = getEventTime(line);
			
			auditRecordKeyValues.put(TIME, UBSIEntryTime);
			auditRecordKeyValues.put(EVENT_ID, String.valueOf(UBSIEntryEventId));
			
			String msgData = line.substring(line.indexOf(" ppid="));
			auditRecordKeyValues.putAll(CommonFunctions.parseKeyValPairs(msgData));
			
		}else{
		
			Matcher event_start_matcher = pattern_message_start.matcher(line);
			if (event_start_matcher.find()) {
				String type = event_start_matcher.group(2);
				String time = event_start_matcher.group(3);
				String eventId = event_start_matcher.group(4);
				String messageData = line.substring(event_start_matcher.end());
	
				auditRecordKeyValues.put(EVENT_ID, eventId);
				auditRecordKeyValues.put(RECORD_TYPE_KEY, type);
	
				if(type.equals(RECORD_TYPE_USER)){
					int indexOfData = messageData.indexOf(KMODULE_DATA_KEY);
					if(indexOfData != -1){
						String data = messageData.substring(indexOfData + KMODULE_DATA_KEY.length() + 1);
						data = data.substring(1, data.length() - 1);// remove quotes
						Map<String, String> eventData = CommonFunctions.parseKeyValPairs(data);
						eventData.put(RECORD_TYPE_KEY, KMODULE_RECORD_TYPE);
						eventData.put(COMM, CommonFunctions.decodeHex(eventData.get(COMM)));
						eventData.put(TIME, time);
						auditRecordKeyValues.putAll(eventData);
					}
				}else if (type.equals(RECORD_TYPE_SYSCALL)) {
					Map<String, String> eventData = CommonFunctions.parseKeyValPairs(messageData);
					if(messageData.contains(COMM + "=") && !messageData.contains(COMM + "=\"")
							&& !"(null)".equals(eventData.get(COMM))){ // comm has a hex encoded value
						// decode and replace value
						eventData.put(COMM, CommonFunctions.decodeHex(eventData.get(COMM)));
					}
					eventData.put(TIME, time);
					auditRecordKeyValues.putAll(eventData);
				} else if (type.equals(RECORD_TYPE_CWD)) {
					Matcher cwd_matcher = pattern_cwd.matcher(messageData);
					if (cwd_matcher.find()) {
						String cwd = cwd_matcher.group(1);
						cwd = cwd.trim();
						if(cwd.startsWith("\"") && cwd.endsWith("\"")){ //is a string path
							cwd = cwd.substring(1, cwd.length()-1);
						}else{ //is in hex format
							try{
								cwd = CommonFunctions.decodeHex(cwd);
							}catch(Exception e){
								//failed to parse
							}
						}                    
						auditRecordKeyValues.put(CWD, cwd);
					}
				} else if (type.equals(RECORD_TYPE_PATH)) {
					Map<String, String> pathKeyValues = CommonFunctions.parseKeyValPairs(messageData);
					String itemNumber = pathKeyValues.get("item");
					String name = pathKeyValues.get("name");
					String mode = pathKeyValues.get("mode");
					mode = mode == null ? "0" : mode;
					String nametype = pathKeyValues.get("nametype");
					
					name = name.trim();
					if(messageData.contains(" name=") && 
							!messageData.contains(" name=\"") &&
							!messageData.contains(" name=(null)")){ 
						//is a hex path if the value of the key name doesn't start with double quotes
						try{
							name = CommonFunctions.decodeHex(name);
						}catch(Exception e){
							//failed to parse
						}
					}
					
					auditRecordKeyValues.put(PATH_PREFIX + itemNumber, name);
					auditRecordKeyValues.put(NAMETYPE_PREFIX + itemNumber, nametype);
					auditRecordKeyValues.put(MODE_PREFIX + itemNumber, mode);
				} else if (type.equals(RECORD_TYPE_EXECVE)) {
					Matcher key_value_matcher = pattern_key_value.matcher(messageData);
					while (key_value_matcher.find()) {
						auditRecordKeyValues.put(EXECVE_PREFIX + key_value_matcher.group(1), key_value_matcher.group(2));
					}
				} else if (type.equals(RECORD_TYPE_FD_PAIR)) {
					Matcher key_value_matcher = pattern_key_value.matcher(messageData);
					while (key_value_matcher.find()) {
						auditRecordKeyValues.put(key_value_matcher.group(1), key_value_matcher.group(2));
					}
				} else if (type.equals(RECORD_TYPE_SOCKETCALL)) {
					Matcher key_value_matcher = pattern_key_value.matcher(messageData);
					while (key_value_matcher.find()) {
						auditRecordKeyValues.put("socketcall_" + key_value_matcher.group(1), key_value_matcher.group(2));
					}
				} else if (type.equals(RECORD_TYPE_SOCKADDR)) {
					Matcher key_value_matcher = pattern_key_value.matcher(messageData);
					while (key_value_matcher.find()) {
						auditRecordKeyValues.put(key_value_matcher.group(1), key_value_matcher.group(2));
					}
				} else if(type.equals(RECORD_TYPE_NETFILTER_PKT)){
					auditRecordKeyValues.put(TIME, time); // add time
					auditRecordKeyValues.put(RECORD_TYPE_KEY, RECORD_TYPE_NETFILTER_PKT); // type
					// rest of the keys as is below
					Matcher key_value_matcher = pattern_key_value.matcher(messageData);
					while (key_value_matcher.find()) {
						auditRecordKeyValues.put(key_value_matcher.group(1), key_value_matcher.group(2));
					}
				} else if (type.equals(RECORD_TYPE_MMAP)){
					Matcher key_value_matcher = pattern_key_value.matcher(messageData);
					while (key_value_matcher.find()) {
						auditRecordKeyValues.put(key_value_matcher.group(1), key_value_matcher.group(2));
					}
				} else{
					             
				}
	
			} else {
	
			}
		}

		return auditRecordKeyValues;
	}
}
