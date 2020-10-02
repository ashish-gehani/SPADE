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

import org.apache.commons.lang.StringUtils;

import spade.core.Settings;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;

/**
 * This class reads and parses the audit logs one event at a time.
 * 
 * Assumes that the all of the records for an event are received 
 * contiguously and are not spread out.
 * 
 */
public class AuditEventReader{

	private Logger logger = Logger.getLogger(this.getClass().getName());

	public static final String 
			ARG0 = "a0",
			ARG1 = "a1",
			ARG2 = "a2",
			ARG3 = "a3",
			COMM = "comm",
			CWD = "cwd",
			EGID = "egid",
			EUID = "euid",
			EVENT_ID = "eventid",
			EXECVE_ARGC = "execve_argc",
			ARGC = "argc",
			EXECVE_PREFIX = "execve_",
			EXIT = "exit",
			FD = "fd",
			FD0 = "fd0",
			FD1 = "fd1",
			FSGID = "fsgid",
			FSUID = "fsuid",
			GID = "gid",
			OUID = "ouid",
			OGID = "ogid",
			ITEMS = "items",
			MODE_PREFIX = "mode",
			NAMETYPE_CREATE = "CREATE",
			NAMETYPE_DELETE = "DELETE",
			NAMETYPE_NORMAL = "NORMAL",
			NAMETYPE_PARENT = "PARENT",
			NAMETYPE_PREFIX = "nametype",
			NAMETYPE_UNKNOWN = "UNKNOWN",
			PATH_PREFIX = "path",
			ITEM = "item",
			PID = "pid",
			PPID = "ppid",
			RECORD_TYPE_CWD = "CWD",
			RECORD_TYPE_DAEMON_START = "DAEMON_START",
			RECORD_TYPE_EOE = "EOE",
			RECORD_TYPE_EXECVE = "EXECVE",
			RECORD_TYPE_FD_PAIR = "FD_PAIR",
			RECORD_TYPE_IPC = "IPC",
			RECORD_TYPE_MQ_SENDRECV = "MQ_SENDRECV",
			RECORD_TYPE_MMAP = "MMAP",
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
			RECORD_TYPE_NETFILTER_HOOK = "NETFILTER_HOOK",
			RECORD_TYPE_KEY = "type",
			SADDR = "saddr",
			NAME = "name",
			SGID = "sgid",
			SUCCESS = "success",
			SUCCESS_NO = "no",
			SUCCESS_YES = "yes",
			SUID = "suid",
			SYSCALL = "syscall",
			TIME = "time",
			UID = "uid",
			MSG_LEN = "msg_len",
			UNIT_PID = "unit_pid",
			UNIT_THREAD_START_TIME = "unit_thread_start_time",
			UNIT_UNITID = "unit_unitid",
			UNIT_ITERATION = "unit_iteration",
			UNIT_TIME = "unit_time",
			UNIT_COUNT = "unit_count",
			USER_MSG_SPADE_AUDIT_HOST_KEY = "spade_host_msg",
			KMODULE_RECORD_TYPE = "netio_module_record",
			KMODULE_DATA_KEY = "netio_intercepted",
			UBSI_INTERCEPTED_DATA_KEY = "ubsi_intercepted",
			KMODULE_FD = "fd",
			KMODULE_SOCKTYPE = "sock_type",
			KMODULE_LOCAL_SADDR = "local_saddr",
			KMODULE_REMOTE_SADDR = "remote_saddr",
			NS_SUBTYPE_KEY = "ns_subtype",
			NS_SUBTYPE_VALUE = "ns_namespaces",
			NS_OPERATION_KEY = "ns_operation",
			NS_OPERATION_VALUE_NEWPROCESS = "ns_NEWPROCESS",
			NS_SYSCALL_KEY = "ns_syscall",
			NS_NS_PID = "ns_ns_pid",
			NS_HOST_PID = "ns_host_pid",
			NS_INUM_MNT = "ns_inum_mnt",
			NS_INUM_NET = "ns_inum_net",
			NS_INUM_PID = "ns_inum_pid",
			NS_INUM_PID_FOR_CHILDREN = "ns_inum_pid_children",
			NS_INUM_USER = "ns_inum_usr",
			NS_INUM_IPC = "ns_inum_ipc",
			NF_SUBTYPE_KEY = "nf_subtype",
			NF_SUBTYPE_VALUE = "nf_netfilter",
			NF_HOOK_KEY = "nf_hook",
			NF_HOOK_VALUE_LOCAL_OUT = "NF_INET_LOCAL_OUT",
			NF_HOOK_VALUE_LOCAL_IN = "NF_INET_LOCAL_IN",
			NF_HOOK_VALUE_POST_ROUTING = "NF_INET_POST_ROUTING",
			NF_HOOK_VALUE_PRE_ROUTING = "NF_INET_PRE_ROUTING",
			NF_PRIORITY_KEY = "nf_priority",
			NF_PRIORITY_VALUE_FIRST = "NF_IP_PRI_FIRST",
			NF_PRIORITY_VALUE_LAST = "NF_IP_PRI_LAST",
			NF_ID_KEY = "nf_id",
			NF_SRC_IP_KEY = "nf_src_ip",
			NF_SRC_PORT_KEY = "nf_src_port",
			NF_DST_IP_KEY = "nf_dst_ip",
			NF_DST_PORT_KEY = "nf_dst_port",
			NF_PROTOCOL_KEY = "nf_protocol",
			NF_PROTOCOL_VALUE_TCP = "TCP",
			NF_PROTOCOL_VALUE_UDP = "UDP",
			NF_IP_VERSION_KEY = "nf_ip_version",
			NF_IP_VERSION_VALUE_IPV4 = "IPV4",
			NF_IP_VERSION_VALUE_IPV6 = "IPV6",
			NF_NET_NS_INUM_KEY = "nf_net_ns";

