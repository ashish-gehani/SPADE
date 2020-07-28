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
package spade.core;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.utility.HelperFunctions;
import spade.utility.Result;

/**
 * This is the base class for reporters.
 *
 * @author Dawood Tariq
 */
public abstract class AbstractReporter{

	private Buffer internalBuffer;
	/**
	 * The arguments that a specific reporter instance is initialized with.
	 */
	public String arguments;

	/**
	 * This method is called by the Kernel for configuration purposes.
	 *
	 * @param buffer The buffer to be set for this reporter.
	 */
	public final void setBuffer(Buffer buffer){
		internalBuffer = buffer;
	}

	/**
	 * Returns the buffer associated with this reporter.
	 *
	 * @return The buffer associated with this reporter.
	 */
	public final Buffer getBuffer(){
		return internalBuffer;
	}

	/**
	 * This method is called by the reporters to send vertices to the buffer.
	 *
	 * @param vertex The vertex to be sent to the buffer.
	 * @return True if the buffer accepted the vertex.
	 */
	public final boolean putVertex(AbstractVertex vertex){
		enforceRateLimit();
		return internalBuffer.putVertex(vertex);
	}

	/**
	 * This method is called by the reporters to send edges to the buffer.
	 *
	 * @param edge The edge to be sent to the buffer.
	 * @return True if the buffer accepted the edge.
	 */
	public final boolean putEdge(AbstractEdge edge){
		enforceRateLimit();
		return internalBuffer.putEdge(edge);
	}

	/**
	 * This method is invoked by the kernel when launching a reporter.
	 *
	 * @param arguments The arguments for this reporter.
	 * @return True if the reporter launched successfully.
	 */
	public abstract boolean launch(String arguments);

	/**
	 * This method is invoked by the kernel when shutting down a reporter.
	 *
	 * @return True if the reporter was shut down successfully.
	 */
	public abstract boolean shutdown();

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public final List<String> getWritablePropertyKeys(){
		return Arrays.asList(propertyNameRateLimit, propertyNameSleepWait, propertyNameWorkableFreeMemory);
	}

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	// keep lower case for ease
	private static final String
			// Internal buffer size.
			propertyNameBufferSize = "buffersize",
			// Current incoming rate per second
			propertyNameCurrentRate = "currentrate",
			// If number of elements incoming per second exceeds this value then reporter
			// blocked from adding anything to
			// internal buffer until the rate of incoming elements drop below this value.
			propertyNameRateLimit = "ratelimit",
			// Time in millis to sleep for while waiting for the rate to drop below the
			// limit.
			propertyNameSleepWait = "sleepwait",
			propertyNameWorkableFreeMemory = "blockingbuffermem",
			propertyNameFreeMemory = "freemem",
			propertyNameGetRate = "getrate",
			propertyNamePutRate = "putrate",
			propertyNameGetCount = "getcount",
			propertyNamePutCount = "putcount";

	private final Object ratePropertyLock = new Object();

	// Variable for property 'rateLimit'
	private Integer incomingRateLimitPerSecond = null;

	// Variable for property 'sleepWait'
	private Long sleepWaitMillis = null;

	// 0 or 1 to indicate if this functionality is enabled
	private int isRateLimited = 0;
	private Long startedAtSeconds = null;
	private Long incomingAbsoluteCount = null;

	private final synchronized String validateProperty(final String propertyName) throws Exception{
		if(HelperFunctions.isNullOrEmpty(propertyName)){
			throw new Exception("NULL/Empty property: '" + propertyName + "'");
		}
		final String lowerCasePropertyName = propertyName.toLowerCase();
		switch(lowerCasePropertyName){
			case propertyNameBufferSize:
			case propertyNameCurrentRate:
			case propertyNameRateLimit:
			case propertyNameSleepWait:
			case propertyNameWorkableFreeMemory:
			case propertyNameFreeMemory:
			case propertyNameGetRate:
			case propertyNamePutRate:
			case propertyNameGetCount:
			case propertyNamePutCount:
				return lowerCasePropertyName;
			default: throw new Exception("Unknown property: '" + propertyName + "'");
		}
	}

