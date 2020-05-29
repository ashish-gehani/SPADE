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

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.Settings;
import spade.edge.opm.WasDerivedFrom;
import spade.reporter.Audit;
import spade.reporter.audit.artifact.NetworkSocketIdentifier;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;
import spade.utility.Result;
import spade.vertex.opm.Artifact;

public class NetfilterHooksManager{

	//////////////////////////////////////
	
	private final static String keyDebug = "debug",
			keyPerHookMaxEntries = "perHookMaxEntries",
			keyEntryTtlMillis = "entryTtlMillis",
			keyMaxNetfilterMappings = "maxNetfilterMappings",
			keyNetfilterMappingsTtlMillis = "netfilterMappingsTtlMillis",
			keyMaxSyscallMappings = "maxSyscallMappings",
			keySyscallMappingsTtlMillis = "syscallMappingsTtlMillis";
	
	private final static Logger logger = Logger.getLogger(NetfilterHooksManager.class.getName());
	private final boolean debug;
	
	private void debugHookEntry(String msg, Exception exception, String time, String eventId,
			String hookName, String id, NetfilterNetworkIdentifier network){
		if(debug){
			debug("[" + msg + "] Hook '"+hookName+"' ("+id+")->("+network+")", exception, time, eventId);
		}
	}
	
	private void debug(String msg, Exception exception, String time, String eventId){
		if(debug){
			log(Level.INFO, "[DEBUG] " + msg, exception, time, eventId);
		}
	}
	
	/** 
	 * @param level Level of the log message
	 * @param msg Message to print
	 * @param exception Exception (if any)
	 * @param time time of the audit event
	 * @param eventId id of the audit event
	 */
	public void log(Level level, String msg, Exception exception, String time, String eventId){
		eventId = eventId == null ? "-1" : eventId;
		String msgPrefix = "[Time:EventID="+time+":"+eventId+"] ";
		if(exception == null){
			logger.log(level, msgPrefix + msg);
		}else{
			logger.log(level, msgPrefix + msg, exception);
		}
	}
	
	//////////////////////////////////////
	
	private final Audit reporter;
	private final boolean namespaces;
	private final NetfilterHookManager localOutManager, localInManager, postRoutingManager, preRoutingManager;
	
	///////////////////////////////////////
	
	private int matchedNetfilterSyscall = 0,
			matchedSyscallNetfilter = 0;
	
	private List<Map<String, String>> networkAnnotationsFromSyscalls = 
			new ArrayList<Map<String, String>>();
	// Host to world view
	private List<SimpleEntry<Map<String, String>, Map<String, String>>> networkAnnotationsFromNetfilter = 
			new ArrayList<SimpleEntry<Map<String, String>, Map<String, String>>>();
	
	private final String[] annotationsToMatch = {
			OPMConstants.ARTIFACT_PROTOCOL,
			OPMConstants.ARTIFACT_REMOTE_ADDRESS,
			OPMConstants.ARTIFACT_REMOTE_PORT,
			OPMConstants.ARTIFACT_LOCAL_PORT,
			OPMConstants.ARTIFACT_LOCAL_ADDRESS, // Going with exact! 0.0.0.0 issue
			OPMConstants.PROCESS_NET_NAMESPACE,
	};
	
	///////////////////////////////////////

