/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International

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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.Settings;
import spade.edge.opm.WasDerivedFrom;
import spade.reporter.Audit;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;
import spade.utility.Result;
import spade.vertex.opm.Artifact;

public class NetfilterHooksManager{

	private static final Logger logger = Logger.getLogger(NetfilterHooksManager.class.getName());
	private final void debug(String msg, Exception e){ if(debug){ logger.log(Level.WARNING, msg, e); } }
	private final void debug(String msg){ debug(msg, null); }

	private final static String keyDebug = "debug",
			keyMaxPerHookEntries = "maxPerHookEntries",
			keyHookEntryTtlMillis = "hookEntryTtlMillis",
			keyMaxSyscallEntries = "maxSyscallEntries",
			keySyscallEntryTtlMillis = "syscallEntryTtlMillis";

	/**
	 * @param level Level of the log message
	 * @param msg Message to print
	 * @param exception Exception (if any)
	 * @param time time of the audit event
	 * @param eventId id of the audit event
	 */
	private final void log(Level level, String msg, Exception exception, String time, String eventId){
		eventId = eventId == null ? "-1" : eventId;
		String msgPrefix = "[Time:EventID="+time+":"+eventId+"] ";
		if(exception == null){
			logger.log(level, msgPrefix + msg);
		}else{
			logger.log(level, msgPrefix + msg, exception);
		}
	}

	////////////////////////////////////////////

	private PreparedStatement 
			__syscall_statement_0 = null, __syscall_statement_1 = null, __syscall_statement_2 = null,
			__syscall_statement_3 = null, __syscall_statement_4 = null, __syscall_statement_5 = null,
			__localout_statement_0 = null, __localout_statement_1 = null, __localout_statement_2 = null,
			__localout_statement_3 = null, __localout_statement_4 = null,
			__postroute_statement_0 = null, __postroute_statement_1 = null, __postroute_statement_2 = null,
			__postroute_statement_3 = null,
			__preroute_statement_0 = null, __preroute_statement_1 = null, __preroute_statement_2 = null,
			__localin_statement_0 = null, __localin_statement_1 = null, __localin_statement_2 = null, 
			__localin_statement_3 = null, __localin_statement_4 = null;

	private final Map<String, PreparedStatement> __delete_row_by_id_statements = new HashMap<String, PreparedStatement>();
	private final Map<String, PreparedStatement> __delete_expired_statements = new HashMap<String, PreparedStatement>();
	private final Map<String, PreparedStatement> __delete_min_statements = new HashMap<String, PreparedStatement>();

	////////////////////////////////////////////

	private final Map<String, Long> statsMap = new HashMap<String, Long>();

	private final boolean debug;
	private final long maxPerHookEntries, hookEntryTtlMillis, maxSyscallEntries, syscallEntryTtlMillis;

	private final boolean namespaces;
	private final Audit reporter;
	private final Connection connection;

	private final Map<String, Long> perTableTtlMillis = new HashMap<String, Long>();
	private final Map<String, Long> perTableMaxCounts = new HashMap<String, Long>();
	private final Map<String, Long> perTableCurrentCounts = new HashMap<String, Long>();

	public NetfilterHooksManager(final Audit reporter, final boolean namespaces) throws Exception{
		this.namespaces = namespaces;
		this.reporter = reporter;
		if(reporter == null){
			throw new IllegalArgumentException("NULL reporter");
		}

		final String configFilePath = Settings.getDefaultConfigFilePath(this.getClass());
		final Map<String, String> configMap = new HashMap<String, String>();
		try{
			configMap.putAll(FileUtility.readConfigFileAsKeyValueMap(configFilePath, "="));
		}catch(Exception e){
			throw new IllegalArgumentException("Failed to read config file: " + configFilePath, e);
		}

		final String valueDebug = configMap.get(keyDebug);
		final Result<Boolean> resultDebug = HelperFunctions.parseBoolean(valueDebug);
		if(resultDebug.error){
			throw new IllegalArgumentException("Invalid flag value for '"+keyDebug+". " + resultDebug.toErrorString());
		}
		this.debug = resultDebug.result;

		//////////////////////////

		final String valuePerHookMaxEntries = configMap.get(keyMaxPerHookEntries);
		final Result<Long> resultPerHookMaxEntries = HelperFunctions.parseLong(valuePerHookMaxEntries, 10, 1, Long.MAX_VALUE);
		if(resultPerHookMaxEntries.error){
			throw new IllegalArgumentException("Invalid flag value for '"+keyMaxPerHookEntries+". " + resultPerHookMaxEntries.toErrorString());
		}
		this.maxPerHookEntries = resultPerHookMaxEntries.result.longValue();

		final String valueEntryTtlMillis = configMap.get(keyHookEntryTtlMillis);
		final Result<Long> resultEntryTtlMillis = HelperFunctions.parseLong(valueEntryTtlMillis, 10, 1, Long.MAX_VALUE);
		if(resultEntryTtlMillis.error){
			throw new IllegalArgumentException("Invalid flag value for '"+keyHookEntryTtlMillis+". " + resultEntryTtlMillis.toErrorString());
		}
		this.hookEntryTtlMillis = resultEntryTtlMillis.result.longValue();

		//////////////////////////

		final String valueMaxSyscallMappings = configMap.get(keyMaxSyscallEntries);
		final Result<Long> resultMaxSyscallMappings = HelperFunctions.parseLong(valueMaxSyscallMappings, 10, 1, Long.MAX_VALUE);
		if(resultMaxSyscallMappings.error){
			throw new IllegalArgumentException("Invalid flag value for '"+keyMaxSyscallEntries+". " + resultMaxSyscallMappings.toErrorString());
		}
		this.maxSyscallEntries = resultMaxSyscallMappings.result.longValue();

		final String valueSyscallMappingsTtlMillis = configMap.get(keySyscallEntryTtlMillis);
		final Result<Long> resultSyscallMappingsTtlMillis = HelperFunctions.parseLong(valueSyscallMappingsTtlMillis, 10, 1, Long.MAX_VALUE);
		if(resultSyscallMappingsTtlMillis.error){
			throw new IllegalArgumentException("Invalid flag value for '"+keySyscallEntryTtlMillis+". " + resultSyscallMappingsTtlMillis.toErrorString());
		}
		this.syscallEntryTtlMillis = resultSyscallMappingsTtlMillis.result;

		//////////////////////////

		logger.log(Level.INFO,
				"Arguments ["+keyDebug+"="+debug+", "
				+ ""+keyMaxPerHookEntries+"="+maxPerHookEntries+", "
				+ ""+keyHookEntryTtlMillis+"="+hookEntryTtlMillis+", "
				+ ""+keyMaxSyscallEntries+"="+maxSyscallEntries+", "
				+ ""+keySyscallEntryTtlMillis+"="+syscallEntryTtlMillis
				+"]");

		this.connection = DriverManager.getConnection("jdbc:h2:mem:");
		sqlCreateLocalInTable();
		sqlCreateLocalOutTable();
		sqlCreatePostRoutingTable();
		sqlCreatePreRoutingTable();
		sqlCreateSyscallTable();
		
		initializePreparedStatements();

		perTableCurrentCounts.put(sqlGetLocalInTableName(), 0L);
		perTableCurrentCounts.put(sqlGetLocalOutTableName(), 0L);
		perTableCurrentCounts.put(sqlGetPostRoutingTableName(), 0L);
		perTableCurrentCounts.put(sqlGetPreRoutingTableName(), 0L);
		perTableCurrentCounts.put(sqlGetSyscallTableName(), 0L);

		perTableMaxCounts.put(sqlGetLocalInTableName(), maxPerHookEntries);
		perTableMaxCounts.put(sqlGetLocalOutTableName(), maxPerHookEntries);
		perTableMaxCounts.put(sqlGetPostRoutingTableName(), maxPerHookEntries);
		perTableMaxCounts.put(sqlGetPreRoutingTableName(), maxPerHookEntries);
		perTableMaxCounts.put(sqlGetSyscallTableName(), maxSyscallEntries);

		perTableTtlMillis.put(sqlGetLocalInTableName(), hookEntryTtlMillis);
		perTableTtlMillis.put(sqlGetLocalOutTableName(), hookEntryTtlMillis);
		perTableTtlMillis.put(sqlGetPostRoutingTableName(), hookEntryTtlMillis);
		perTableTtlMillis.put(sqlGetPreRoutingTableName(), hookEntryTtlMillis);
		perTableTtlMillis.put(sqlGetSyscallTableName(), syscallEntryTtlMillis);
	}

	private final void incrementStats(String statId, long incrementBy){
		if(statsMap.get(statId) == null){
			statsMap.put(statId, 0L);
		}
		statsMap.put(statId, statsMap.get(statId) + incrementBy);
	}

	public final synchronized void printStats(){
		logger.log(Level.INFO, "Netfilter entries: " + perTableCurrentCounts + "]");
		logger.log(Level.INFO, "NetfilterStats[" + statsMap + "]");
	}

	public synchronized final void shutdown(){
		if(this.connection != null){
			try{
				this.connection.close();
			}catch(Exception e){
				logger.log(Level.WARNING, "Failed to close database connection", e);
			}
		}
	}

	private final long parseAuditEventTimeToMillis(String seconds) throws Exception{
		try{
			Double d = Double.parseDouble(seconds);
			return (long)(d * 1000);
		}catch(Exception e){
			throw new RuntimeException("Unexpected audit event time format: " + seconds, e);
		}
	}

	private final String formatMillisToAuditEventTime(Object millisObject){
		Long millis = (Long)millisObject;
		double eventTimeDouble = millis;
		eventTimeDouble = eventTimeDouble / 1000.0;
		return String.format("%.3f", eventTimeDouble);
	}

	private final boolean areTheFirstAndTheLastAddressesTheSame(Map<String, Object> row){
		return HelperFunctions.objectsEqual(row.get(sqlColumnNameRemoteIp), row.get(sqlColumnNameRemoteIpLast))
				&& HelperFunctions.objectsEqual(row.get(sqlColumnNameRemotePort), row.get(sqlColumnNameRemotePortLast))
				&& HelperFunctions.objectsEqual(row.get(sqlColumnNameLocalIp), row.get(sqlColumnNameLocalIpLast))
				&& HelperFunctions.objectsEqual(row.get(sqlColumnNameLocalPort), row.get(sqlColumnNameLocalPortLast));
	}