	public final synchronized Object getProperty(final String propertyName) throws Exception{
		final String validPropertyName = validateProperty(propertyName);

		switch(validPropertyName){
			case propertyNameBufferSize:{
				final Buffer reference = this.internalBuffer;
				if(reference != null){
					return reference.size();
				}else{
					return null;
				}
			}
			case propertyNameCurrentRate:{
				return getCurrentIncomingRatePerSecond();
			}
			case propertyNameRateLimit:{
				synchronized(this.ratePropertyLock){
					return this.incomingRateLimitPerSecond;
				}
			}
			case propertyNameSleepWait:{
				synchronized(this.ratePropertyLock){
					return this.sleepWaitMillis;
				}
			}
			case propertyNameWorkableFreeMemory:{
				final Buffer reference = this.internalBuffer;
				if(reference != null){
					if(reference instanceof BlockingBuffer){
						return String.format("%.3f", (((BlockingBuffer)reference).getFreeWorkableMemoryPercentage())) + "%";
					}else{
						return null;
					}
				}else{
					return null;
				}
			}
			case propertyNameFreeMemory:{
				return String.format("%.3f", HelperFunctions.getFreeMemoryPercentage()) + "%";
			}
			case propertyNameGetRate:{
				final Buffer reference = this.internalBuffer;
				if(reference != null){
					if(reference instanceof BlockingBuffer){
						return String.format("%.3f", (((BlockingBuffer)reference).getGetRate())) + "%";
					}else{
						return null;
					}
				}else{
					return null;
				}
			}
			case propertyNamePutRate:{
				final Buffer reference = this.internalBuffer;
				if(reference != null){
					if(reference instanceof BlockingBuffer){
						return String.format("%.3f", (((BlockingBuffer)reference).getPutRate())) + "%";
					}else{
						return null;
					}
				}else{
					return null;
				}
			}
			case propertyNameGetCount:{
				final Buffer reference = this.internalBuffer;
				if(reference != null){
					if(reference instanceof BlockingBuffer){
						return (((BlockingBuffer)reference).getGetCount());
					}else{
						return null;
					}
				}else{
					return null;
				}
			}
			case propertyNamePutCount:{
				final Buffer reference = this.internalBuffer;
				if(reference != null){
					if(reference instanceof BlockingBuffer){
						return (((BlockingBuffer)reference).getPutCount());
					}else{
						return null;
					}
				}else{
					return null;
				}
			}
			default: break;
		}
		throw new Exception("Unhandled property: '" + propertyName + "'");
	}

	public final synchronized void setProperty(final String propertyName, final String propertyValue) throws Exception{
		final String validPropertyName = validateProperty(propertyName);

		switch(validPropertyName){
			case propertyNameCurrentRate:
			case propertyNameBufferSize:
			case propertyNameFreeMemory:
			case propertyNameGetRate:
			case propertyNamePutRate:
			case propertyNameGetCount:
			case propertyNamePutCount:{
				throw new Exception("Unsettable property: '" + propertyName + "'");
			}
			case propertyNameRateLimit:{
				final Result<Long> result = HelperFunctions.parseLong(propertyValue, 10, 1, Integer.MAX_VALUE);
				if(result.error){
					throw new Exception("Invalid value for property '" + propertyName + "': '" + propertyValue + "'. "
							+ result.toErrorString());
				}else{
					synchronized(this.ratePropertyLock){
						this.incomingRateLimitPerSecond = result.result.intValue();
						this.isRateLimited = 1;
						return;
					}
				}
			}
			case propertyNameSleepWait:{
				final Result<Long> result = HelperFunctions.parseLong(propertyValue, 10, 100, Long.MAX_VALUE);
				if(result.error){
					throw new Exception("Invalid value for property '" + propertyName + "': '" + propertyValue + "'. "
							+ result.toErrorString());
				}else{
					synchronized(this.ratePropertyLock){
						this.sleepWaitMillis = result.result.longValue();
						return;
					}
				}
			}
			case propertyNameWorkableFreeMemory:{
				final Buffer reference = this.internalBuffer;
				if(reference != null){
					if(reference instanceof BlockingBuffer){
						((BlockingBuffer)reference).setFreeWorkableMemoryPercentage(propertyValue);
						return;
					}else{
						return;
					}
				}else{
					return;
				}
			}
			default: break;
		}
		throw new Exception("Unhandled property: '" + propertyName + "'");
	}