	public NetfilterHooksManager(final Audit reporter, final boolean namespaces) throws IllegalArgumentException{
		this.reporter = reporter;
		if(reporter == null){
			throw new IllegalArgumentException("NULL reporter");
		}
		this.namespaces = namespaces;
		
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
		
		final int perHookMaxEntries;
		final long entryTtlMillis;
		final String valuePerHookMaxEntries = configMap.get(keyPerHookMaxEntries);
		final Result<Long> resultPerHookMaxEntries = HelperFunctions.parseLong(valuePerHookMaxEntries, 10, 1, Integer.MAX_VALUE);
		if(resultPerHookMaxEntries.error){
			throw new IllegalArgumentException("Invalid flag value for '"+keyPerHookMaxEntries+". " + resultPerHookMaxEntries.toErrorString());
		}
		perHookMaxEntries = resultPerHookMaxEntries.result.intValue();
		
		final String valueEntryTtlMillis = configMap.get(keyEntryTtlMillis);
		final Result<Long> resultEntryTtlMillis = HelperFunctions.parseLong(valueEntryTtlMillis, 10, 1, Long.MAX_VALUE);
		if(resultEntryTtlMillis.error){
			throw new IllegalArgumentException("Invalid flag value for '"+keyEntryTtlMillis+". " + resultEntryTtlMillis.toErrorString());
		}
		entryTtlMillis = resultEntryTtlMillis.result;
		
		//////////////////////////
		
		final int maxNetfilterMappings;
		final long netfilterMappingsTtlMillis;
		final String valueMaxNetfilterMappings = configMap.get(keyMaxNetfilterMappings);
		final Result<Long> resultMaxNetfilterMappings = HelperFunctions.parseLong(valueMaxNetfilterMappings, 10, 1, Integer.MAX_VALUE);
		if(resultMaxNetfilterMappings.error){
			throw new IllegalArgumentException("Invalid flag value for '"+keyMaxNetfilterMappings+". " + resultMaxNetfilterMappings.toErrorString());
		}
		maxNetfilterMappings = resultMaxNetfilterMappings.result.intValue();
		
		final String valueNetfilterMappingsTtlMillis = configMap.get(keyNetfilterMappingsTtlMillis);
		final Result<Long> resultNetfilterMappingsTtlMillis = HelperFunctions.parseLong(valueNetfilterMappingsTtlMillis, 10, 1, Long.MAX_VALUE);
		if(resultNetfilterMappingsTtlMillis.error){
			throw new IllegalArgumentException("Invalid flag value for '"+keyNetfilterMappingsTtlMillis+". " + resultNetfilterMappingsTtlMillis.toErrorString());
		}
		netfilterMappingsTtlMillis = resultNetfilterMappingsTtlMillis.result;
		
		//////////////////////////

		final int maxSyscallMappings;
		final long syscallMappingsTtlMillis;
		final String valueMaxSyscallMappings = configMap.get(keyMaxSyscallMappings);
		final Result<Long> resultMaxSyscallMappings = HelperFunctions.parseLong(valueMaxSyscallMappings, 10, 1, Integer.MAX_VALUE);
		if(resultMaxSyscallMappings.error){
			throw new IllegalArgumentException("Invalid flag value for '"+keyMaxSyscallMappings+". " + resultMaxSyscallMappings.toErrorString());
		}
		maxSyscallMappings = resultMaxSyscallMappings.result.intValue();
		
		final String valueSyscallMappingsTtlMillis = configMap.get(keySyscallMappingsTtlMillis);
		final Result<Long> resultSyscallMappingsTtlMillis = HelperFunctions.parseLong(valueSyscallMappingsTtlMillis, 10, 1, Long.MAX_VALUE);
		if(resultSyscallMappingsTtlMillis.error){
			throw new IllegalArgumentException("Invalid flag value for '"+keySyscallMappingsTtlMillis+". " + resultSyscallMappingsTtlMillis.toErrorString());
		}
		syscallMappingsTtlMillis = resultSyscallMappingsTtlMillis.result;
		
		//////////////////////////
		
		
		this.localOutManager = new NetfilterHookManager(AuditEventReader.NF_HOOK_VALUE_LOCAL_OUT, perHookMaxEntries, entryTtlMillis);
		this.localInManager = new NetfilterHookManager(AuditEventReader.NF_HOOK_VALUE_LOCAL_IN, perHookMaxEntries, entryTtlMillis);
		this.postRoutingManager = new NetfilterHookManager(AuditEventReader.NF_HOOK_VALUE_POST_ROUTING, perHookMaxEntries, entryTtlMillis);
		this.preRoutingManager = new NetfilterHookManager(AuditEventReader.NF_HOOK_VALUE_PRE_ROUTING, perHookMaxEntries, entryTtlMillis);
		
		logger.log(Level.INFO, 
				"Arguments ["+keyDebug+"="+debug+", "
				+ ""+keyPerHookMaxEntries+"="+perHookMaxEntries+", "
				+ ""+keyEntryTtlMillis+"="+entryTtlMillis
				+ ""+keyMaxNetfilterMappings+"="+maxNetfilterMappings+", "
				+ ""+keyNetfilterMappingsTtlMillis+"="+netfilterMappingsTtlMillis
				+ ""+keyMaxSyscallMappings+"="+maxSyscallMappings+", "
				+ ""+keySyscallMappingsTtlMillis+"="+syscallMappingsTtlMillis
				+"]");
	}
	
