package spade.filter;

import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.filter.clamprov.AuditEvent;
import spade.filter.clamprov.Buffer;
import spade.filter.clamprov.BufferKey;
import spade.filter.clamprov.ClamProvEvent;
import spade.filter.clamprov.ClamProvLogReader;
import spade.filter.clamprov.ClamProvThread;
import spade.utility.ArgumentFunctions;
import spade.utility.FileUtility;

public class ClamProv extends AbstractFilter{

	private static final Logger logger = Logger.getLogger(ClamProv.class.getName());

	public static final String
		annotationKeyCallSiteId = "call site id";

	private static final String
		argumentKeyWindowMillis = "window",
		argumentKeySleepMillis = "sleep",
		argumentKeyUserDirectories = "userDirectories";

	private long windowMillis;
	private long sleepMillis;

	private Buffer<AbstractEdge> auditBuffer;
	private Buffer<Long> clamProvBuffer;

	private volatile boolean shutdown = false;

	@Override
	public synchronized final boolean initialize(final String arguments){
		try{
			final String configPath = Settings.getDefaultConfigFilePath(this.getClass());
			final Map<String, String> configMap = FileUtility.readConfigFileAsKeyValueMap(configPath, "=");
			this.windowMillis = ArgumentFunctions.mustBeGreaterThanZero(argumentKeyWindowMillis, configMap);
			this.sleepMillis = ArgumentFunctions.mustBeGreaterThanZero(argumentKeySleepMillis, configMap);
			final List<String> userDirectories = ArgumentFunctions.mustParseCommaSeparatedValues(argumentKeyUserDirectories, configMap);

			auditBuffer = new Buffer<AbstractEdge>(this.windowMillis);
			clamProvBuffer = new Buffer<Long>(this.windowMillis);

			if(userDirectories.isEmpty()){
				throw new Exception("No user directories specified using '" + argumentKeyUserDirectories + "'");
			}
			final Set<String> clamProvLogPaths = new HashSet<String>();
			for(final String userDirectory : userDirectories){
				try{
					final String clamProvLogPath = getClamProvLogPath(userDirectory);
					FileUtility.pathMustBeAReadableFile(clamProvLogPath);
					clamProvLogPaths.add(clamProvLogPath);
				}catch(Exception e){
					throw new Exception("Invalid user directory '" + userDirectory + "' in '" + argumentKeyUserDirectories + "'", e);
				}
			}

			for(final String clamProvLogPath : clamProvLogPaths){
				try{
					new ClamProvThread(new ClamProvLogReader(clamProvLogPath), sleepMillis, this).start();
				}catch(Exception e){
					throw new Exception("Failed to start clam-prov thread for '" + clamProvLogPath + "'", e);
				}
			}

			return true;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to initialize", e);

			// So that any remaining threads stop
			shutdown = true;

			return false;
		}
	}

	@Override
	public synchronized final boolean shutdown(){
		shutdown = true;
		if(auditBuffer != null){
			auditBuffer.clear();
		}
		if(clamProvBuffer != null){
			clamProvBuffer.clear();
		}
		return true;
	}

	public final boolean isShutdown(){
		return shutdown;
	}

	private final String getClamProvLogPath(final String userDirectory){
		return Paths.get(userDirectory, ".clam-prov", "audit.log").toString();
	}

	@Override
	public final void putVertex(final AbstractVertex vertex){
		putInNextFilter(vertex);
	}

	@Override
	public final void putEdge(final AbstractEdge edge){
		if(auditBuffer == null || clamProvBuffer == null){
			putInNextFilter(edge);
			return;
		}
		try{
			final AuditEvent event = AuditEvent.create(edge);
			if(event == null){
				putInNextFilter(edge);
				return;
			}else{
				if(!isHandleableSyscall(event.syscallName)){
					putInNextFilter(edge);
					return;
				}else{
					handleAuditEvent(event, edge);
				}
			}
		}catch(Exception e){
			logger.log(Level.WARNING, "Failed to create clam-prov event from audit event", e);
			putInNextFilter(edge);
			return;
		}
	}

	private final void putUpdatedEdge(final AbstractEdge edge, final long callSiteId){
		edge.addAnnotation(annotationKeyCallSiteId, String.valueOf(callSiteId));
		putInNextFilter(edge);
	}

	public final void handleAuditEvent(final AuditEvent event, final AbstractEdge edge){
		if(auditBuffer == null || clamProvBuffer == null){
			return;
		}
		final long eventMillis = event.milliseconds;
		final BufferKey bufferKey = event.bufferKey;
		final Long callSiteId = clamProvBuffer.get(bufferKey, eventMillis);
		if(callSiteId == null){
			auditBuffer.add(bufferKey, eventMillis, edge);
		}else{
			putUpdatedEdge(edge, callSiteId);
		}
	}

	public final void handleClamProvEvent(final ClamProvEvent event){
		if(auditBuffer == null || clamProvBuffer == null){
			return;
		}
		if(!isHandleableSyscall(event)){
			return;
		}
		final BufferKey bufferKey = event.bufferKey;
		final AbstractEdge edge = auditBuffer.get(bufferKey, event.milliseconds);
		if(edge == null){
			clamProvBuffer.add(bufferKey, event.milliseconds, event.callSiteId);
		}else{
			putUpdatedEdge(edge, event.callSiteId);
		}
	}

	private boolean isHandleableSyscall(final String syscallName){
		switch(syscallName){
			case "read":
			case "readv":
			case "pread":
			case "preadv":
			case "recvfrom":
			case "mmap":
			case "write":
			case "writev":
			case "pwrite":
			case "pwritev":
			case "sendto":
				return true;
			default: return false;
		}
	}

	private boolean isHandleableSyscall(final ClamProvEvent event){
		if(isHandleableSyscall(event.functionName)){
			switch(event.functionName){
				case "read":
				case "readv":
				case "pread":
				case "preadv":
				case "recvfrom":
				case "write":
				case "writev":
				case "pwrite":
				case "pwritev":
				case "sendto":
					return event.exit > -1;
				case "mmap":
					// TODO double check
					return event.exit > -1;
				default: return false;
			}
		}else{
			return false;
		}
	}
}