	private final Artifact __createNetworkArtifact(final Object netNs, final Object protocol, final Object remoteIp,
			final Object remotePort, final Object localIp, final Object localPort, final Object epoch, 
			final Object version, final Object source){
		Artifact artifact = new Artifact();
		if(netNs != null){ artifact.addAnnotation(OPMConstants.PROCESS_NET_NAMESPACE, netNs.toString()); }
		if(protocol != null){ artifact.addAnnotation(OPMConstants.ARTIFACT_PROTOCOL, protocol.toString()); }
		if(remoteIp != null){ artifact.addAnnotation(OPMConstants.ARTIFACT_REMOTE_ADDRESS, remoteIp.toString()); }
		if(remotePort != null){ artifact.addAnnotation(OPMConstants.ARTIFACT_REMOTE_PORT, remotePort.toString()); }
		if(localIp != null){ artifact.addAnnotation(OPMConstants.ARTIFACT_LOCAL_ADDRESS, localIp.toString()); }
		if(localPort != null){ artifact.addAnnotation(OPMConstants.ARTIFACT_LOCAL_PORT, localPort.toString()); }
		if(epoch != null){ artifact.addAnnotation(OPMConstants.ARTIFACT_EPOCH, epoch.toString()); }
		if(version != null){ artifact.addAnnotation(OPMConstants.ARTIFACT_VERSION, version.toString()); }
		if(source != null){ artifact.addAnnotation(OPMConstants.SOURCE, source.toString()); }
		artifact.addAnnotation(OPMConstants.ARTIFACT_SUBTYPE, OPMConstants.SUBTYPE_NETWORK_SOCKET);
		return artifact;
	}

	private final Artifact createNetfilterNetworkArtifact(final Object netNs, final Object protocol, final Object remoteIp,
			final Object remotePort, final Object localIp, final Object localPort, final Object epoch, 
			final Object version){
		return __createNetworkArtifact(netNs, protocol, remoteIp, remotePort, localIp, localPort, epoch, version, 
				OPMConstants.SOURCE_AUDIT_NETFILTER);
	}

	private final Artifact createSyscallNetworkArtifact(final Map<String, Object> syscallTableRow){
		return __createNetworkArtifact(syscallTableRow.get(sqlColumnNameNetns), syscallTableRow.get(sqlColumnNameProtocol), 
				syscallTableRow.get(sqlColumnNameRemoteIp), syscallTableRow.get(sqlColumnNameRemotePort), 
				syscallTableRow.get(sqlColumnNameLocalIp), syscallTableRow.get(sqlColumnNameLocalPort), 
				syscallTableRow.get(sqlColumnNameEpoch), syscallTableRow.get(sqlColumnNameVersion), 
				OPMConstants.SOURCE_AUDIT_SYSCALL);
	}

	private final void putNetfilterArtifact(final Artifact artifact){
		if(artifact != null){
			incrementStats("putVertex", 1);
			this.reporter.putVertex(artifact);
		}
	}

	private final void putNetfilterWasDerivedFromEdge(final Artifact from, final Artifact to, Map<String, Object> row){
		putNetfilterWasDerivedFromEdge(from, to, sqlGetEventTime(row), row.get(sqlColumnNameEventId));
	}

	private final void putNetfilterWasDerivedFromEdge(final Artifact from, final Artifact to, final long eventTime, final Object eventId){
		if(from != null && to != null){
			WasDerivedFrom edge = new WasDerivedFrom(from, to);
			edge.addAnnotation(OPMConstants.SOURCE, OPMConstants.SOURCE_AUDIT_NETFILTER);
			edge.addAnnotation(OPMConstants.EDGE_TIME, formatMillisToAuditEventTime(eventTime));
			if(eventId != null){ edge.addAnnotation(OPMConstants.EDGE_EVENT_ID, eventId.toString()); }
			edge.addAnnotation(OPMConstants.EDGE_OPERATION, OPMConstants.OPERATION_UPDATE);
			incrementStats("putEdge", 1);
			this.reporter.putEdge(edge);
		}
	}

	private final void sqlInsertSyscallTable(final long eventTime, final String eventId,
			final String version, final String epoch, final String netNs, final String protocol,
			final String remoteIp, final String remotePort, final String localIp, final String localPort,
			final boolean ingress) throws Exception{
		sm_syscallEventInsertSyscall(eventTime, eventId, version, epoch, netNs, protocol, remoteIp, remotePort, localIp, localPort, ingress);
	}

	////// function calls to implement new storage of data

	private final List<Map<String, Object>> sm_syscallEventFindLocalOut(final boolean isFirst, final String netNs,
			final String protocol, final String remoteIp, final String remotePort, final String localIp, final String localPort) 
					throws Exception{
		//__syscall_statement_0
		__syscall_statement_0.setBoolean(1, isFirst); 
		__syscall_statement_0.setString(2, netNs); 
		__syscall_statement_0.setString(3, protocol); 
		__syscall_statement_0.setString(4, remoteIp); 
		__syscall_statement_0.setString(5, remotePort); 
		__syscall_statement_0.setString(6, localIp);
		__syscall_statement_0.setString(7, localPort);
		return sqlGetResult(__syscall_statement_0);
	}

	private final List<Map<String, Object>> sm_syscallEventFindPostRouting(final boolean isFirst, final Object skbId, 
			final Object protocol, final Object remoteIp, final Object remotePort, final Object localIp, final Object localPort)
					throws Exception{
		//__syscall_statement_1
		__syscall_statement_1.setBoolean(1, isFirst);
		__syscall_statement_1.setObject(2, skbId);
		__syscall_statement_1.setObject(3, protocol);
		__syscall_statement_1.setObject(4, remoteIp);
		__syscall_statement_1.setObject(5, remotePort);
		__syscall_statement_1.setObject(6, localIp);
		__syscall_statement_1.setObject(7, localPort);
		return sqlGetResult(__syscall_statement_1);
	}

	private final void sm_syscallEventUpdateLocalOut(final String version, final String epoch, 
			final boolean updatedBySyscall, final long rowId) throws Exception{
		//__syscall_statement_2
		__syscall_statement_2.setString(1, version);
		__syscall_statement_2.setString(2, epoch); 
		__syscall_statement_2.setBoolean(3, updatedBySyscall);
		__syscall_statement_2.setLong(4, rowId);
		sqlUpdate(sqlGetLocalOutTableName(), __syscall_statement_2);
	}

	private final List<Map<String, Object>> sm_syscallEventFindLocalIn(final boolean isFirst, final String netNs, final String protocol,
			final String remoteIpLast, final String remotePortLast, final String localIpLast, final String localPortLast)
					throws Exception{
		//__syscall_statement_3
		__syscall_statement_3.setBoolean(1, isFirst);
		__syscall_statement_3.setString(2, netNs);
		__syscall_statement_3.setString(3, protocol); 
		__syscall_statement_3.setString(4, remoteIpLast);
		__syscall_statement_3.setString(5, remotePortLast);
		__syscall_statement_3.setString(6, localIpLast);
		__syscall_statement_3.setString(7, localPortLast);
		return sqlGetResult(__syscall_statement_3);
	}

	private final List<Map<String, Object>> sm_syscallEventFindPreRouting(final boolean isFirst, final Object skbId, final Object protocol,
			final Object remoteIpLast, final Object remotePortLast, final Object localIpLast, final Object localPortLast)
				throws Exception{
		//__syscall_statement_4
		__syscall_statement_4.setBoolean(1, isFirst);
		__syscall_statement_4.setObject(2, skbId); 
		__syscall_statement_4.setObject(3, protocol);
		__syscall_statement_4.setObject(4, remoteIpLast);
		__syscall_statement_4.setObject(5, remotePortLast);
		__syscall_statement_4.setObject(6, localIpLast);
		__syscall_statement_4.setObject(7, localPortLast);
		return sqlGetResult(__syscall_statement_4);
	}

	private final void sm_syscallEventInsertSyscall(final long eventTime, final String eventId, final String version,
			final String epoch, final String netNs, final String protocol, final String remoteIp, final String remotePort,
			final String localIp, final String localPort, final boolean isIngress) throws Exception{
		//__syscall_statement_5
		__syscall_statement_5.setLong(1, eventTime);
		__syscall_statement_5.setString(2, eventId);
		__syscall_statement_5.setString(3, version);
		__syscall_statement_5.setString(4, epoch);
		__syscall_statement_5.setString(5, netNs);
		__syscall_statement_5.setString(6, protocol);
		__syscall_statement_5.setString(7, remoteIp);
		__syscall_statement_5.setString(8, remotePort);
		__syscall_statement_5.setString(9, localIp);
		__syscall_statement_5.setString(10, localPort);
		__syscall_statement_5.setBoolean(11, isIngress);
		sqlInsert(sqlGetSyscallTableName(), eventTime, __syscall_statement_5);
	}

	//////

	private final void sm_localOutEventInsertLocalOut(final long eventTime, final String eventId,
			final String netNs, final String protocol, final String remoteIp, final String remotePort,
			final String localIp, final String localPort, final String skbId, final boolean isFirst, final boolean updatedBySyscall)
				throws Exception{
		//__localout_statement_0
		__localout_statement_0.setLong(1, eventTime);
		__localout_statement_0.setString(2, eventId);
		__localout_statement_0.setString(3, netNs);
		__localout_statement_0.setString(4, protocol);
		__localout_statement_0.setString(5, remoteIp);
		__localout_statement_0.setString(6, remotePort);
		__localout_statement_0.setString(7, localIp);
		__localout_statement_0.setString(8, localPort);
		__localout_statement_0.setString(9, skbId);
		__localout_statement_0.setBoolean(10, isFirst);
		__localout_statement_0.setBoolean(11, updatedBySyscall);
		sqlInsert(sqlGetLocalOutTableName(), eventTime, __localout_statement_0);
	}

	private final List<Map<String, Object>> sm_localOutEventFindLocalOut(final String skbId, final boolean isFirst, final String protocol)
		throws Exception{
		//__localout_statement_1
		__localout_statement_1.setString(1, skbId);
		__localout_statement_1.setBoolean(2, isFirst);
		__localout_statement_1.setString(3, protocol);
		return sqlGetResult(__localout_statement_1);
	}

	private final List<Map<String, Object>> sm_localOutEventFindSyscall(final boolean isIngress, final Object netNs, final Object protocol,
			final Object remoteIp, final Object remotePort, final Object localIp, final Object localPort) throws Exception{
		//__localout_statement_2
		__localout_statement_2.setBoolean(1, isIngress);
		__localout_statement_2.setObject(2, netNs);
		__localout_statement_2.setObject(3, protocol);
		__localout_statement_2.setObject(4, remoteIp);
		__localout_statement_2.setObject(5, remotePort);
		__localout_statement_2.setObject(6, localIp);
		__localout_statement_2.setObject(7, localPort);
		return sqlGetResult(__localout_statement_2);
	}

	private final void sm_localOutEventUpdateLocalOutByLast(final long eventTime, final String eventId, final String remoteIpLast,
			final String remotePortLast, final String localIpLast, final String localPortLast, final boolean isFirst,
			final long rowId) throws Exception{
		//__localout_statement_3
		__localout_statement_3.setLong(1, eventTime);
		__localout_statement_3.setString(2, eventId);
		__localout_statement_3.setString(3, remoteIpLast);
		__localout_statement_3.setString(4, remotePortLast);
		__localout_statement_3.setString(5, localIpLast);
		__localout_statement_3.setString(6, localPortLast);
		__localout_statement_3.setBoolean(7, isFirst);
		__localout_statement_3.setLong(8, rowId);
		sqlUpdate(sqlGetLocalOutTableName(), __localout_statement_3);
	}