	public synchronized void printStats(){
		String netfilterStat = String.format("Unmatched: "
				+ "%d netfilter-syscall, "
				+ "%d syscall-netfilter. "
				+ "Matched: "
				+ "%d netfilter-syscall, "
				+ "%d syscall-netfilter.",
				networkAnnotationsFromNetfilter.size(),
				networkAnnotationsFromSyscalls.size(),
				matchedNetfilterSyscall,
				matchedSyscallNetfilter);
		logger.log(Level.INFO, netfilterStat);
	}
	
	public final synchronized void putWasDerivedFromEdgeFromNetworkArtifacts(final boolean ingress, final Artifact syscallArtifact){
		if(OPMConstants.isNetworkArtifact(syscallArtifact)){
			
			SimpleEntry<Map<String, String>, Map<String, String>> matchedEntry = null;
			for(SimpleEntry<Map<String, String>, Map<String, String>> entry : networkAnnotationsFromNetfilter){
				boolean allAnnotationsMatched = true;
				Map<String, String> hostViewNetfilter = entry.getKey();
				for(String annotationToMatch : annotationsToMatch){
					allAnnotationsMatched = allAnnotationsMatched &&
							HelperFunctions.objectsEqual(hostViewNetfilter.get(annotationToMatch), syscallArtifact.getAnnotation(annotationToMatch));
					if(!allAnnotationsMatched){
						break;
					}
				}
				if(allAnnotationsMatched){
					matchedEntry = entry;
					break;
				}
			}
			if(matchedEntry != null){
				// remove the annotations map from netfilter because we are going to consume that.
	    		// removing that here because the map is going to be updated below.
				networkAnnotationsFromNetfilter.remove(matchedEntry);
				
				Map<String, String> netfilterArtifactAnnotations = matchedEntry.getValue();

	    		String epoch = syscallArtifact.getAnnotation(OPMConstants.ARTIFACT_EPOCH);
	    		String version = syscallArtifact.getAnnotation(OPMConstants.ARTIFACT_VERSION);
	    		String netNsId = syscallArtifact.getAnnotation(OPMConstants.PROCESS_NET_NAMESPACE);
	    		
	    		if(epoch != null){
	    			netfilterArtifactAnnotations.put(OPMConstants.ARTIFACT_EPOCH, epoch);
	    		}
	    		if(version != null){
	    			netfilterArtifactAnnotations.put(OPMConstants.ARTIFACT_VERSION, version);
	    		}
	    		if(namespaces && netNsId != null){
    				netfilterArtifactAnnotations.put(OPMConstants.PROCESS_NET_NAMESPACE, netNsId);
	    		}
	    		
	    		String netfilterTime = netfilterArtifactAnnotations.remove(OPMConstants.EDGE_TIME); //remove
	    		String netfilterEventId = netfilterArtifactAnnotations.remove(OPMConstants.EDGE_EVENT_ID); //remove
				
	    		// Syscall artifact already put
	    		Artifact netfilterArtifact = new Artifact();
	    		netfilterArtifact.addAnnotations(netfilterArtifactAnnotations);
	    		reporter.putVertex(netfilterArtifact);

	    		WasDerivedFrom wdfEdge = null;
	    		if(ingress){
	    			wdfEdge = new WasDerivedFrom(syscallArtifact, netfilterArtifact);
	    		}else{
	    			wdfEdge = new WasDerivedFrom(netfilterArtifact, syscallArtifact);
	    		}
				reporter.putEdge(wdfEdge, reporter.getOperation(SYSCALL.UPDATE), netfilterTime, netfilterEventId, OPMConstants.SOURCE_AUDIT_NETFILTER);
				
				matchedSyscallNetfilter++;
			}else{
				networkAnnotationsFromSyscalls.add(syscallArtifact.getCopyOfAnnotations());
			}
		}
	}
	
