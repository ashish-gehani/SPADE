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
package spade.core;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.utility.FileUtility;
import spade.utility.HelperFunctions;
import spade.utility.Result;

public class BlockingBuffer extends Buffer{

	public static final String keyWorkableFreeMemoryPercentageForBuffer = "workableFreeMemory";
	private static final String keySleepWaitMillis = "sleepWaitMillis";
	private static final String keyReportingIntervalSeconds = "reportingIntervalSeconds";
	
	private final long sleepWaitMillis;
	private final long reportingIntervalMillis;
	
	private final Object reportLock = new Object();
	private final Object putLock = new Object();
	private final Object getLock = new Object();
	private final long createdAtMillis = System.currentTimeMillis();
	private long putCount = 0;
	private long getCount = 0;
	
	private long lastReportedAtMillis = System.currentTimeMillis();

	private final Logger logger = Logger.getLogger(this.getClass().getName());
	private final String reporterNameForLogging;
	private final Object percentageLock = new Object();
	private double freeWorkableMemoryPercentage;
	
	public BlockingBuffer(final String freeWorkableMemoryPercentageString, final Class<? extends AbstractReporter> reporterClass){
		setFreeWorkableMemoryPercentage(freeWorkableMemoryPercentageString);
		this.reporterNameForLogging = reporterClass.getSimpleName();
		
		final String configFilePath = Settings.getDefaultConfigFilePath(this.getClass());
		
		final Map<String, String> map;
		try{
			map = FileUtility.readConfigFileAsKeyValueMap(configFilePath, "=");
		}catch(Throwable t){
			throw new RuntimeException("Failed to read file '"+configFilePath+"'. " + t.getMessage(), t);
		}
		
		final String sleepWaitMillisString = map.get(keySleepWaitMillis);
		final Result<Long> sleepWaitMillisResult = HelperFunctions.parseLong(sleepWaitMillisString, 10, 10, Long.MAX_VALUE);
		if(sleepWaitMillisResult.error){
			throw new RuntimeException("Invalid value for '"+keySleepWaitMillis+"' in file '"+configFilePath+"'. " 
					+ sleepWaitMillisResult.errorMessage);
		}
		
		final String reportingIntervalSecondsString = map.get(keyReportingIntervalSeconds);
		final Result<Long> reportingIntervalSecondsResult = HelperFunctions.parseLong(reportingIntervalSecondsString, 10, Integer.MIN_VALUE, Integer.MAX_VALUE);
		if(reportingIntervalSecondsResult.error){
			throw new RuntimeException("Invalid value for '"+keyReportingIntervalSeconds+"' in file '"+configFilePath+"'. " 
					+ reportingIntervalSecondsResult.errorMessage);
		}
		
		this.sleepWaitMillis = sleepWaitMillisResult.result;
		this.reportingIntervalMillis = reportingIntervalSecondsResult.result.intValue() * 1000;
		
		log(Level.INFO, String.format("%s=%.3f%%, %s=%s, %s=%s", 
				keyWorkableFreeMemoryPercentageForBuffer, getFreeWorkableMemoryPercentage(),
				keyReportingIntervalSeconds, reportingIntervalSecondsResult.result.intValue() + "("+getReportingEnableDisableString()+")",
				keySleepWaitMillis, this.sleepWaitMillis));
	}
	
	private final String getReportingEnableDisableString(){
		return this.reportingIntervalMillis > 0 ? "enabled" : "disabled";
	}
	
	public final double getFreeWorkableMemoryPercentage(){
		synchronized(percentageLock){
			return freeWorkableMemoryPercentage;
		}
	}
	
	public final void setFreeWorkableMemoryPercentage(final String freeWorkableMemoryPercentageString){
		final Result<Double> freeWorkableMemoryPercentageResult = HelperFunctions.parseDouble(freeWorkableMemoryPercentageString, 0, 100);
		if(freeWorkableMemoryPercentageResult.error){
			throw new RuntimeException(freeWorkableMemoryPercentageResult.errorMessage);
		}
		synchronized(percentageLock){
			this.freeWorkableMemoryPercentage = freeWorkableMemoryPercentageResult.result;
		}
	}
	
	@Override
	public final boolean putVertex(AbstractVertex vertex){
		report();
		final boolean added = super.putVertex(vertex);
		if(added){
			put();
		}
		return added;
	}

	@Override
	public final boolean putEdge(AbstractEdge edge){
		report();
		final boolean added = super.putEdge(edge);
		if(added){
			put();
		}
		return added;
	}

	@Override
	public final Object getBufferElement(){
		report();
		final Object result = super.getBufferElement();
		if(result != null){
			synchronized(getLock){
				getCount++;
			}
		}
		return result;
	}
	
	private final void put(){
		synchronized(putLock){
			putCount++;
		}
		if(isShutdown()){
			return;
		}
		boolean slept = false;
		final long waitStartMillis = System.currentTimeMillis();
		
		final double freeWorkableMemoryPercentageSpecified = getFreeWorkableMemoryPercentage();
		
		if(HelperFunctions.getFreeMemoryPercentage() <= freeWorkableMemoryPercentageSpecified){
			if(reportingIntervalMillis > 0){
				log(Level.INFO, String.format("Blocking until free memory percentage rises above min. (current) %.3f <= (specified) %.3f" 
					, HelperFunctions.getFreeMemoryPercentage(), freeWorkableMemoryPercentageSpecified), null);
			}
		}
		
		while(HelperFunctions.getFreeMemoryPercentage() <= freeWorkableMemoryPercentageSpecified){
			slept = true;
			
			if(isShutdown()){ // If shutdown has been called then break out
				break;
			}
			
			HelperFunctions.sleepSafe(sleepWaitMillis);
		}
		
		if(slept){
			if(reportingIntervalMillis > 0){
				final long waitEndMillis = System.currentTimeMillis() - waitStartMillis;
				log(Level.INFO, "Blocked for " + (waitEndMillis) 
						+ " millis for free memory percentage to rise above min.", null);
			}
		}
	}
	
	public final double getPutRate(){
		synchronized(putLock){
			return getRate(putCount);
		}
	}
	
	public final double getGetRate(){
		synchronized(getLock){
			return getRate(getCount);
		}
	}
	
	public final long getPutCount(){
		synchronized(putLock){
			return putCount;
		}
	}
	
	public final long getGetCount(){
		synchronized(getLock){
			return getCount;
		}
	}
	
	private final synchronized double getRate(final long count){
		return count / ((System.currentTimeMillis() - createdAtMillis) / (1000.0 * 60.0));
	}
	
	private final void log(final Level level, final String msg){
		log(level, msg, null);
	}
	
	private final void log(final Level level, final String msg, final Throwable t){
		logger.log(level, "["+reporterNameForLogging+"] " + msg, t);
	}
	
	private final void report(){
		synchronized(reportLock){
			if(reportingIntervalMillis > 0){
				if((System.currentTimeMillis() - lastReportedAtMillis) > reportingIntervalMillis){
					lastReportedAtMillis = System.currentTimeMillis();
					log(Level.INFO, 
							String.format("Size=%s, Get-count=%s, Put-count=%s, Free-mem=%.3f percent", 
									size(), getGetCount(), getPutCount(), HelperFunctions.getFreeMemoryPercentage())
							);
				}
			}
		}
	}

}