	private final void sm_localOutEventUpdateLocalOutBySyscall(final long eventTime, final String eventId, final String remoteIpLast,
			final String remotePortLast, final String localIpLast, final String localPortLast, final boolean isFirst,
			final Object version, final Object epoch, final boolean updatedBySyscall, final long rowId) throws Exception{
		//__localout_statement_4
		__localout_statement_4.setLong(1, eventTime);
		__localout_statement_4.setString(2, eventId);
		__localout_statement_4.setString(3, remoteIpLast);
		__localout_statement_4.setString(4, remotePortLast);
		__localout_statement_4.setString(5, localIpLast);
		__localout_statement_4.setString(6, localPortLast);
		__localout_statement_4.setBoolean(7, isFirst);
		__localout_statement_4.setObject(8, version);
		__localout_statement_4.setObject(9, epoch);
		__localout_statement_4.setBoolean(10, updatedBySyscall);
		__localout_statement_4.setLong(11, rowId);
		sqlUpdate(sqlGetLocalOutTableName(), __localout_statement_4);
	}

	//////

	private final void sm_postRoutingEventInsertPostRouting(final long eventTime, final String eventId, final String protocol,
			final String remoteIp, final String remotePort, final String localIp, final String localPort, final String skbId,
			final boolean isFirst) throws Exception{
		//__postroute_statement_0
		__postroute_statement_0.setLong(1, eventTime);
		__postroute_statement_0.setString(2, eventId); 
		__postroute_statement_0.setString(3, protocol);
		__postroute_statement_0.setString(4, remoteIp);
		__postroute_statement_0.setString(5, remotePort);
		__postroute_statement_0.setString(6, localIp);
		__postroute_statement_0.setString(7, localPort);
		__postroute_statement_0.setString(8, skbId);
		__postroute_statement_0.setBoolean(9, isFirst);
		sqlInsert(sqlGetPostRoutingTableName(), eventTime, __postroute_statement_0);
	}

	private final List<Map<String, Object>> sm_postRoutingEventFindPostRouting(final String skbId, final boolean isFirst,
			final String protocol) throws Exception{
		//__postroute_statement_1
		__postroute_statement_1.setString(1, skbId);
		__postroute_statement_1.setBoolean(2, isFirst);
		__postroute_statement_1.setString(3, protocol);
		return sqlGetResult(__postroute_statement_1);
	}

	private final List<Map<String, Object>> sm_postRoutingEventFindLocalOut(final String skbId, final boolean isFirst,
			final boolean updatedBySyscall, final String protocol, final Object remoteIpLast, final Object remotePortLast,
			final Object localIpLast, final Object localPortLast) throws Exception{
		//__postroute_statement_2
		__postroute_statement_2.setString(1, skbId);
		__postroute_statement_2.setBoolean(2, isFirst);
		__postroute_statement_2.setBoolean(3, updatedBySyscall);
		__postroute_statement_2.setString(4, protocol);
		__postroute_statement_2.setObject(5, remoteIpLast);
		__postroute_statement_2.setObject(6, remotePortLast);
		__postroute_statement_2.setObject(7, localIpLast);
		__postroute_statement_2.setObject(8, localPortLast);
		return sqlGetResult(__postroute_statement_2);
	}

	private final void sm_postRoutingEventUpdatePostRouting(final long eventTime, final String eventId, final String remoteIpLast,
			final String remotePortLast, final String localIpLast, final String localPortLast, final boolean isFirst,
			final long rowId) throws Exception{
		//__postroute_statement_3
		__postroute_statement_3.setLong(1, eventTime);
		__postroute_statement_3.setString(2, eventId);
		__postroute_statement_3.setString(3, remoteIpLast);
		__postroute_statement_3.setString(4, remotePortLast);
		__postroute_statement_3.setString(5, localIpLast);
		__postroute_statement_3.setString(6, localPortLast);
		__postroute_statement_3.setBoolean(7, isFirst);
		__postroute_statement_3.setLong(8, rowId);
		sqlUpdate(sqlGetPostRoutingTableName(), __postroute_statement_3);
	}

	//////

	private final void sm_preRoutingEventInsertPreRouting(final long eventTime, final String eventId, final String protocol,
			final String remoteIp, final String remotePort, final String localIp, final String localPort, final String skbId,
			final boolean isFirst) throws Exception{
		//__preroute_statement_0
		__preroute_statement_0.setLong(1, eventTime);
		__preroute_statement_0.setString(2, eventId); 
		__preroute_statement_0.setString(3, protocol);
		__preroute_statement_0.setString(4, remoteIp);
		__preroute_statement_0.setString(5, remotePort);
		__preroute_statement_0.setString(6, localIp);
		__preroute_statement_0.setString(7, localPort);
		__preroute_statement_0.setString(8, skbId);
		__preroute_statement_0.setBoolean(9, isFirst);
		sqlInsert(sqlGetPreRoutingTableName(), eventTime, __preroute_statement_0);
	}

	private final List<Map<String, Object>> sm_preRoutingEventFindPreRoutingBySkb(final String skbId, final boolean isFirst,
			final String protocol) throws Exception{
		//__preroute_statement_1
		__preroute_statement_1.setString(1, skbId);
		__preroute_statement_1.setBoolean(2, isFirst);
		__preroute_statement_1.setString(3, protocol);
		return sqlGetResult(__preroute_statement_1);
	}

	private final void sm_preRoutingEventUpdatePreRouting(final long eventTime, final String eventId,
			final String remoteIpLast, final String remotePortLast, final String localIpLast, final String localPortLast,
			final boolean isFirst, final long rowId) throws Exception{
		//__preroute_statement_2
		__preroute_statement_2.setLong(1, eventTime);
		__preroute_statement_2.setString(2, eventId);
		__preroute_statement_2.setString(3, remoteIpLast);
		__preroute_statement_2.setString(4, remotePortLast);
		__preroute_statement_2.setString(5, localIpLast);
		__preroute_statement_2.setString(6, localPortLast);
		__preroute_statement_2.setBoolean(7, isFirst);
		__preroute_statement_2.setLong(8, rowId);
		sqlUpdate(sqlGetPostRoutingTableName(), __preroute_statement_2);
	}

	//////

	private final void sm_localInEventInsertLocalIn(final long eventTime, final String eventId, final String netNs, final String protocol,
			final String remoteIp, final String remotePort, final String localIp, final String localPort, final String skbId,
			final boolean isFirst) throws Exception{
		//__localin_statement_0
		__localin_statement_0.setLong(1, eventTime);
		__localin_statement_0.setString(2, eventId);
		__localin_statement_0.setString(3, netNs);
		__localin_statement_0.setString(4, protocol);
		__localin_statement_0.setString(5, remoteIp);
		__localin_statement_0.setString(6, remotePort);
		__localin_statement_0.setString(7, localIp);
		__localin_statement_0.setString(8, localPort);
		__localin_statement_0.setString(9, skbId);
		__localin_statement_0.setBoolean(10, isFirst);
		sqlInsert(sqlGetLocalInTableName(), eventTime, __localin_statement_0);
	}

	private final List<Map<String, Object>> sm_localInEventFindLocalIn(final String skbId, final boolean isFirst, final String protocol)
		throws Exception{
		//__localin_statement_1
		__localin_statement_1.setString(1, skbId);
		__localin_statement_1.setBoolean(2, isFirst);
		__localin_statement_1.setString(3, protocol);
		return sqlGetResult(__localin_statement_1);
	}

	private final List<Map<String, Object>> sm_localInEventFindSyscall(final boolean isIngress, final String netNs, final String protocol,
			final String remoteIp, final String remotePort, final String localIp, final String localPort) throws Exception{
		//__localin_statement_2
		__localin_statement_2.setBoolean(1, isIngress);
		__localin_statement_2.setString(2, netNs);
		__localin_statement_2.setString(3, protocol);
		__localin_statement_2.setString(4, remoteIp);
		__localin_statement_2.setString(5, remotePort);
		__localin_statement_2.setString(6, localIp);
		__localin_statement_2.setString(7, localPort);
		return sqlGetResult(__localin_statement_2);
	}

	private final void sm_localInEventUpdateLocalIn(final long eventTime, final String eventId, final String remoteIpLast,
			final String remotePortLast, final String localIpLast, final String localPortLast, final boolean isFirst,
			final String netNs, final long rowId) throws Exception{
		//__localin_statement_3
		__localin_statement_3.setLong(1, eventTime);
		__localin_statement_3.setString(2, eventId);
		__localin_statement_3.setString(3, remoteIpLast);
		__localin_statement_3.setString(4, remotePortLast);
		__localin_statement_3.setString(5, localIpLast);
		__localin_statement_3.setString(6, localPortLast);
		__localin_statement_3.setBoolean(7, isFirst);
		__localin_statement_3.setString(8, netNs); 
		__localin_statement_3.setLong(9, rowId);
		sqlUpdate(sqlGetLocalInTableName(), __localin_statement_3);
	}

	private final List<Map<String, Object>> sm_localInEventFindPreRouting(final String skbId, final boolean isFirst, final String protocol)
		throws Exception{
		//__localin_statement_4
		__localin_statement_4.setString(1, skbId);
		__localin_statement_4.setBoolean(2, isFirst);
		__localin_statement_4.setString(3, protocol);
		return sqlGetResult(__localin_statement_4);
	}

	//////

	private final int sm_deleteRowById(final String tableName, final long rowId) throws Exception{
		PreparedStatement statement = __delete_row_by_id_statements.get(tableName);
		statement.setLong(1, rowId);
		return statement.executeUpdate();
	}

	private final int sm_deleteExpiredRows(final String tableName, final long olderThanEventTime) throws Exception{
		final PreparedStatement deleteStatement0 = __delete_expired_statements.get(tableName);
		deleteStatement0.setLong(1, olderThanEventTime);
		return deleteStatement0.executeUpdate();
	}

	private final int sm_deleteOldestRows(final String tableName) throws Exception{
		final PreparedStatement deleteStatement1 = __delete_min_statements.get(tableName);
		return deleteStatement1.executeUpdate();
	}

	////// function calls to implement new storage of data