	public final synchronized void handleNetfilterHookEvent(final Map<String, String> eventData){
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
    	
    	final String localIp, localPort, remoteIp, remotePort;
    	final String protocol;
    	
    	if(AuditEventReader.NF_HOOK_VALUE_LOCAL_OUT.equals(hookName)
    			|| AuditEventReader.NF_HOOK_VALUE_POST_ROUTING.equals(hookName)){
    		localIp = srcIp;
    		localPort = srcPort;
    		remoteIp = dstIp;
    		remotePort = dstPort;
    	}else if(AuditEventReader.NF_HOOK_VALUE_LOCAL_IN.equals(hookName)
    			|| AuditEventReader.NF_HOOK_VALUE_PRE_ROUTING.equals(hookName)){
    		localIp = dstIp;
    		localPort = dstPort;
    		remoteIp = srcIp;
    		remotePort = srcPort;
    	}else{
    		log(Level.WARNING, "Unexpected hook '"+hookName+"' instead of: " + 
					Arrays.asList(AuditEventReader.NF_HOOK_VALUE_LOCAL_OUT, AuditEventReader.NF_HOOK_VALUE_LOCAL_IN,
							AuditEventReader.NF_HOOK_VALUE_POST_ROUTING, AuditEventReader.NF_HOOK_VALUE_PRE_ROUTING), 
					null, time, eventId);
    		return;
    	}
    	
    	if(isTcp){
    		protocol = Audit.PROTOCOL_NAME_TCP;
    	}else{
    		protocol = Audit.PROTOCOL_NAME_UDP;
    	}
    	
    	final double timeSecondsDouble = Double.parseDouble(time);
    	final long timeMillisLong = (long)(timeSecondsDouble * 1000);
    	
    	final NetfilterNetworkIdentifier newNetworkSocketIdentifier = 
    			new NetfilterNetworkIdentifier(localIp, localPort, remoteIp, remotePort, protocol, netNsInum, timeMillisLong);
    	
    	final NetfilterHookManager hookManager;
		switch(hookName){
			case AuditEventReader.NF_HOOK_VALUE_LOCAL_IN: hookManager = localInManager; break;
			case AuditEventReader.NF_HOOK_VALUE_PRE_ROUTING: hookManager = preRoutingManager; break;
			case AuditEventReader.NF_HOOK_VALUE_LOCAL_OUT: hookManager = localOutManager; break;
			case AuditEventReader.NF_HOOK_VALUE_POST_ROUTING: hookManager = postRoutingManager; break;
			default:
				log(Level.WARNING, "handleNetfilterHookEvent:UNKNOWN-HOOK", null, time, eventId);
				return;
		}
    	
    	if(isFirst){
    		hookManager.handleFirst(time, eventId, id, newNetworkSocketIdentifier);
    		return;
    	}else{ // is last
    		final boolean ingress; // or egress
    		if(AuditEventReader.NF_HOOK_VALUE_LOCAL_IN.equals(hookName) || AuditEventReader.NF_HOOK_VALUE_PRE_ROUTING.equals(hookName)){
    			ingress = true;
    		}else if(AuditEventReader.NF_HOOK_VALUE_LOCAL_OUT.equals(hookName) || AuditEventReader.NF_HOOK_VALUE_POST_ROUTING.equals(hookName)){
    			ingress = false;
    		}else{
    			log(Level.WARNING, "handleNetfilterHookEvent:UNKNOWN-HOOK", null, time, eventId);
    			return;
    		}
    		
    		final NetfilterNetworkIdentifier oldNetworkSocketIdentifier = hookManager.handleLast(time, eventId, id);
    		if(oldNetworkSocketIdentifier == null){
    			log(Level.WARNING, "Missing 'first' entry for hook '"+hookName+"'", null, time, eventId);
    			return;
    		}else{
    			if(!HelperFunctions.objectsEqual(newNetworkSocketIdentifier.getProtocol(), oldNetworkSocketIdentifier.getProtocol())){
    				log(Level.WARNING, "Protocol mismatch in 'first' and 'last' entries for hook '"+hookName+"'."
    						+ "firstNet=" + oldNetworkSocketIdentifier + ", lastNet=" + newNetworkSocketIdentifier, null, time, eventId);
    				return;
    			}
    			final NetfilterNetworkIdentifier networkViewHost;
    			final NetfilterNetworkIdentifier networkViewWorld;
    			if(ingress){
    				networkViewHost = newNetworkSocketIdentifier;
    				networkViewWorld = oldNetworkSocketIdentifier;
    			}else{
    				networkViewHost = oldNetworkSocketIdentifier;
    				networkViewWorld = newNetworkSocketIdentifier;
    			}
    			
    	    	Map<String, String> annotationsHostViewFromNetfilter = networkViewHost.getAnnotationsMap();
    	    	
    	    	Map<String, String> annotationsWorldViewFromNetfilter = networkViewWorld.getAnnotationsMap();
    	    	annotationsWorldViewFromNetfilter.put(OPMConstants.ARTIFACT_SUBTYPE, OPMConstants.SUBTYPE_NETWORK_SOCKET);
    	    	annotationsWorldViewFromNetfilter.put(OPMConstants.SOURCE, OPMConstants.SOURCE_AUDIT_NETFILTER);

    	    	// check if the host view exists in the syscall net list
    	    	Map<String, String> matchedEntry = null;
    	    	for(Map<String, String> entry : networkAnnotationsFromSyscalls){
			boolean allAnnotationsMatched = true;
    	    		for(String annotationToMatch : annotationsToMatch){
				allAnnotationsMatched = allAnnotationsMatched &&
					HelperFunctions.objectsEqual(entry.get(annotationToMatch), annotationsHostViewFromNetfilter.get(annotationToMatch));
				if(!allAnnotationsMatched){
    	    				break;
    	    			}
    	    		}
			if(allAnnotationsMatched){
				matchedEntry = entry;
				break;
			}
    	    	}
    	    	
    	    	if(matchedEntry != null){ // found
    	    		// logic for deduplication deviates from the current one
    	    		// standardize this too and handling of netfilter in syscall functions. TODO
    	    		String epoch = matchedEntry.get(OPMConstants.ARTIFACT_EPOCH);
    	    		String version = matchedEntry.get(OPMConstants.ARTIFACT_VERSION);
    	    		String netNsId = matchedEntry.get(OPMConstants.PROCESS_NET_NAMESPACE);
    	    		
    	    		if(epoch != null){
    	    			annotationsWorldViewFromNetfilter.put(OPMConstants.ARTIFACT_EPOCH, epoch);
    	    		}
    	    		if(version != null){
    	    			annotationsWorldViewFromNetfilter.put(OPMConstants.ARTIFACT_VERSION, version);
    	    		}
    	    		if(netNsId != null){
    	    			annotationsWorldViewFromNetfilter.put(OPMConstants.PROCESS_NET_NAMESPACE, netNsId);
    	    		}
    	    		
    	    		Artifact artifactFromSyscall = new Artifact();
    	    		artifactFromSyscall.addAnnotations(matchedEntry);
    	    		
    	    		Artifact artifactFromNetfilter = new Artifact();
    	    		artifactFromNetfilter.addAnnotations(annotationsWorldViewFromNetfilter);
    	    		reporter.putVertex(artifactFromNetfilter); // add this only. syscall one already added.
    	    		
    	    		WasDerivedFrom wdfEdge = null;
    	    		if(ingress){
    	    			wdfEdge = new WasDerivedFrom(artifactFromSyscall, artifactFromNetfilter);
    	    		}else{
    	    			wdfEdge = new WasDerivedFrom(artifactFromNetfilter, artifactFromSyscall);
    	    		}
    	    		reporter.putEdge(wdfEdge, reporter.getOperation(SYSCALL.UPDATE), time, eventId, OPMConstants.SOURCE_AUDIT_NETFILTER);
    	    		
    	    		// Found a match, and have consumed this. So, remove from the list.
    	    		networkAnnotationsFromSyscalls.remove(matchedEntry);
    	    		
    	    		matchedNetfilterSyscall++;
    	    	}else{
    	    		// Not found
    	    		// Add extra annotations to draw the edge later
    	    		annotationsWorldViewFromNetfilter.put(OPMConstants.EDGE_EVENT_ID, eventId);
    	    		annotationsWorldViewFromNetfilter.put(OPMConstants.EDGE_TIME, time);
    	    		networkAnnotationsFromNetfilter.add(
    	    				new SimpleEntry<Map<String, String>, Map<String, String>>(
    	    						annotationsHostViewFromNetfilter, annotationsWorldViewFromNetfilter));
    	    	}
    		}
    	}
	}
	