	//Reporting variables
	private boolean reportingEnabled = false;
	private long reportEveryMs;
	private long startTime, lastReportedTime;
	private long lastReportedRecordCount, recordCount;

	/**
	 * Buffers all the records for the current event being read
	 */
	private final Set<AuditRecord> currentEventRecords = new HashSet<AuditRecord>();

	/**
	 * Keeps track of the current event id being buffered
	 */
	private String currentEventIdString = null;

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
	 * Flag to keep track of end of file/stream
	 */
	private boolean EOF = false;

	/**
	 * Create instance of the class that reads from the given stream
	 * 
	 * @param streamId An identifier to read the audit logs from
	 * @param streamToReadFrom The stream to read from
	 * @throws Exception IllegalArgumentException or IOException
	 */
	public AuditEventReader(String streamId, InputStream streamToReadFrom) throws Exception{
		if(streamId == null){
			throw new IllegalArgumentException("Stream ID cannot be NULL");
		}
		if(streamToReadFrom == null){
			throw new IllegalArgumentException("The stream to read from cannot be NULL");
		}

		stream = new BufferedReader(new InputStreamReader(streamToReadFrom));

		setGlobalsFromConfig();
	}

	private void setGlobalsFromConfig(){
		String defaultConfigFilePath = Settings.getDefaultConfigFilePath(this.getClass());
		try{
			if(new File(defaultConfigFilePath).exists()){
				Map<String, String> properties = FileUtility.readConfigFileAsKeyValueMap(defaultConfigFilePath, "=");
				if(properties != null && properties.size() > 0){
					Long reportingInterval = HelperFunctions.parseLong(properties.get("reportingIntervalSeconds"), null);
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

	public final Map<String, String> readEventData() throws Exception{
		if(reportingEnabled){
			long currentTime = System.currentTimeMillis();
			if((currentTime - lastReportedTime) >= reportEveryMs){
				printStats();
				lastReportedTime = currentTime;
				lastReportedRecordCount = recordCount;
			}
		}

		while(!EOF){
			final String line = stream.readLine();
			if(line == null){
				EOF = true;
				break;
			}
			writeToOutputLog(line);

			if(reportingEnabled){
				recordCount++;
			}

			// Can throw the malformed audit data exception
			final AuditRecord record = new AuditRecord(line);

			if(record.type.equals(RECORD_TYPE_EOE)
					|| record.type.equals(RECORD_TYPE_PROCTITLE)
					|| record.type.startsWith(RECORD_TYPE_UNKNOWN_PREFIX)){
				continue;
			}

			if(currentEventIdString == null){
				// First event
				currentEventIdString = record.id;
				currentEventRecords.add(record);
				continue;
			}

			if(currentEventIdString.equals(record.id)){
				currentEventRecords.add(record);
				continue;
			}else{
				final Set<AuditRecord> auditRecordsToFlush = new HashSet<AuditRecord>();
				auditRecordsToFlush.addAll(currentEventRecords);

				currentEventRecords.clear();
				currentEventRecords.add(record);
				currentEventIdString = record.id;

				return convertAuditRecordsToEventMap(auditRecordsToFlush);
			}
		}

		if(currentEventRecords.isEmpty()){
			return null;
		}

		final Set<AuditRecord> auditRecordsToFlush = new HashSet<AuditRecord>();
		auditRecordsToFlush.addAll(currentEventRecords);

		currentEventRecords.clear();

		return convertAuditRecordsToEventMap(auditRecordsToFlush);
	}

	private final Map<String, String> convertAuditRecordsToEventMap(final Set<AuditRecord> auditRecords)
			throws Exception{
		try{
			final Map<String, String> eventMap = new HashMap<String, String>();
			for(final AuditRecord auditRecord : auditRecords){
				final Map<String, String> recordMap = parseAuditRecord(auditRecord);
				if(recordMap != null){
					eventMap.putAll(recordMap);
				}
			}
			return eventMap;
		}catch(Exception e){
			throw new MalformedAuditDataException(
					"Failed to create event map from audit records", String.valueOf(auditRecords), e);
		}
	}

	private final Map<String, String> parseDaemonStartRecord(final AuditRecord auditRecord) throws Exception{
		final Map<String, String> auditRecordKeyValues = new HashMap<String, String>();
		auditRecordKeyValues.put(TIME, auditRecord.time);
		auditRecordKeyValues.put(EVENT_ID, auditRecord.id);
		auditRecordKeyValues.put(RECORD_TYPE_KEY, RECORD_TYPE_DAEMON_START);
		return auditRecordKeyValues;
	}
	
	private final Map<String, String> parseUBSIRecord(final AuditRecord auditRecord) throws Exception{
		final Map<String, String> auditRecordKeyValues = new HashMap<String, String>();

		final String dataAfterUnit;

		if(auditRecord.type.equals(RECORD_TYPE_UBSI_ENTRY)){
			/*
			 * UBSI_ENTRY format:
			 * -> type=UBSI_ENTRY msg=ubsi(1601572509.571:501): 
			 * 		unit=(pid=701 thread_time=1601572509.571 unitid=901 iteration=0 time=1601572509.571 count=0) 
			 * 		ppid=700 pid=701 auid=1000 uid=1000 gid=1000 euid=1000 suid=1000 fsuid=1000 egid=1000 sgid=1000 fsgid=1000 
			 * 		tty=pts0 ses=3 comm="synth" exe="" key=(null)
			 */
			auditRecordKeyValues.putAll(parseUnitKeyValuePairs(auditRecord, "unit", ""));
			dataAfterUnit = StringUtils.substringAfter(auditRecord.data, ") ");
		}else if(auditRecord.type.equals(RECORD_TYPE_UBSI_EXIT)){
			/*
			 * UBSI_EXIT format:
			 * -> type=UBSI_EXIT msg=ubsi(1601572509.571:507): 
			 * 		ppid=700 pid=701 auid=1000 uid=1000 gid=1000 euid=1000 suid=1000 fsuid=1000 egid=1000 sgid=1000 fsgid=1000 
			 * 		tty=pts0 ses=3 comm="synth" exe="" key=(null)
			 */
			dataAfterUnit = auditRecord.data;
		}else if(auditRecord.type.equals(RECORD_TYPE_UBSI_DEP)){
			/*
			 * UBSI_DEP format:
			 * -> type=UBSI_DEP msg=ubsi(1601572509.571:506): 
			 * 		dep=(pid=701 thread_time=1601572509.571 unitid=901 iteration=0 time=1601572509.571 count=0), 
			 * 		unit=(pid=702 thread_time=1601572509.571 unitid=898 iteration=0 time=1601572509.571 count=0) 
			 * 		ppid=700 pid=702 auid=1000 uid=1000 gid=1000 euid=1000 suid=1000 fsuid=1000 egid=1000 sgid=1000 fsgid=1000 
			 * 		tty=pts0 ses=3 comm="synth" exe="" key=(null)
			 */
			auditRecordKeyValues.putAll(parseUnitKeyValuePairs(auditRecord, "dep", "0"));
			auditRecordKeyValues.putAll(parseUnitKeyValuePairs(auditRecord, "unit", ""));
			dataAfterUnit = StringUtils.substringAfter(auditRecord.data, ") ");
		}else{
			dataAfterUnit = null;
			throw new MalformedAuditDataException("Unexpected UBSI record type '" + auditRecord.type + "'", auditRecord.toString());
		}

		if(dataAfterUnit == null){
			throw new MalformedAuditDataException("Missing process data in '" + auditRecord.type + "' record", auditRecord.toString());
		}

		final Map<String, String> processMap = HelperFunctions.parseKeyValPairs(dataAfterUnit);
		processMap.put(COMM, mustParseAuditString(dataAfterUnit, COMM));

		auditRecordKeyValues.putAll(processMap);

		auditRecordKeyValues.put(TIME, auditRecord.time);
		auditRecordKeyValues.put(EVENT_ID, auditRecord.id);
		auditRecordKeyValues.put(RECORD_TYPE_KEY, auditRecord.type);

		return auditRecordKeyValues;
	}
	
	private final Map<String, String> parseNetioInterceptedRecord(final AuditRecord auditRecord,
			final String netioInterceptedSubRecord) throws Exception{
		/*
		 * netio_intercepted format
		 * -> type=USER msg=audit(1601572509.571:501): 
		 * 		netio_intercepted="syscall=%d exit=%ld success=%d fd=%d pid=%d ppid=%d 
		 * 		uid=%u euid=%u suid=%u fsuid=%u gid=%u egid=%u sgid=%u fsgid=%u 
		 * 		comm=%s sock_type=%d local_saddr=%s remote_saddr=%s remote_saddr_size=%d net_ns_inum=%ld"
		 */
		final Map<String, String> auditRecordKeyValues = HelperFunctions.parseKeyValPairs(netioInterceptedSubRecord);
		auditRecordKeyValues.put(COMM, mustParseAuditString(netioInterceptedSubRecord, COMM));
		auditRecordKeyValues.put(TIME, auditRecord.time);
		auditRecordKeyValues.put(EVENT_ID, auditRecord.id);
		auditRecordKeyValues.put(RECORD_TYPE_KEY, KMODULE_RECORD_TYPE);
		return auditRecordKeyValues;
	}
	
	private final Map<String, String> parseUbsiInterceptedRecord(final AuditRecord auditRecord,
			final String ubsiInterceptedSubRecord) throws Exception{
		/*
		 * ubsi_intercepted format
		 * -> type=USER msg=audit(1601572509.571:501): 
		 * 		ubsi_intercepted="syscall=%d success=%s exit=%ld a0=%x a1=%x a2=0 a3=0 
	 	 * 		items=0 ppid=%d pid=%d uid=%u gid=%u euid=%u suid=%u fsuid=%u egid=%u sgid=%u fsgid=%u comm=%s"
	 	 * 
	 	 */
		final Map<String, String> auditRecordKeyValues = HelperFunctions.parseKeyValPairs(ubsiInterceptedSubRecord);
		auditRecordKeyValues.put(COMM, mustParseAuditString(ubsiInterceptedSubRecord, COMM));
		auditRecordKeyValues.put(TIME, auditRecord.time);
		auditRecordKeyValues.put(EVENT_ID, auditRecord.id);
		auditRecordKeyValues.put(RECORD_TYPE_KEY, RECORD_TYPE_SYSCALL);
		return auditRecordKeyValues;
	}
	
	private final Map<String, String> parseNamespaceRecord(final AuditRecord auditRecord, final Map<String, String> dataMap){
		/*
		 * namespaces format
		 * -> type=USER msg=audit(1601572509.571:501): 
		 * 		ns_syscall=%d ns_subtype=ns_namespaces ns_operation=ns_%s ns_ns_pid=%ld ns_host_pid=%ld 
		 * 		ns_inum_mnt=%ld ns_inum_net=%ld ns_inum_pid=%ld ns_inum_pid_children=%ld ns_inum_usr=%ld ns_inum_ipc=%ld
		 */
		final Map<String, String> auditRecordKeyValues = new HashMap<String, String>();
		auditRecordKeyValues.putAll(dataMap);
		return auditRecordKeyValues;
	}

	private final Map<String, String> parseNetfilterRecord(final AuditRecord auditRecord, final Map<String, String> dataMap){
		/* 
		 * netfilter (1) format
		 * -> type=USER msg=audit(1601572509.571:501): 
		 * 		version=%s nf_subtype=nf_netfilter nf_hook=%s nf_priority=%s nf_id=%p nf_src_ip=%s nf_src_port=%d 
		 * 		nf_dst_ip=%s nf_dst_port=%d nf_protocol=%s nf_ip_version=%s nf_net_ns=%u
		 * 
		 * netfilter (2) format
		 * -> type=USER msg=audit(1601572509.571:501): 
		 * 		version=%s nf_subtype=nf_netfilter nf_hook=%s nf_priority=%s nf_id=%p nf_src_ip=%s nf_src_port=%d 
		 * 		nf_dst_ip=%s nf_dst_port=%d nf_protocol=%s nf_ip_version=%s nf_net_ns=-1
		 * 
		 * netfilter (3) format
		 * -> type=USER msg=audit(1601572509.571:501): 
		 * 		version=%s nf_subtype=nf_netfilter nf_hook=%s nf_priority=%s nf_id=%p nf_src_ip=%s nf_src_port=%d 
		 * 		nf_dst_ip=%s nf_dst_port=%d nf_protocol=%s nf_ip_version=%s
		 */
		final Map<String, String> auditRecordKeyValues = new HashMap<String, String>();
		auditRecordKeyValues.putAll(dataMap);
		auditRecordKeyValues.put(TIME, auditRecord.time);
		auditRecordKeyValues.put(EVENT_ID, auditRecord.id);
		auditRecordKeyValues.put(RECORD_TYPE_KEY, RECORD_TYPE_NETFILTER_HOOK);
		return auditRecordKeyValues;
	}
	
	private final Map<String, String> parseSyscallRecord(final AuditRecord auditRecord) throws Exception{
		/*
		 * -> node=ubuntu-bionic type=SYSCALL msg=audit(1601587102.900:16403): 
		 * 		arch=c000003e syscall=0 success=yes exit=30 a0=6 a1=7fff06b61700 a2=1000 a3=0 items=0 
		 * 		ppid=26414 pid=26415 auid=1000 uid=1002 gid=1002 euid=1002 suid=1002 fsuid=1002 egid=1002 sgid=1002 fsgid=1002 
		 * 		tty=(none) ses=3 comm="screen" exe="/usr/bin/screen" key=(null)
		 */
		final Map<String, String> auditRecordKeyValues = HelperFunctions.parseKeyValPairs(auditRecord.data);
		auditRecordKeyValues.put(COMM, mustParseAuditString(auditRecord.data, COMM));
		auditRecordKeyValues.put(TIME, auditRecord.time);
		auditRecordKeyValues.put(EVENT_ID, auditRecord.id);
		auditRecordKeyValues.put(RECORD_TYPE_KEY, RECORD_TYPE_SYSCALL);
		return auditRecordKeyValues;
	}
	
	private final Map<String, String> parseCwdRecord(final AuditRecord auditRecord) throws Exception{
		/*
		 * -> node=ubuntu-bionic type=CWD msg=audit(1601587106.252:16451): cwd="/"
		 */
		final Map<String, String> auditRecordKeyValues = HelperFunctions.parseKeyValPairs(auditRecord.data);
		auditRecordKeyValues.put(CWD, mustParseAuditString(auditRecord.data, CWD));
		return auditRecordKeyValues;
	}
	
	private final Map<String, String> parsePathRecord(final AuditRecord auditRecord) throws Exception{
		/*
		 * -> node=ubuntu-bionic type=PATH msg=audit(1601587106.252:16451): 
		 * 		item=0 name="/usr/share/dbus-1/system-services" inode=32602 dev=08:01 mode=040755 
		 * 		ouid=0 ogid=0 rdev=00:00 nametype=NORMAL cap_fp=0000000000000000 cap_fi=0000000000000000 
		 * 		cap_fe=0 cap_fver=0
		 */
		final Map<String, String> auditRecordKeyValues = new HashMap<String, String>();
		final Map<String, String> tempMap = HelperFunctions.parseKeyValPairs(auditRecord.data);

		final String itemNumber = tempMap.get(ITEM);
		final String mode = tempMap.get(MODE_PREFIX) == null ? "0" : tempMap.get(MODE_PREFIX);
		final String nametype = tempMap.get(NAMETYPE_PREFIX);
		final String name = parseAuditString(auditRecord.data, NAME);

		auditRecordKeyValues.put(MODE_PREFIX + itemNumber, mode);
		auditRecordKeyValues.put(NAMETYPE_PREFIX + itemNumber, nametype);
		auditRecordKeyValues.put(PATH_PREFIX + itemNumber, name);
		return auditRecordKeyValues;
	}
	
	private final Map<String, String> parseExecveRecord(final AuditRecord auditRecord) throws Exception{
		/*
		 * -> node=ubuntu-bionic type=EXECVE msg=audit(1601587110.584:16741): argc=1 a0="./server_mq"
		 */
		final Map<String, String> auditRecordKeyValues = new HashMap<String, String>();
		final Map<String, String> tempMap = HelperFunctions.parseKeyValPairs(auditRecord.data);

		final String argcString = tempMap.get(ARGC);
		final Integer argc = HelperFunctions.parseInt(argcString, null);
		if(argc != null){
			for(int i = 0; i < argc; i++){
				final String key = "a" + i;
				final String prefixedKey = EXECVE_PREFIX + key;
				final String value = parseAuditString(auditRecord.data, key);
				if(value != null){
					auditRecordKeyValues.put(prefixedKey, value);
				}else{
					auditRecordKeyValues.put(prefixedKey, "");
				}
			}
		}
		auditRecordKeyValues.put(EXECVE_ARGC, argcString);
		return auditRecordKeyValues;
	}
	
	private final Map<String, String> parseSimpleKeyValuePairRecord(final AuditRecord auditRecord) throws Exception{
		/*
		 * -> node=ubuntu-bionic type=FD_PAIR msg=audit(1601587107.820:16569): fd0=3 fd1=4
		 * 
		 * -> node=ubuntu-bionic type=SOCKADDR msg=audit(1601587107.820:16569): saddr=0100
		 * 
		 * -> node=ubuntu-bionic type=MMAP msg=audit(1601587110.584:16743): fd=3 flags=0x2
		 * 
		 * -> node=ubuntu-bionic type=IPC msg=audit(1601587136.164:19493): ouid=1000 ogid=1000 mode=0666
		 * 
		 * -> node=ubuntu-bionic type=MQ_SENDRECV msg=audit(1601587110.592:16933): 
		 * 		mqdes=3 msg_len=266 msg_prio=0 abs_timeout_sec=0 abs_timeout_nsec=0
		 */
		return HelperFunctions.parseKeyValPairs(auditRecord.data);
	}
	
	private final Map<String, String> parseAuditRecord(final AuditRecord auditRecord) throws Exception{
		switch(auditRecord.type){
			case RECORD_TYPE_DAEMON_START:
				return parseDaemonStartRecord(auditRecord);
			case RECORD_TYPE_UBSI_ENTRY:
			case RECORD_TYPE_UBSI_EXIT:
			case RECORD_TYPE_UBSI_DEP:
				return parseUBSIRecord(auditRecord);
			case RECORD_TYPE_USER:{
				final String netioInterceptedSubRecord = 
						StringUtils.substringBetween(auditRecord.data, KMODULE_DATA_KEY + "=\"", "\"");
				if(netioInterceptedSubRecord != null){
					return parseNetioInterceptedRecord(auditRecord, netioInterceptedSubRecord);
				}
				
				final String ubsiInterceptedSubRecord = 
						StringUtils.substringBetween(auditRecord.data, UBSI_INTERCEPTED_DATA_KEY + "=\"", "\"");
				if(ubsiInterceptedSubRecord != null){
					return parseUbsiInterceptedRecord(auditRecord, ubsiInterceptedSubRecord);
				}
				
				final Map<String, String> dataMap = HelperFunctions.parseKeyValPairs(auditRecord.data);
				if(NS_SUBTYPE_VALUE.equals(dataMap.get(NS_SUBTYPE_KEY))){
					return parseNamespaceRecord(auditRecord, dataMap);
				}

				if(NF_SUBTYPE_VALUE.equals(dataMap.get(NF_SUBTYPE_KEY))){
					return parseNetfilterRecord(auditRecord, dataMap);
				}
			}
			break;
			case RECORD_TYPE_SYSCALL:
				return parseSyscallRecord(auditRecord);
			case RECORD_TYPE_CWD:
				return parseCwdRecord(auditRecord);
			case RECORD_TYPE_PATH:
				return parsePathRecord(auditRecord);
			case RECORD_TYPE_EXECVE:
				return parseExecveRecord(auditRecord);
			case RECORD_TYPE_FD_PAIR:
			case RECORD_TYPE_SOCKADDR:
			case RECORD_TYPE_MMAP:
			case RECORD_TYPE_IPC:
			case RECORD_TYPE_MQ_SENDRECV:
				return parseSimpleKeyValuePairRecord(auditRecord);
		}
		return null;
	}

	private Map<String, String> parseUnitKeyValuePairs(final AuditRecord auditRecord, final String unitKey,
			final String keysSuffix)
			throws Exception{
		final String unitKeyValuesString = StringUtils.substringBetween(auditRecord.data, unitKey + "=(", ")"); 
		if(unitKeyValuesString == null){
			throw new MalformedAuditDataException(
					"Record doesn't contain the unit in the format '"+unitKey+"=(<key-value-pairs>)'", auditRecord.toString());
		}

		final List<String> missingUnitFields = new ArrayList<String>();
		final String pid = StringUtils.substringBetween(unitKeyValuesString, "pid=", " ");
		if(pid == null){
			missingUnitFields.add("pid");
		}
		final String threadTime = StringUtils.substringBetween(unitKeyValuesString, " thread_time=", " ");
		if(threadTime == null){
			missingUnitFields.add("thread_time");
		}
		final String unitId = StringUtils.substringBetween(unitKeyValuesString, " unitid=", " ");
		if(unitId == null){
			missingUnitFields.add("unitid");
		}
		final String iteration = StringUtils.substringBetween(unitKeyValuesString, " iteration=", " ");
		if(iteration == null){
			missingUnitFields.add("iteration");
		}
		final String time = StringUtils.substringBetween(unitKeyValuesString, " time=", " ");
		if(time == null){
			missingUnitFields.add("time");
		}
		final String count = StringUtils.substringAfter(unitKeyValuesString, " count=");
		if(count == null){
			missingUnitFields.add("count");
		}

		if(!missingUnitFields.isEmpty()){
			throw new MalformedAuditDataException(
					"Record doesn't contain the unit in the format "
					+ "'"+unitKey+"=(pid=<int> thread_time=<float> unitid=<int> iteration=<int> time=<float> count=<int>)'."
					+ " Missing fields: " + missingUnitFields, auditRecord.toString());
		}

		final Map<String, String> map = new HashMap<String, String>();
		map.put(UNIT_PID + keysSuffix, pid);
		map.put(UNIT_THREAD_START_TIME + keysSuffix, threadTime);
		map.put(UNIT_UNITID + keysSuffix, unitId);
		map.put(UNIT_ITERATION + keysSuffix, iteration);
		map.put(UNIT_TIME + keysSuffix, time);
		map.put(UNIT_COUNT + keysSuffix, count);
		return map;
	}

	private final String mustParseAuditString(final String recordData, final String key) throws Exception{
		final String value = parseAuditString(recordData, key);
		if(value == null){
			throw new MalformedAuditDataException("Missing field: " + key);
		}else{
			return value;
		}
	}

	private final String parseAuditString(final String recordData, final String key){
		final String formattedKey = key + "=";
		final int keyStartIndex = recordData.indexOf(formattedKey);
		if(keyStartIndex < 0){
			return null;
		}else{
			final int valueStartIndex = keyStartIndex + formattedKey.length();
			if(valueStartIndex >= recordData.length()){
				return null;
			}else{
				final char valueFirstChar = recordData.charAt(valueStartIndex);
				if(valueFirstChar == '"'){
					// is quoted string
					final int valueEndIndex = recordData.indexOf('"', valueStartIndex + 1);
					if(valueEndIndex < 0){
						return null;
					}else{
						return recordData.substring(valueStartIndex+1, valueEndIndex);
					}
				}else if(valueFirstChar == '('){
					// is quoted string
					final int valueEndIndex = recordData.indexOf(')', valueStartIndex + 1);
					if(valueEndIndex < 0){
						return null;
					}else{
						final String value = recordData.substring(valueStartIndex+1, valueEndIndex);
						if(value.equals("null")){
							return null;
						}else{
							return value;
						}
					}
				}else{
					// is hex string
					int valueEndIndex = recordData.indexOf(' ', valueStartIndex + 1);
					if(valueEndIndex < 0){
						valueEndIndex = recordData.length();
					}
					final String hexValue = recordData.substring(valueStartIndex, valueEndIndex);
					return HelperFunctions.decodeHex(hexValue);
				}
			}
		}
	}
}