	private final synchronized void initializePreparedStatements() throws Exception{
		__syscall_statement_0 = connection.prepareStatement("select * from " + sqlGetLocalOutTableName() + " where"
				+ " ("+sqlColumnNameIsFirst+"=?) and " + sqlColumnNameNetns + "=? and " + sqlColumnNameProtocol + "=? and "
				+ sqlColumnNameRemoteIp + "=? and " + sqlColumnNameRemotePort + "=? and " + sqlColumnNameLocalIp + "=? and " 
				+ sqlColumnNameLocalPort + "=?");
		__syscall_statement_1 = connection.prepareStatement("select * from " + sqlGetPostRoutingTableName() + " where"
				+ " ("+sqlColumnNameIsFirst+"=?) and " + sqlColumnNameSkbId + "=? and " + sqlColumnNameProtocol + "=? and "
				+ sqlColumnNameRemoteIp + "=? and " + sqlColumnNameRemotePort + "=? and " + sqlColumnNameLocalIp + "=? and " 
				+ sqlColumnNameLocalPort + "=?");
		__syscall_statement_2 = connection.prepareStatement("update " + sqlGetLocalOutTableName() + " set"
				+ " " + sqlColumnNameVersion + "=?," + " " + sqlColumnNameEpoch + "=?,"
				+ " " + sqlColumnNameUpdatedBySyscall + "=?" + " where " + sqlColumnNameRowId + "=?");
		__syscall_statement_3 = connection.prepareStatement("select * from " + sqlGetLocalInTableName() + " where"
				+ " ("+sqlColumnNameIsFirst+"=?) and " + sqlColumnNameNetns + "=? and " + sqlColumnNameProtocol + "=? and "
				+ sqlColumnNameRemoteIpLast + "=? and " + sqlColumnNameRemotePortLast + "=? and " + sqlColumnNameLocalIpLast + "=? and " 
				+ sqlColumnNameLocalPortLast + "=?");
		__syscall_statement_4 = connection.prepareStatement("select * from " + sqlGetPreRoutingTableName() + " where "
				+ "(" + sqlColumnNameIsFirst + "=?) and " + sqlColumnNameSkbId + "=? and " + sqlColumnNameProtocol + "=? and "
				+ sqlColumnNameRemoteIpLast + "=? and " + sqlColumnNameRemotePortLast + "=? and " + sqlColumnNameLocalIpLast + "=? and "
				+ sqlColumnNameLocalPortLast + "=?");
		__syscall_statement_5 = connection.prepareStatement("insert into " + sqlGetSyscallTableName()
				+ " (" + sqlColumnNameEventTime + ", " + sqlColumnNameEventId + ", " + sqlColumnNameVersion + ", " + sqlColumnNameEpoch
				+ ", " + sqlColumnNameNetns + ", " + sqlColumnNameProtocol + ", " + sqlColumnNameRemoteIp
				+ ", " + sqlColumnNameRemotePort + ", " + sqlColumnNameLocalIp + ", " + sqlColumnNameLocalPort
				+ ", " + sqlColumnNameIsIngress
				+ ") values (?, ? ,?, ?, ? ,?, ?, ? ,?, ?, ?)");
		////
		__localout_statement_0 = connection.prepareStatement("insert into " + sqlGetLocalOutTableName() 
				+ "(" + sqlColumnNameEventTime + ", " + sqlColumnNameEventId + ", " + sqlColumnNameNetns + ", " + sqlColumnNameProtocol
				+ ", " + sqlColumnNameRemoteIp + ", " + sqlColumnNameRemotePort + ", " + sqlColumnNameLocalIp + ", " + sqlColumnNameLocalPort
				+ ", " + sqlColumnNameSkbId + ", " + sqlColumnNameIsFirst + ", " + sqlColumnNameUpdatedBySyscall
				+ ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
		__localout_statement_1 = connection.prepareStatement("select * from " + sqlGetLocalOutTableName() + " where "
				+ sqlColumnNameSkbId + "=? and " + sqlColumnNameIsFirst + "=? and " + sqlColumnNameProtocol + "=?");
		__localout_statement_2 = connection.prepareStatement("select * from " + sqlGetSyscallTableName() + " where "
				+ sqlColumnNameIsIngress + "=? and " + sqlColumnNameNetns + "=? and " + sqlColumnNameProtocol + "=? and "
				+ sqlColumnNameRemoteIp + "=? and " + sqlColumnNameRemotePort + "=? and " + sqlColumnNameLocalIp + "=? and "
				+ sqlColumnNameLocalPort + "=?"
				);
		__localout_statement_3 = connection.prepareStatement("update " + sqlGetLocalOutTableName() + " set "
				+ sqlColumnNameEventTime + "=?, " + sqlColumnNameEventId + "=?, " + sqlColumnNameRemoteIpLast + "=?, "
				+ sqlColumnNameRemotePortLast + "=?, " + sqlColumnNameLocalIpLast + "=?, " + sqlColumnNameLocalPortLast + "=?, "
				+ sqlColumnNameIsFirst + "=? where " + sqlColumnNameRowId + "=?");
		__localout_statement_4 = connection.prepareStatement("update " + sqlGetLocalOutTableName() + " set "
				+ sqlColumnNameEventTime + "=?, " + sqlColumnNameEventId + "=?, " + sqlColumnNameRemoteIpLast + "=?, "
				+ sqlColumnNameRemotePortLast + "=?, " + sqlColumnNameLocalIpLast + "=?, " + sqlColumnNameLocalPortLast + "=?, "
				+ sqlColumnNameIsFirst + "=?, " + sqlColumnNameVersion + "=?, " + sqlColumnNameEpoch + "=?, "
				+ sqlColumnNameUpdatedBySyscall + "=? where " + sqlColumnNameRowId + "=?");
		////
		__postroute_statement_0 = connection.prepareStatement("insert into " + sqlGetPostRoutingTableName()
				+ "(" + sqlColumnNameEventTime + ", " + sqlColumnNameEventId + ", " + sqlColumnNameProtocol
				+ ", " + sqlColumnNameRemoteIp + ", " + sqlColumnNameRemotePort + ", " + sqlColumnNameLocalIp + ", " + sqlColumnNameLocalPort
				+ ", " + sqlColumnNameSkbId + ", " + sqlColumnNameIsFirst
				+ ") values (?, ?, ?, ?, ?, ?, ?, ?, ?)");
		__postroute_statement_1 = connection.prepareStatement("select * from " + sqlGetPostRoutingTableName() + " where "
				+ sqlColumnNameSkbId + "=? and " + sqlColumnNameIsFirst + "=? and " + sqlColumnNameProtocol + "=?");
		__postroute_statement_2 = connection.prepareStatement("select * from " + sqlGetLocalOutTableName() + " where "
				+ sqlColumnNameSkbId + "=? and " + sqlColumnNameIsFirst + "=? and " + sqlColumnNameUpdatedBySyscall + "=? and "
				+ sqlColumnNameProtocol + "=? and " + sqlColumnNameRemoteIpLast + "=? and "
				+ sqlColumnNameRemotePortLast + "=? and " + sqlColumnNameLocalIpLast + "=? and " + sqlColumnNameLocalPortLast + "=?");
		__postroute_statement_3 = connection.prepareStatement("update " + sqlGetPostRoutingTableName() + " set "
				+ sqlColumnNameEventTime + "=?, " + sqlColumnNameEventId + "=?, " + sqlColumnNameRemoteIpLast + "=?, "
				+ sqlColumnNameRemotePortLast + "=?, " + sqlColumnNameLocalIpLast + "=?, " + sqlColumnNameLocalPortLast + "=?, "
				+ sqlColumnNameIsFirst + "=? where " + sqlColumnNameRowId + "=?");
		////
		__preroute_statement_0 = connection.prepareStatement("insert into " + sqlGetPreRoutingTableName()
				+ "(" + sqlColumnNameEventTime + ", " + sqlColumnNameEventId + ", " + sqlColumnNameProtocol
				+ ", " + sqlColumnNameRemoteIp + ", " + sqlColumnNameRemotePort + ", " + sqlColumnNameLocalIp + ", " + sqlColumnNameLocalPort
				+ ", " + sqlColumnNameSkbId + ", " + sqlColumnNameIsFirst
				+ ") values (?, ?, ?, ?, ?, ?, ?, ?, ?)");
		__preroute_statement_1 = connection.prepareStatement("select * from " + sqlGetPreRoutingTableName() + " where "
				+ sqlColumnNameSkbId + "=? and " + sqlColumnNameIsFirst + "=? and " + sqlColumnNameProtocol + "=?");
		__preroute_statement_2 = connection.prepareStatement("update " + sqlGetPreRoutingTableName() + " set "
				+ sqlColumnNameEventTime + "=?, " + sqlColumnNameEventId + "=?, " + sqlColumnNameRemoteIpLast + "=?, "
				+ sqlColumnNameRemotePortLast + "=?, " + sqlColumnNameLocalIpLast + "=?, " + sqlColumnNameLocalPortLast + "=?, "
				+ sqlColumnNameIsFirst + "=? where " + sqlColumnNameRowId + "=?");
		////
		__localin_statement_0 = connection.prepareStatement("insert into " + sqlGetLocalInTableName()
				+ "(" + sqlColumnNameEventTime + ", " + sqlColumnNameEventId + ", " + sqlColumnNameNetns + ", " + sqlColumnNameProtocol
				+ ", " + sqlColumnNameRemoteIp + ", " + sqlColumnNameRemotePort + ", " + sqlColumnNameLocalIp + ", " + sqlColumnNameLocalPort
				+ ", " + sqlColumnNameSkbId + ", " + sqlColumnNameIsFirst
				+ ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
		__localin_statement_1 = connection.prepareStatement("select * from " + sqlGetLocalInTableName() + " where "
				+ sqlColumnNameSkbId + "=? and " + sqlColumnNameIsFirst + "=? and " + sqlColumnNameProtocol + "=?");
		__localin_statement_2 = connection.prepareStatement("select * from " + sqlGetSyscallTableName() + " where "
				+ sqlColumnNameIsIngress + "=? and " + sqlColumnNameNetns + "=? and " + sqlColumnNameProtocol + "=? and "
				+ sqlColumnNameRemoteIp + "=? and " + sqlColumnNameRemotePort + "=? and " + sqlColumnNameLocalIp + "=? and "
				+ sqlColumnNameLocalPort + "=?");
		__localin_statement_3 = connection.prepareStatement("update " + sqlGetLocalInTableName() + " set "
				+ sqlColumnNameEventTime + "=?, " + sqlColumnNameEventId + "=?, " 
				+ sqlColumnNameRemoteIpLast + "=?, " + sqlColumnNameRemotePortLast + "=?, "
				+ sqlColumnNameLocalIpLast + "=?, " + sqlColumnNameLocalPortLast + "=?, "
				+ sqlColumnNameIsFirst + "=?, " + sqlColumnNameNetns + "=? where "
				+ sqlColumnNameRowId + "=?");
		__localin_statement_4 = connection.prepareStatement("select * from " + sqlGetPreRoutingTableName() + " where "
				+ sqlColumnNameSkbId + "=? and " + sqlColumnNameIsFirst + "=? and " + sqlColumnNameProtocol + "=?");
		////
		__delete_row_by_id_statements.put(sqlGetSyscallTableName(), connection.prepareStatement("delete from " + sqlGetSyscallTableName() + " where " + sqlColumnNameRowId + "=?"));
		__delete_row_by_id_statements.put(sqlGetLocalOutTableName(), connection.prepareStatement("delete from " + sqlGetLocalOutTableName() + " where " + sqlColumnNameRowId + "=?"));
		__delete_row_by_id_statements.put(sqlGetLocalInTableName(), connection.prepareStatement("delete from " + sqlGetLocalInTableName() + " where " + sqlColumnNameRowId + "=?"));
		__delete_row_by_id_statements.put(sqlGetPostRoutingTableName(), connection.prepareStatement("delete from " + sqlGetPostRoutingTableName() + " where " + sqlColumnNameRowId + "=?"));
		__delete_row_by_id_statements.put(sqlGetPreRoutingTableName(), connection.prepareStatement("delete from " + sqlGetPreRoutingTableName() + " where " + sqlColumnNameRowId + "=?"));
		////
		__delete_expired_statements.put(sqlGetSyscallTableName(), connection.prepareStatement("delete from "+ sqlGetSyscallTableName() + " where " + sqlColumnNameEventTime + " < ?"));
		__delete_expired_statements.put(sqlGetLocalOutTableName(), connection.prepareStatement("delete from "+ sqlGetLocalOutTableName() + " where " + sqlColumnNameEventTime + " < ?"));
		__delete_expired_statements.put(sqlGetLocalInTableName(), connection.prepareStatement("delete from "+ sqlGetLocalInTableName() + " where " + sqlColumnNameEventTime + " < ?"));
		__delete_expired_statements.put(sqlGetPostRoutingTableName(), connection.prepareStatement("delete from "+ sqlGetPostRoutingTableName() + " where " + sqlColumnNameEventTime + " < ?"));
		__delete_expired_statements.put(sqlGetPreRoutingTableName(), connection.prepareStatement("delete from "+ sqlGetPreRoutingTableName() + " where " + sqlColumnNameEventTime + " < ?"));
		////
		__delete_min_statements.put(sqlGetSyscallTableName(), connection.prepareStatement("delete from "+ sqlGetSyscallTableName() + " where " + sqlColumnNameEventTime + " in (select min("+sqlColumnNameEventTime+") from "+sqlGetSyscallTableName()+")"));
		__delete_min_statements.put(sqlGetLocalOutTableName(), connection.prepareStatement("delete from "+ sqlGetLocalOutTableName() + " where " + sqlColumnNameEventTime + " in (select min("+sqlColumnNameEventTime+") from "+sqlGetLocalOutTableName()+")"));
		__delete_min_statements.put(sqlGetLocalInTableName(), connection.prepareStatement("delete from "+ sqlGetLocalInTableName() + " where " + sqlColumnNameEventTime + " in (select min("+sqlColumnNameEventTime+") from "+sqlGetLocalInTableName()+")"));
		__delete_min_statements.put(sqlGetPostRoutingTableName(), connection.prepareStatement("delete from "+ sqlGetPostRoutingTableName() + " where " + sqlColumnNameEventTime + " in (select min("+sqlColumnNameEventTime+") from "+sqlGetPostRoutingTableName()+")"));
		__delete_min_statements.put(sqlGetPreRoutingTableName(), connection.prepareStatement("delete from "+ sqlGetPreRoutingTableName() + " where " + sqlColumnNameEventTime + " in (select min("+sqlColumnNameEventTime+") from "+sqlGetPreRoutingTableName()+")"));
	}

	public synchronized final void handleNetworkSyscallEvent(final String eventTimeString, final String eventId, final boolean ingress,
			final Artifact syscallArtifact) throws Exception{
		final long eventTime = parseAuditEventTimeToMillis(eventTimeString);
		if(!OPMConstants.isNetworkArtifact(syscallArtifact)){
			return;
		}

		final String version = syscallArtifact.getAnnotation(OPMConstants.ARTIFACT_VERSION);
		final String epoch = syscallArtifact.getAnnotation(OPMConstants.ARTIFACT_EPOCH);
		final String netNs = syscallArtifact.getAnnotation(OPMConstants.PROCESS_NET_NAMESPACE);
		final String protocol = syscallArtifact.getAnnotation(OPMConstants.ARTIFACT_PROTOCOL);
		final String localIp = syscallArtifact.getAnnotation(OPMConstants.ARTIFACT_LOCAL_ADDRESS);
		final String localPort = syscallArtifact.getAnnotation(OPMConstants.ARTIFACT_LOCAL_PORT);
		final String remoteIp = syscallArtifact.getAnnotation(OPMConstants.ARTIFACT_REMOTE_ADDRESS);
		final String remotePort = syscallArtifact.getAnnotation(OPMConstants.ARTIFACT_REMOTE_PORT);

		if(!ingress){ // outgoing
			final List<Map<String, Object>> result0 =
					sm_syscallEventFindLocalOut(false, netNs, protocol, remoteIp, remotePort, localIp, localPort);
			if(result0.size() == 0){
				sqlInsertSyscallTable(eventTime, eventId, version, epoch, netNs, protocol, remoteIp, remotePort, localIp, localPort, ingress);
			}else{
				final Map<String, Object> row0 = result0.get(0);
				if(result0.size() > 1){
					debug("Expected only one match in "+sqlGetLocalOutTableName()+" but found '"+result0.size()+"' for: " + syscallArtifact);
				}
				final boolean row0AddressesAreSame = areTheFirstAndTheLastAddressesTheSame(row0);
				if(row0AddressesAreSame){
					// Don't draw an edge
				}else{
					final Artifact netfilterArtifact0 = createNetfilterNetworkArtifact(
							row0.get(sqlColumnNameNetns), row0.get(sqlColumnNameProtocol), row0.get(sqlColumnNameRemoteIpLast), 
							row0.get(sqlColumnNameRemotePortLast), row0.get(sqlColumnNameLocalIpLast), row0.get(sqlColumnNameLocalPortLast), 
							epoch, version);
					putNetfilterArtifact(netfilterArtifact0);
					putNetfilterWasDerivedFromEdge(netfilterArtifact0, syscallArtifact, row0);
				}
				final List<Map<String, Object>> result1 = 
						sm_syscallEventFindPostRouting(false, row0.get(sqlColumnNameSkbId), row0.get(sqlColumnNameProtocol), 
								row0.get(sqlColumnNameRemoteIpLast), row0.get(sqlColumnNameRemotePortLast), 
								row0.get(sqlColumnNameLocalIpLast), row0.get(sqlColumnNameLocalPortLast));
				if(result1.size() == 0){
					sm_syscallEventUpdateLocalOut(version, epoch, true, sqlGetRowIdForSql(row0));
				}else{
					final Map<String, Object> row1 = result1.get(0);
					if(result1.size() > 1){
						debug("Expected only one match in "+sqlGetPostRoutingTableName()+" but found '"+result1.size()+"' for: " 
								+ syscallArtifact);
					}
					final boolean row1AddressesAreSame = areTheFirstAndTheLastAddressesTheSame(row1);
					if(row1AddressesAreSame){
						// Don't draw an edge
					}else{
						final Artifact sourceArtifact = row0AddressesAreSame ? syscallArtifact 
								: createNetfilterNetworkArtifact(
										row0.get(sqlColumnNameNetns), row0.get(sqlColumnNameProtocol), 
										row0.get(sqlColumnNameRemoteIpLast), row0.get(sqlColumnNameRemotePortLast), 
										row0.get(sqlColumnNameLocalIpLast), row0.get(sqlColumnNameLocalPortLast), 
										epoch, version);
						final Artifact netfilterArtifact1 = createNetfilterNetworkArtifact(
								sourceArtifact.getAnnotation(OPMConstants.PROCESS_NET_NAMESPACE), 
								row1.get(sqlColumnNameProtocol), 
								row1.get(sqlColumnNameRemoteIpLast), row1.get(sqlColumnNameRemotePortLast), 
								row1.get(sqlColumnNameLocalIpLast), row1.get(sqlColumnNameLocalPortLast), 
								sourceArtifact.getAnnotation(OPMConstants.ARTIFACT_EPOCH), 
								sourceArtifact.getAnnotation(OPMConstants.ARTIFACT_VERSION));
						putNetfilterArtifact(netfilterArtifact1);
						putNetfilterWasDerivedFromEdge(netfilterArtifact1, sourceArtifact, row1);
					}
					sqlDeleteRowByRowId(sqlGetLocalOutTableName(), row0);
					sqlDeleteRowByRowId(sqlGetPostRoutingTableName(), row1);
				}
			}
		}else{
			// incoming i.e. ingress = true
			final List<Map<String, Object>> result0 = 
					sm_syscallEventFindLocalIn(false, netNs, protocol, remoteIp, remotePort, localIp, localPort);
			if(result0.size() == 0){
				sqlInsertSyscallTable(eventTime, eventId, version, epoch, netNs, protocol, remoteIp, remotePort, localIp, localPort, ingress);
			}else{
				final Map<String, Object> row0 = result0.get(0);
				if(result0.size() > 1){
					debug("Expected only one match in "+sqlGetLocalInTableName()+" but found '"+result0.size()+"' for: " 
							+ syscallArtifact);
				}
				final boolean areFirstAndLastAddressesSame = areTheFirstAndTheLastAddressesTheSame(row0);
				if(areFirstAndLastAddressesSame){
					// Don't draw an edge
				}else{
					final Artifact netfilterArtifact0 = createNetfilterNetworkArtifact(
							row0.get(sqlColumnNameNetns), row0.get(sqlColumnNameProtocol), row0.get(sqlColumnNameRemoteIp), 
							row0.get(sqlColumnNameRemotePort), row0.get(sqlColumnNameLocalIp), row0.get(sqlColumnNameLocalPort), 
							epoch, version);
					putNetfilterArtifact(netfilterArtifact0);
					putNetfilterWasDerivedFromEdge(syscallArtifact, netfilterArtifact0, row0);
				}
				final List<Map<String, Object>> result1 = 
						sm_syscallEventFindPreRouting(false, row0.get(sqlColumnNameSkbId), row0.get(sqlColumnNameProtocol), 
								row0.get(sqlColumnNameRemoteIp), row0.get(sqlColumnNameRemotePort), row0.get(sqlColumnNameLocalIp), 
								row0.get(sqlColumnNameLocalPort));
		
				if(result1.size() == 0){
					// nothing to do
				}else{
					final Map<String, Object> row1 = result1.get(0);
					if(result1.size() > 1){
						debug("Expected only one match in "+sqlGetPreRoutingTableName()+" but found '"+result1.size()+"' for: " 
								+ syscallArtifact);
					}
					final Artifact sourceArtifact = areFirstAndLastAddressesSame ? syscallArtifact :
						createNetfilterNetworkArtifact(
								row0.get(sqlColumnNameNetns), row0.get(sqlColumnNameProtocol), row0.get(sqlColumnNameRemoteIp), 
								row0.get(sqlColumnNameRemotePort), row0.get(sqlColumnNameLocalIp), row0.get(sqlColumnNameLocalPort), 
								epoch, version);
					final Artifact netfilterArtifact1 = createNetfilterNetworkArtifact(
							sourceArtifact.getAnnotation(OPMConstants.PROCESS_NET_NAMESPACE), 
							row1.get(sqlColumnNameProtocol), row1.get(sqlColumnNameRemoteIp), 
							row1.get(sqlColumnNameRemotePort), row1.get(sqlColumnNameLocalIp), 
							row1.get(sqlColumnNameLocalPort), sourceArtifact.getAnnotation(OPMConstants.ARTIFACT_EPOCH), 
							sourceArtifact.getAnnotation(OPMConstants.ARTIFACT_VERSION));
					putNetfilterArtifact(netfilterArtifact1);
					putNetfilterWasDerivedFromEdge(sourceArtifact, netfilterArtifact1, row1);
					sqlDeleteRowByRowId(sqlGetPreRoutingTableName(), row1);
				}
				sqlDeleteRowByRowId(sqlGetLocalInTableName(), row0);
			}
		}
	}
	
	public final synchronized void handleNetfilterHookEvent(final Map<String, String> eventData) throws Exception{
		final String time = eventData.get(AuditEventReader.TIME);
		final String eventId = eventData.get(AuditEventReader.EVENT_ID);
		final String hookName = eventData.get(AuditEventReader.NF_HOOK_KEY);
    	final String priorityName = eventData.get(AuditEventReader.NF_PRIORITY_KEY);
    	final String id = eventData.get(AuditEventReader.NF_ID_KEY);
    	final String srcIp = eventData.get(AuditEventReader.NF_SRC_IP_KEY);
    	final String srcPort = eventData.get(AuditEventReader.NF_SRC_PORT_KEY);
    	final String dstIp = eventData.get(AuditEventReader.NF_DST_IP_KEY);
    	final String dstPort = eventData.get(AuditEventReader.NF_DST_PORT_KEY);
    	final String protocolName = eventData.get(AuditEventReader.NF_PROTOCOL_KEY);
    	final String ipVersionName = eventData.get(AuditEventReader.NF_IP_VERSION_KEY);
    	String netNsInum = eventData.get(AuditEventReader.NF_NET_NS_INUM_KEY);
    	
    	if(HelperFunctions.isNullOrEmpty(time)){
    		log(Level.WARNING, "NULL/Empty id '"+time+"'", null, time, eventId);
			return;
    	}
    	
    	if(HelperFunctions.isNullOrEmpty(hookName)){
    		log(Level.WARNING, "NULL/Empty hookName '"+hookName+"'", null, time, eventId);
			return;
    	}
    	
    	if(HelperFunctions.isNullOrEmpty(priorityName)){
    		log(Level.WARNING, "NULL/Empty priorityName '"+priorityName+"'", null, time, eventId);
			return;
    	}
    	
    	if(HelperFunctions.isNullOrEmpty(protocolName)){
    		log(Level.WARNING, "NULL/Empty protocolName '"+protocolName+"'", null, time, eventId);
			return;
    	}
    	
    	if(HelperFunctions.isNullOrEmpty(ipVersionName)){
    		log(Level.WARNING, "NULL/Empty ipVersionName '"+ipVersionName+"'", null, time, eventId);
			return;
    	}
    	
    	if(HelperFunctions.isNullOrEmpty(id)){
    		log(Level.WARNING, "NULL/Empty id '"+id+"'", null, time, eventId);
			return;
    	}
    	
    	if(HelperFunctions.isNullOrEmpty(srcIp)){
    		log(Level.WARNING, "NULL/Empty src IP '"+srcIp+"'", null, time, eventId);
			return;
    	}
    	
    	if(HelperFunctions.isNullOrEmpty(srcPort)){
    		log(Level.WARNING, "NULL/Empty src port '"+srcPort+"'", null, time, eventId);
			return;
    	}
    	
    	if(HelperFunctions.isNullOrEmpty(dstIp)){
    		log(Level.WARNING, "NULL/Empty dst IP '"+dstIp+"'", null, time, eventId);
			return;
    	}
    	
    	if(HelperFunctions.isNullOrEmpty(dstPort)){
    		log(Level.WARNING, "NULL/Empty dst port '"+dstPort+"'", null, time, eventId);
			return;
    	}
    	
    	if(namespaces){
	    	if(HelperFunctions.isNullOrEmpty(netNsInum)){
	    		log(Level.WARNING, "NULL/Empty net ns inum '"+netNsInum+"'", null, time, eventId);
				return;
	    	}
    	}else{
    		// Set to null so that the exact matching doesn't trip up
    		netNsInum = null;
    	}
    	
    	final boolean isIpv4; // or ipv6
    	switch(ipVersionName){
			case AuditEventReader.NF_IP_VERSION_VALUE_IPV4: isIpv4 = true; break;
			case AuditEventReader.NF_IP_VERSION_VALUE_IPV6: isIpv4 = false; break;
			default: 
				log(Level.WARNING, "Unexpected IP version '"+ipVersionName+"' instead of: " + 
						Arrays.asList(AuditEventReader.NF_IP_VERSION_VALUE_IPV4, AuditEventReader.NF_IP_VERSION_VALUE_IPV6), 
						null, time, eventId);
			return;
		}
    	
    	final boolean isTcp; // or udp
    	switch(protocolName){
			case AuditEventReader.NF_PROTOCOL_VALUE_TCP: isTcp = true; break;
			case AuditEventReader.NF_PROTOCOL_VALUE_UDP: isTcp = false; break;
			default:
				log(Level.WARNING, "Unexpected protocol '"+protocolName+"' instead of: " + 
						Arrays.asList(AuditEventReader.NF_PROTOCOL_VALUE_TCP, AuditEventReader.NF_PROTOCOL_VALUE_UDP), 
						null, time, eventId); 
			return;
		}
    	
    	final boolean isFirst; // or last
    	switch(priorityName){
			case AuditEventReader.NF_PRIORITY_VALUE_FIRST: isFirst = true; break;
			case AuditEventReader.NF_PRIORITY_VALUE_LAST: isFirst = false; break;
			default:
				log(Level.WARNING, "Unexpected priority '"+priorityName+"' instead of: " + 
						Arrays.asList(AuditEventReader.NF_PRIORITY_VALUE_FIRST, AuditEventReader.NF_PRIORITY_VALUE_LAST), 
						null, time, eventId);
			return;
		}
    	
    	final String protocol;
    	if(isTcp){
    		protocol = Audit.PROTOCOL_NAME_TCP;
    	}else{
    		protocol = Audit.PROTOCOL_NAME_UDP;
    	}
    	
    	final long eventTime = parseAuditEventTimeToMillis(time);
    	
    	switch(hookName){
			case AuditEventReader.NF_HOOK_VALUE_LOCAL_OUT:
				handleNetfilterLocalOutEvent(eventData, isFirst, srcIp, srcPort, dstIp, dstPort, protocol, 
						netNsInum, id, eventTime, eventId);
				break;
			case AuditEventReader.NF_HOOK_VALUE_POST_ROUTING:
				handleNetfilterPostRoutingEvent(eventData, isFirst, srcIp, srcPort, dstIp, dstPort, protocol, 
						netNsInum, id, eventTime, eventId);
				break;
			case AuditEventReader.NF_HOOK_VALUE_LOCAL_IN:
				handleNetfilterLocalInEvent(eventData, isFirst, dstIp, dstPort, srcIp, srcPort, protocol, 
						netNsInum, id, eventTime, eventId);
				break;
			case AuditEventReader.NF_HOOK_VALUE_PRE_ROUTING:
				handleNetfilterPreRoutingEvent(eventData, isFirst, dstIp, dstPort, srcIp, srcPort, protocol, 
						netNsInum, id, eventTime, eventId);
	    		break;
			default:
				log(Level.WARNING, "Unexpected hook '"+hookName+"' instead of: " + 
						Arrays.asList(AuditEventReader.NF_HOOK_VALUE_LOCAL_OUT, AuditEventReader.NF_HOOK_VALUE_LOCAL_IN,
								AuditEventReader.NF_HOOK_VALUE_POST_ROUTING, AuditEventReader.NF_HOOK_VALUE_PRE_ROUTING), 
						null, time, eventId);
	    		return;
		}
	}
	
	private synchronized final void handleNetfilterLocalOutEvent(final Map<String, String> eventData, 
			final boolean isFirst, final String localIp, 
			final String localPort, final String remoteIp, final String remotePort, final String protocol,
			final String netNs, final String skbId, final long eventTime, final String eventId) throws Exception{
		if(isFirst){
			sm_localOutEventInsertLocalOut(eventTime, eventId, netNs, protocol, remoteIp, remotePort, localIp, localPort, 
					skbId, isFirst, false);
		}else{
			final List<Map<String, Object>> result0 = sm_localOutEventFindLocalOut(skbId, true, protocol);
			if(result0.size() == 0){
				debug("Missing first entry in "+sqlGetLocalOutTableName()+" for: " + eventData);
			}else{
				final Map<String, Object> row0 = result0.get(0);
				if(result0.size() > 1){
					debug("Expected only one first entry match in "+sqlGetLocalOutTableName()+" but found '"+result0.size()+"' for: " 
							+ eventData);
				}
				final List<Map<String, Object>> result1 = 
						sm_localOutEventFindSyscall(false, row0.get(sqlColumnNameNetns), row0.get(sqlColumnNameProtocol), 
								row0.get(sqlColumnNameRemoteIp), row0.get(sqlColumnNameRemotePort), 
								row0.get(sqlColumnNameLocalIp), row0.get(sqlColumnNameLocalPort));
				if(result1.size() == 0){
					sm_localOutEventUpdateLocalOutByLast(eventTime, eventId, remoteIp, remotePort, localIp, localPort, 
							isFirst, sqlGetRowIdForSql(row0));
				}else{
					final Map<String, Object> row1 = result1.get(0);
					if(result1.size() > 1){
						debug("Unexpected multiple entries in " + sqlGetSyscallTableName() + " for: " + eventData);
					}
					if(HelperFunctions.objectsEqual(row0.get(sqlColumnNameRemoteIp), remoteIp)
							&& HelperFunctions.objectsEqual(row0.get(sqlColumnNameRemotePort), remotePort)
							&& HelperFunctions.objectsEqual(row0.get(sqlColumnNameLocalIp), localIp)
							&& HelperFunctions.objectsEqual(row0.get(sqlColumnNameLocalPort), localPort)){
						// Don't draw the edge
					}else{
						final Artifact syscallArtifact = createSyscallNetworkArtifact(row1);
						final Artifact netfilterArtifact = createNetfilterNetworkArtifact(row0.get(sqlColumnNameNetns), 
								protocol, remoteIp, remotePort, localIp, localPort, row1.get(sqlColumnNameEpoch), 
								row1.get(sqlColumnNameVersion));
						putNetfilterArtifact(netfilterArtifact);
						putNetfilterWasDerivedFromEdge(netfilterArtifact, syscallArtifact, eventTime, eventId);
						sqlDeleteRowByRowId(sqlGetSyscallTableName(), row1);
					}
					sm_localOutEventUpdateLocalOutBySyscall(eventTime, eventId, remoteIp, remotePort, localIp, localPort, 
							isFirst, row1.get(sqlColumnNameVersion), row1.get(sqlColumnNameEpoch), true, sqlGetRowIdForSql(row0));
				}
			}
		}
	}
	
	private synchronized final void handleNetfilterPostRoutingEvent(final Map<String, String> eventData, 
			final boolean isFirst, final String localIp, 
			final String localPort, final String remoteIp, final String remotePort, final String protocol,
			final String netNs, final String skbId, final long eventTime, final String eventId) throws Exception{
		if(isFirst){
			sm_postRoutingEventInsertPostRouting(eventTime, eventId, protocol, remoteIp, remotePort, localIp, localPort, skbId, isFirst);
		}else{
			final List<Map<String, Object>> result0 = sm_postRoutingEventFindPostRouting(skbId, true, protocol);
			if(result0.size() == 0){
				debug("Missing first entry in "+sqlGetPostRoutingTableName()+" for: " + eventData);
			}else{
				final Map<String, Object> row0 = result0.get(0);
				if(result0.size() > 1){
					debug("Expected only one first entry match in "+sqlGetPostRoutingTableName()+" but found '"+result0.size()+"' for: " 
							+ eventData);
				}
				final List<Map<String, Object>> result1 = 
						sm_postRoutingEventFindLocalOut(skbId, false, true, protocol, row0.get(sqlColumnNameRemoteIp), 
								row0.get(sqlColumnNameRemotePort), row0.get(sqlColumnNameLocalIp), row0.get(sqlColumnNameLocalPort));
				if(result1.size() == 0){
					sm_postRoutingEventUpdatePostRouting(eventTime, eventId, remoteIp, remotePort, localIp, localPort, false, 
							sqlGetRowIdForSql(row0));
				}else{
					final Map<String, Object> row1 = result1.get(0);
					if(result1.size() > 1){
						debug("Unexpected multiple entries in " + sqlGetLocalOutTableName() + " for: " + eventData);
					}
					if(HelperFunctions.objectsEqual(row0.get(sqlColumnNameRemoteIp), remoteIp)
							&& HelperFunctions.objectsEqual(row0.get(sqlColumnNameRemotePort), remotePort)
							&& HelperFunctions.objectsEqual(row0.get(sqlColumnNameLocalIp), localIp)
							&& HelperFunctions.objectsEqual(row0.get(sqlColumnNameLocalPort), localPort)){
						// Don't draw the edge
					}else{
						final Artifact netfilterArtifactFromLocalOut = createNetfilterNetworkArtifact(row1.get(sqlColumnNameNetns),
								row1.get(sqlColumnNameProtocol), row1.get(sqlColumnNameRemoteIpLast), 
								row1.get(sqlColumnNameRemotePortLast), row1.get(sqlColumnNameLocalIpLast), 
								row1.get(sqlColumnNameLocalPortLast), row1.get(sqlColumnNameEpoch), row1.get(sqlColumnNameVersion));
						final Artifact netfilterArtifactFromPostRouting = createNetfilterNetworkArtifact(row1.get(sqlColumnNameNetns), 
								protocol, remoteIp, remotePort, localIp, localPort, row1.get(sqlColumnNameEpoch), 
								row1.get(sqlColumnNameVersion));
						putNetfilterArtifact(netfilterArtifactFromPostRouting);
						putNetfilterWasDerivedFromEdge(netfilterArtifactFromPostRouting, netfilterArtifactFromLocalOut, eventTime, eventId);
						sqlDeleteRowByRowId(sqlGetPostRoutingTableName(), row0);
						sqlDeleteRowByRowId(sqlGetLocalOutTableName(), row1);
					}
				}
			}
		}
	}
	
	private synchronized final void handleNetfilterPreRoutingEvent(final Map<String, String> eventData, 
			final boolean isFirst, final String localIp, 
			final String localPort, final String remoteIp, final String remotePort, final String protocol,
			final String netNs, final String skbId, final long eventTime, final String eventId) throws Exception{
		if(isFirst){
			sm_preRoutingEventInsertPreRouting(eventTime, eventId, protocol, remoteIp, remotePort, localIp, localPort, skbId, isFirst);
		}else{
			final List<Map<String, Object>> result0 = sm_preRoutingEventFindPreRoutingBySkb(skbId, true, protocol);
			if(result0.size() == 0){
				debug("Missing first entry in "+sqlGetPreRoutingTableName()+" for: " + eventData);
			}else{
				final Map<String, Object> row0 = result0.get(0);
				if(result0.size() > 1){
					debug("Unexpected multiple entries in " + sqlGetPreRoutingTableName() + " for: " + eventData);
				}
				if(HelperFunctions.objectsEqual(row0.get(sqlColumnNameRemoteIp), remoteIp)
						&& HelperFunctions.objectsEqual(row0.get(sqlColumnNameRemotePort), remotePort)
						&& HelperFunctions.objectsEqual(row0.get(sqlColumnNameLocalIp), localIp)
						&& HelperFunctions.objectsEqual(row0.get(sqlColumnNameLocalPort), localPort)){
					sqlDeleteRowByRowId(sqlGetPreRoutingTableName(), row0);
				}else{
					sm_preRoutingEventUpdatePreRouting(eventTime, eventId, remoteIp, remotePort, localIp, localPort, 
							false, sqlGetRowIdForSql(row0));
				}
			}
		}
	}
	
	private synchronized final void handleNetfilterLocalInEvent(final Map<String, String> eventData, 
			final boolean isFirst, final String localIp, 
			final String localPort, final String remoteIp, final String remotePort, final String protocol,
			final String netNs, final String skbId, final long eventTime, final String eventId) throws Exception{
		if(isFirst){
			sm_localInEventInsertLocalIn(eventTime, eventId, netNs, protocol, remoteIp, remotePort, localIp, localPort, skbId, isFirst);
		}else{
			final List<Map<String, Object>> result0 = sm_localInEventFindLocalIn(skbId, true, protocol);
			if(result0.size() == 0){
				debug("Missing first entry in "+sqlGetLocalInTableName()+" for: " + eventData);
			}else{
				final Map<String, Object> row0 = result0.get(0);
				if(result0.size() > 1){
					debug("Unexpected multiple entries in " + sqlGetLocalInTableName() + " for: " + eventData);
				}
				final List<Map<String, Object>> result1 = 
						sm_localInEventFindSyscall(true, netNs, protocol, remoteIp, remotePort, localIp, localPort);
				if(result1.size() == 0){
					sm_localInEventUpdateLocalIn(eventTime, eventId, remoteIp, remotePort, localIp, localPort, false, 
							netNs, sqlGetRowIdForSql(row0));
				}else{
					final Map<String, Object> row1 = result1.get(0);
					if(result1.size() > 1){
						debug("Unexpected multiple entries in " + sqlGetSyscallTableName() + " for: " + eventData);
					}
					final boolean areFirstAndLastAddressSame;
					if(HelperFunctions.objectsEqual(row0.get(sqlColumnNameRemoteIp), remoteIp)
							&& HelperFunctions.objectsEqual(row0.get(sqlColumnNameRemotePort), remotePort)
							&& HelperFunctions.objectsEqual(row0.get(sqlColumnNameLocalIp), localIp)
							&& HelperFunctions.objectsEqual(row0.get(sqlColumnNameLocalPort), localPort)){
						// Don't draw an edge
						areFirstAndLastAddressSame = true;
					}else{
						final Artifact syscallArtifact = createSyscallNetworkArtifact(row1);
						final Artifact netfilterArtifact = createNetfilterNetworkArtifact(netNs, protocol, 
								row0.get(sqlColumnNameRemoteIp), row0.get(sqlColumnNameRemotePort), 
								row0.get(sqlColumnNameLocalIp), row0.get(sqlColumnNameLocalPort), 
								row1.get(sqlColumnNameEpoch), row1.get(sqlColumnNameVersion));
						putNetfilterArtifact(netfilterArtifact);
						putNetfilterWasDerivedFromEdge(syscallArtifact, netfilterArtifact, eventTime, eventId);
						sqlDeleteRowByRowId(sqlGetSyscallTableName(), row1);
						areFirstAndLastAddressSame = false;
					}
					final List<Map<String, Object>> result2 = sm_localInEventFindPreRouting(skbId, false, protocol);
					if(result2.size() == 0){
						// Nothing to do
					}else{
						final Map<String, Object> row2 = result2.get(0);
						if(result2.size() > 1){
							debug("Unexpected multiple entries in " + sqlGetPreRoutingTableName() + " for: " + eventData);
						}
						final Artifact sourceArtifact = areFirstAndLastAddressSame ? createSyscallNetworkArtifact(row1)
								: createNetfilterNetworkArtifact(netNs, protocol, 
										row0.get(sqlColumnNameRemoteIp), row0.get(sqlColumnNameRemotePort), 
										row0.get(sqlColumnNameLocalIp), row0.get(sqlColumnNameLocalPort), 
										row1.get(sqlColumnNameEpoch), row1.get(sqlColumnNameVersion));
						final Artifact netfilterArtifact1 = createNetfilterNetworkArtifact(
								sourceArtifact.getAnnotation(OPMConstants.PROCESS_NET_NAMESPACE), 
								row2.get(sqlColumnNameProtocol), row2.get(sqlColumnNameRemoteIp), 
								row2.get(sqlColumnNameRemotePort), row2.get(sqlColumnNameLocalIp), 
								row2.get(sqlColumnNameLocalPort), sourceArtifact.getAnnotation(OPMConstants.ARTIFACT_EPOCH), 
								sourceArtifact.getAnnotation(OPMConstants.ARTIFACT_VERSION));
						putNetfilterArtifact(netfilterArtifact1);
						putNetfilterWasDerivedFromEdge(sourceArtifact, netfilterArtifact1, row2);
						sqlDeleteRowByRowId(sqlGetPreRoutingTableName(), row2);
					}
					sqlDeleteRowByRowId(sqlGetLocalInTableName(), row0);
				}
			}
		}
	}

	//////////////////////////////////////////////////////////////////////////////////
	// ***************** SQL **********************
	//////////////////////////////////////////////////////////////////////////////////
	
	private static final String 
		sqlKeywordAutoIncrement = "auto_increment",
		sqlDataTypeLong = "long",
		sqlDataTypeVarChar = "varchar",
		sqlDataTypeBoolean = "boolean";

	private static final String
		sqlColumnNameRowId = "ROW_ID",
		sqlColumnNameEventTime = "EVENT_TIME",
		sqlColumnNameEventId = "EVENT_ID",
		sqlColumnNameVersion = "VERSION",
		sqlColumnNameEpoch = "EPOCH",
		sqlColumnNameNetns = "NETNS",
		sqlColumnNameProtocol = "PROTOCOL",
		sqlColumnNameRemoteIp = "REMOTE_IP",
		sqlColumnNameRemotePort = "REMOTE_PORT",
		sqlColumnNameLocalIp = "LOCAL_IP",
		sqlColumnNameLocalPort = "LOCAL_PORT",
		sqlColumnNameRemoteIpLast = "REMOTE_IP_LAST",
		sqlColumnNameRemotePortLast = "REMOTE_PORT_LAST",
		sqlColumnNameLocalIpLast = "LOCAL_IP_LAST",
		sqlColumnNameLocalPortLast = "LOCAL_PORT_LAST",
		sqlColumnNameSkbId = "SKB_ID",
		sqlColumnNameIsFirst = "IS_FIRST",
		sqlColumnNameIsIngress = "IS_INGRESS",
		sqlColumnNameUpdatedBySyscall = "UPDATED_BY_SYSCALL";
	
	private final String sqlGetRowIdColumnDefinition(){ return sqlColumnNameRowId + " " + sqlDataTypeLong + " " + sqlKeywordAutoIncrement; }
	private final String sqlGetEventTimeColumnDefinition(){ return sqlColumnNameEventTime + " " + sqlDataTypeLong; }
	private final String __sqlGetVarcharColumnDefinition(String columnName){ return columnName + " " + sqlDataTypeVarChar; }
	private final String sqlGetEventIdColumnDefinition(){ return __sqlGetVarcharColumnDefinition(sqlColumnNameEventId); }
	private final String sqlGetVersionColumnDefinition(){ return __sqlGetVarcharColumnDefinition(sqlColumnNameVersion); }
	private final String sqlGetEpochColumnDefinition(){ return __sqlGetVarcharColumnDefinition(sqlColumnNameEpoch); }
	private final String sqlGetNetnsColumnDefinition(){ return __sqlGetVarcharColumnDefinition(sqlColumnNameNetns); }
	private final String sqlGetProtocolColumnDefinition(){ return __sqlGetVarcharColumnDefinition(sqlColumnNameProtocol); }
	private final String sqlGetRemoteIpColumnDefinition(){ return __sqlGetVarcharColumnDefinition(sqlColumnNameRemoteIp); }
	private final String sqlGetRemotePortColumnDefinition(){ return __sqlGetVarcharColumnDefinition(sqlColumnNameRemotePort); }
	private final String sqlGetLocalIpColumnDefinition(){ return __sqlGetVarcharColumnDefinition(sqlColumnNameLocalIp); }
	private final String sqlGetLocalPortColumnDefinition(){ return __sqlGetVarcharColumnDefinition(sqlColumnNameLocalPort); }
	private final String sqlGetRemoteIpLastColumnDefinition(){ return __sqlGetVarcharColumnDefinition(sqlColumnNameRemoteIpLast); }
	private final String sqlGetRemotePortLastColumnDefinition(){ return __sqlGetVarcharColumnDefinition(sqlColumnNameRemotePortLast); }
	private final String sqlGetLocalIpLastColumnDefinition(){ return __sqlGetVarcharColumnDefinition(sqlColumnNameLocalIpLast); }
	private final String sqlGetLocalPortLastColumnDefinition(){ return __sqlGetVarcharColumnDefinition(sqlColumnNameLocalPortLast); }
	private final String sqlGetSkbIdColumnDefinition(){ return __sqlGetVarcharColumnDefinition(sqlColumnNameSkbId); }
	private final String sqlGetIsFirstColumnDefinition(){ return sqlColumnNameIsFirst + " " + sqlDataTypeBoolean; }
	private final String sqlGetIsIngressColumnDefinition(){ return sqlColumnNameIsIngress + " " + sqlDataTypeBoolean; }
	private final String sqlGetUpdatedBySyscallColumnDefinition(){ return sqlColumnNameUpdatedBySyscall + " " + sqlDataTypeBoolean; }
	
	private final String sqlGetSyscallTableName(){ return "SPADE_SYSCALL"; }
	private final String sqlGetLocalOutTableName(){ return "SPADE_LOCAL_OUT"; }
	private final String sqlGetPostRoutingTableName(){ return "SPADE_POST_ROUTING"; }
	private final String sqlGetLocalInTableName(){ return "SPADE_LOCAL_IN"; }
	private final String sqlGetPreRoutingTableName(){ return "SPADE_PRE_ROUTING"; }
	
	//////////////////////////////////////////////////////////////////////////////////
	// ***************** CREATE **********************
	//////////////////////////////////////////////////////////////////////////////////
	
	private final void sqlCreateSyscallTable() throws Exception{
		try(Statement statement = connection.createStatement()){
			statement.execute(
					String.format(
								"create table %s ("
								+ " %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s "
								+ ")", 
								sqlGetSyscallTableName(),
								sqlGetRowIdColumnDefinition(), sqlGetEventIdColumnDefinition(),
								sqlGetEventTimeColumnDefinition(), sqlGetVersionColumnDefinition(),
								sqlGetEpochColumnDefinition(), sqlGetNetnsColumnDefinition(),
								sqlGetProtocolColumnDefinition(), sqlGetRemoteIpColumnDefinition(),
								sqlGetRemotePortColumnDefinition(), sqlGetLocalIpColumnDefinition(),
								sqlGetLocalPortColumnDefinition(), sqlGetIsIngressColumnDefinition()
								)
							);
		}catch(Exception e){
			throw e;
		}
	}

	private final void sqlCreateLocalOutTable() throws Exception{
		try(Statement statement = connection.createStatement()){
			statement.execute(
					String.format(
								"create table %s ("
								+ " %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s "
								+ ")", 
								sqlGetLocalOutTableName(),
								sqlGetRowIdColumnDefinition(), sqlGetEventIdColumnDefinition(),
								sqlGetEventTimeColumnDefinition(), sqlGetVersionColumnDefinition(),
								sqlGetEpochColumnDefinition(), sqlGetNetnsColumnDefinition(),
								sqlGetProtocolColumnDefinition(), sqlGetRemoteIpColumnDefinition(),
								sqlGetRemotePortColumnDefinition(), sqlGetLocalIpColumnDefinition(),
								sqlGetLocalPortColumnDefinition(), sqlGetRemoteIpLastColumnDefinition(),
								sqlGetRemotePortLastColumnDefinition(), sqlGetLocalIpLastColumnDefinition(),
								sqlGetLocalPortLastColumnDefinition(), sqlGetSkbIdColumnDefinition(),
								sqlGetIsFirstColumnDefinition(), sqlGetUpdatedBySyscallColumnDefinition()
								)
							);
		}catch(Exception e){
			throw e;
		}
	}
	
	private final void sqlCreatePostRoutingTable() throws Exception{
		try(Statement statement = connection.createStatement()){
			statement.execute(
					String.format(
								"create table %s ("
								+ " %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s "
								+ ")", 
								sqlGetPostRoutingTableName(),
								sqlGetRowIdColumnDefinition(), sqlGetEventIdColumnDefinition(),
								sqlGetEventTimeColumnDefinition(), sqlGetProtocolColumnDefinition(), 
								sqlGetRemoteIpColumnDefinition(), sqlGetRemotePortColumnDefinition(), 
								sqlGetLocalIpColumnDefinition(), sqlGetLocalPortColumnDefinition(), 
								sqlGetRemoteIpLastColumnDefinition(), sqlGetRemotePortLastColumnDefinition(), 
								sqlGetLocalIpLastColumnDefinition(), sqlGetLocalPortLastColumnDefinition(), 
								sqlGetSkbIdColumnDefinition(), sqlGetIsFirstColumnDefinition()
								)
							);
		}catch(Exception e){
			throw e;
		}
	}
	
	private final void sqlCreateLocalInTable() throws Exception{
		try(Statement statement = connection.createStatement()){
			statement.execute(
					String.format(
								"create table %s ("
								+ " %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s "
								+ ")", 
								sqlGetLocalInTableName(),
								sqlGetRowIdColumnDefinition(), sqlGetEventIdColumnDefinition(),
								sqlGetEventTimeColumnDefinition(), sqlGetNetnsColumnDefinition(),
								sqlGetProtocolColumnDefinition(), sqlGetRemoteIpColumnDefinition(),
								sqlGetRemotePortColumnDefinition(), sqlGetLocalIpColumnDefinition(),
								sqlGetLocalPortColumnDefinition(), sqlGetRemoteIpLastColumnDefinition(),
								sqlGetRemotePortLastColumnDefinition(), sqlGetLocalIpLastColumnDefinition(),
								sqlGetLocalPortLastColumnDefinition(), sqlGetSkbIdColumnDefinition(),
								sqlGetIsFirstColumnDefinition()
								)
							);
		}catch(Exception e){
			throw e;
		}
	}
	
	private final void sqlCreatePreRoutingTable() throws Exception{
		try(Statement statement = connection.createStatement()){
			statement.execute(
					String.format(
								"create table %s ("
								+ " %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s "
								+ ")", 
								sqlGetPreRoutingTableName(),
								sqlGetRowIdColumnDefinition(), sqlGetEventIdColumnDefinition(),
								sqlGetEventTimeColumnDefinition(), sqlGetProtocolColumnDefinition(), 
								sqlGetRemoteIpColumnDefinition(), sqlGetRemotePortColumnDefinition(), 
								sqlGetLocalIpColumnDefinition(), sqlGetLocalPortColumnDefinition(), 
								sqlGetRemoteIpLastColumnDefinition(), sqlGetRemotePortLastColumnDefinition(), 
								sqlGetLocalIpLastColumnDefinition(), sqlGetLocalPortLastColumnDefinition(), 
								sqlGetSkbIdColumnDefinition(), sqlGetIsFirstColumnDefinition()
								)
							);
		}catch(Exception e){
			throw e;
		}
	}
	
	private final List<Map<String, Object>> sqlMapifyResultSet(final ResultSet resultSet) throws Exception{
		final List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
		final ResultSetMetaData resultSetMetadata = resultSet.getMetaData();
		final int columnCount = resultSetMetadata.getColumnCount();
		final String[] columnNames = new String[columnCount];
		for(int a = 0; a < columnCount; a++){
			columnNames[a] = resultSetMetadata.getColumnLabel(a+1).toUpperCase();
		}
		if(resultSet.next()){
			final Map<String, Object> map = new HashMap<String, Object>();
			for(String columnName : columnNames){
				map.put(columnName, resultSet.getObject(columnName));
			}
			rows.add(map);
		}
		return rows;
	}
	
	private final List<Map<String, Object>> sqlGetResult(PreparedStatement statement) throws Exception{
		ResultSet resultSet = statement.executeQuery();
		List<Map<String, Object>> result = sqlMapifyResultSet(resultSet);
		resultSet.close();
		return result;
	}
	
	private final void sqlInsert(String tableName, long eventTime, PreparedStatement statement) throws Exception{
		long currentCount = perTableCurrentCounts.get(tableName);
		final long maxCount = perTableMaxCounts.get(tableName);
		if(currentCount >= maxCount){
			final int expiredRows = sm_deleteExpiredRows(tableName, eventTime - perTableTtlMillis.get(tableName));
			
			if(expiredRows > 0){
				currentCount -= expiredRows;
				debug("Cleanup(expired): Discarded " + expiredRows + " entries from " + tableName);
				incrementStats("expired(total)", expiredRows);
				incrementStats("expired("+tableName+")", expiredRows);
			}else{
				final int deletedRows = sm_deleteOldestRows(tableName);
				//deleteStatement1.close();
				currentCount -= deletedRows;
				incrementStats("evicted(total)", deletedRows);
				incrementStats("evicted("+tableName+")", deletedRows);
				debug("Cleanup(forced): Discarded " + deletedRows + " entries from " + tableName);
			}
			perTableCurrentCounts.put(tableName, currentCount);
		}
		
		int rowsInserted = statement.executeUpdate();
		perTableCurrentCounts.put(tableName, currentCount + rowsInserted);
		incrementStats("inserted(total)", rowsInserted);
		incrementStats("inserted("+tableName+")", rowsInserted);
	}

	private final void sqlUpdate(String tableName, PreparedStatement statement) throws Exception{
		statement.execute();
	}
	
	private final void sqlDeleteRowByRowId(String tableName, Map<String, Object> row) throws Exception{
		final int deletedRows = sm_deleteRowById(tableName, sqlGetRowIdForSql(row));
		long currentCount = perTableCurrentCounts.get(tableName);
		currentCount -= deletedRows;
		perTableCurrentCounts.put(tableName, currentCount);
		incrementStats("deleted(total)", deletedRows);
		incrementStats("deleted("+tableName+")", deletedRows);
	}
	
	private final long sqlGetRowIdForSql(Map<String, Object> row){
		return (long)row.get(sqlColumnNameRowId);
	}
	
	private final long sqlGetEventTime(Map<String, Object> row){
		return (long)row.get(sqlColumnNameEventTime);
	}

	///////////////////////
}