	//////////////////////////////////////
	
	/*
	public static void main(String[] args) throws Exception{
		final HashMap<NetfilterHookEntry, NetfilterNetworkIdentifier> idToFirstEntries
			= new HashMap<NetfilterHookEntry, NetfilterNetworkIdentifier>();
		NetfilterHookEntry n0 = new NetfilterHookEntry("id0");
		HelperFunctions.sleepSafe(5);
		NetfilterHookEntry n1 = new NetfilterHookEntry("id1");
		System.out.println(n0.createdAt + ", " + n1.createdAt);
		idToFirstEntries.put(n1, null);
		idToFirstEntries.put(n0, null);
		
		System.out.println(idToFirstEntries.size());
		final TreeSet<NetfilterHookEntry> sortedEntries = new TreeSet<NetfilterHookEntry>(netfilterHookEntrySorter);
		sortedEntries.addAll(idToFirstEntries.keySet());
		System.out.println(sortedEntries);
	}
	*/
	
	private class NetfilterHookManager{
		private final String hookName;
		private final int maxEntries;
		private final long entryTtlMillis;
		
		private final HashMap<NetfilterHookEntry, NetfilterNetworkIdentifier> idToFirstEntries
			= new HashMap<NetfilterHookEntry, NetfilterNetworkIdentifier>();
		