	public final synchronized void unsetProperty(final String propertyName) throws Exception{
		final String validPropertyName = validateProperty(propertyName);

		switch(validPropertyName){
			case propertyNameCurrentRate:
			case propertyNameBufferSize:
			case propertyNameWorkableFreeMemory:
			case propertyNameFreeMemory:
			case propertyNameGetRate:
			case propertyNamePutRate:
			case propertyNamePutCount:
			case propertyNameGetCount:{
				throw new Exception("Un-unsettable property: '" + propertyName + "'");
			}
			case propertyNameRateLimit:{
				synchronized(this.ratePropertyLock){
					this.incomingRateLimitPerSecond = null;
					this.isRateLimited = 0;
					return;
				}
			}
			case propertyNameSleepWait:{
				synchronized(this.ratePropertyLock){
					this.sleepWaitMillis = null;
					return;
				}
			}
			default: break;
		}
		throw new Exception("Unhandled property: '" + propertyName + "'");
	}

	/////////////////////////////////////////////////////////////////////////////////////////////////////////////

	private final double getCurrentIncomingRatePerSecond(){
		if(incomingAbsoluteCount != null && startedAtSeconds != null){
			return ((double)incomingAbsoluteCount / (double)(((System.currentTimeMillis()) / 1000) - startedAtSeconds));
		}else{
			return 0.0;
		}
	}

	private final void enforceRateLimit(){
		// NULL only if the first time
		if(this.incomingAbsoluteCount == null){
			this.incomingAbsoluteCount = 0L;
		}
		// NULL only if the first time
		if(this.startedAtSeconds == null){
			this.startedAtSeconds = System.currentTimeMillis() / 1000;
		}

		incomingAbsoluteCount++;

		int copyIsRateLimited;
		synchronized(this.ratePropertyLock){
			copyIsRateLimited = isRateLimited;
		}

		switch(copyIsRateLimited){
			case 1:{
				long copyIncomingRateLimitPerSecond;
				Long copySleepWaitMillis;
				synchronized(this.ratePropertyLock){
					if(incomingRateLimitPerSecond == null){
						return;
					}
					copyIncomingRateLimitPerSecond = incomingRateLimitPerSecond;
					copySleepWaitMillis = sleepWaitMillis;
				}

				long waitStartMillis = 0;
				boolean needToWait = getCurrentIncomingRatePerSecond() > copyIncomingRateLimitPerSecond;
				if(needToWait){
					logger.log(Level.INFO,
							"Max incoming buffer rate limit exceeded: " + incomingRateLimitPerSecond
									+ " elements per second. " + "Current: " + getCurrentIncomingRatePerSecond()
									+ " elements per second");
					waitStartMillis = System.currentTimeMillis();
				}
				while(getCurrentIncomingRatePerSecond() > copyIncomingRateLimitPerSecond){
					// Break out if enforcing disabled
					synchronized(this.ratePropertyLock){
						switch(isRateLimited){
							case 0: return;
						}
						if(incomingRateLimitPerSecond == null){
							return;
						}
						copyIncomingRateLimitPerSecond = incomingRateLimitPerSecond; // In case it has been updated
						copySleepWaitMillis = sleepWaitMillis;
					}
					if(copySleepWaitMillis != null){
						HelperFunctions.sleepSafe(copySleepWaitMillis);
					}else{
						HelperFunctions.sleepSafe(100); // Sleep for 1/10th of a second at least
					}
				}
				if(needToWait){
					logger.log(Level.INFO,
							"Incoming buffer rate limit fell below max in: "
									+ (System.currentTimeMillis() - waitStartMillis) + " millis. " + "Current: "
									+ getCurrentIncomingRatePerSecond() + " elements per second");
				}
			}
			break;
			default: break;
		}
	}
}