		private NetfilterHookManager(final String hookName, final int maxEntries, final long entryTtlMillis){
			this.hookName = hookName;
			this.maxEntries = maxEntries;
			this.entryTtlMillis = entryTtlMillis;
		}
		
		private synchronized void handleFirst(final String time, final String eventId, 
				final String id, final NetfilterNetworkIdentifier networkSocketIdentifier){
			if(id != null && networkSocketIdentifier != null){
				final NetfilterHookEntry newEntry = new NetfilterHookEntry(id);
				NetfilterNetworkIdentifier oldNetworkSocketIdentifier = null;
				if((oldNetworkSocketIdentifier = idToFirstEntries.remove(newEntry)) != null){ // just overwrite if not null
					debugHookEntry("handleFirst:Overwritten", null, time, eventId, hookName, id, oldNetworkSocketIdentifier);
					idToFirstEntries.put(newEntry, networkSocketIdentifier);
				}else{
					if(idToFirstEntries.size() >= maxEntries){
						final TreeSet<NetfilterHookEntry> sortedEntries = new TreeSet<NetfilterHookEntry>(netfilterHookEntrySorter);
						sortedEntries.addAll(idToFirstEntries.keySet());
						for(NetfilterHookEntry sortedEntry : sortedEntries){
							if(System.currentTimeMillis() - sortedEntry.createdAt >= entryTtlMillis){
								debugHookEntry("handleFirst:Expired", null, time, eventId, 
										hookName, sortedEntry.toString(), idToFirstEntries.remove(sortedEntry));
							}else{
								// Not expired one
								// Break out of the loop because none later would be expired either because sorted by time
								break;
							}
						}
						// If nothing had expired. Force kick out.
						if(idToFirstEntries.size() >= maxEntries){
							final int toRemoveCount = (idToFirstEntries.size() - maxEntries) + 1;
							int index = 0;
							for(NetfilterHookEntry sortedEntry : sortedEntries){
								if(index >= toRemoveCount){
									break;
								}
								debugHookEntry("handleFirst:ForceRemove", null, time, eventId, 
										hookName, sortedEntry.toString(), idToFirstEntries.remove(sortedEntry));
								index++;
							}
						}
					}
					// Must have space by now
					idToFirstEntries.put(newEntry, networkSocketIdentifier);
				}
			}
		}
		
		private synchronized NetfilterNetworkIdentifier handleLast(final String time, final String eventId, final String id){
			if(id != null){
				final NetfilterHookEntry newEntry = new NetfilterHookEntry(id);
				final NetfilterNetworkIdentifier oldNetworkSocketIdentifier = idToFirstEntries.remove(newEntry);
				if(oldNetworkSocketIdentifier == null){
					debugHookEntry("handleLast:NULL", null, time, eventId, 
							hookName, id, oldNetworkSocketIdentifier);
				}
				return oldNetworkSocketIdentifier;
			}
			return null;
		}
	}
	
	/////////////////////////////////////////
	
	private static class NetfilterHookEntry{
		private final long createdAt;
		// equality based on only id!
		private final String id;
		
		private NetfilterHookEntry(final String id){
			this.id = id;
			this.createdAt = System.currentTimeMillis();
		}
		@Override
		public int hashCode(){
			final int prime = 31;
			int result = 1;
			result = prime * result + ((id == null) ? 0 : id.hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj){
			if(this == obj)
				return true;
			if(obj == null)
				return false;
			if(getClass() != obj.getClass())
				return false;
			NetfilterHookEntry other = (NetfilterHookEntry)obj;
			if(id == null){
				if(other.id != null)
					return false;
			}else if(!id.equals(other.id))
				return false;
			return true;
		}
		@Override
		public String toString(){
			return "NetfilterHookEntry [createdAt=" + createdAt + ", id=" + id + "]";
		}
	}
	
	//////////////////////////////////////
	
	private static final Comparator<NetfilterHookEntry> netfilterHookEntrySorter
		= new Comparator<NetfilterHookEntry>(){
			@Override
			public int compare(NetfilterHookEntry o1, NetfilterHookEntry o2){
				if(o1.createdAt > o2.createdAt){
					return 1;
				}else if(o1.createdAt < o2.createdAt){
					return -1;
				}else{
					return o1.id.compareTo(o2.id);
				}
			}
	};
	
	//////////////////////////////////////
	
	public static class NetfilterNetworkIdentifier extends NetworkSocketIdentifier{
		private static final long serialVersionUID = -6799479595760537665L;
		public final long netfilterTimeInMillis;
		public NetfilterNetworkIdentifier(String localHost, String localPort, String remoteHost, String remotePort,
				String protocol, String netNamespaceId, long netfilterTimeInMillis){
			super(localHost, localPort, remoteHost, remotePort, protocol, netNamespaceId);
			this.netfilterTimeInMillis = netfilterTimeInMillis;
		}
		@Override
		public int hashCode(){
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result + (int)(netfilterTimeInMillis ^ (netfilterTimeInMillis >>> 32));
			return result;
		}
		@Override
		public boolean equals(Object obj){
			if(this == obj)
				return true;
			if(!super.equals(obj))
				return false;
			if(getClass() != obj.getClass())
				return false;
			NetfilterNetworkIdentifier other = (NetfilterNetworkIdentifier)obj;
			if(netfilterTimeInMillis != other.netfilterTimeInMillis)
				return false;
			return true;
		}
		@Override
		public String toString(){
			return "NetfilterNetworkIdentifier [netfilterTimeInMillis=" + netfilterTimeInMillis + ", super()="
					+ super.toString() + "]";
		}
	}
	
	//////////////////////////////////////
	
}
