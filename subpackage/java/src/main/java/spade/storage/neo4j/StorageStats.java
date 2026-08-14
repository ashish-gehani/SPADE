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
package spade.storage.neo4j;

import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StorageStats{

	private final TreeMap<String, ActionTimer> actionTimers = new TreeMap<String, ActionTimer>();
	
	public final StorageStat vertexCount = new StorageStat("Vertices");
	public final StorageStat vertexCacheMiss = new StorageStat("Vertex Cache Miss");
	public final StorageStat vertexCacheHit = new StorageStat("Vertex Cache Hit");
	public final StorageStat vertexDbHit = new StorageStat("Vertex DB Hit");

	public final StorageStat edgeCount = new StorageStat("Edges");
	public final StorageStat edgeCacheMiss = new StorageStat("Edge Cache Miss");
	public final StorageStat edgeCacheHit = new StorageStat("Edge Cache Hit");
	public final StorageStat edgeDbHit = new StorageStat("Edge DB Hit");

	public final StorageStat pendingTasksIncoming = new StorageStat("Pending Tasks Incoming");
	public final StorageStat pendingTasksOutgoing = new StorageStat("Pending Tasks Outgoing");

	private final boolean timeMe;
	private final boolean reportingEnabled;
	private final long reportingStartedAtMillis;
	private final long reportingIntervalMillis;
	private long lastReportedAtMillis;
	
	private long intervalNumber = 0;

	public StorageStats(final boolean reportingEnabled, final int reportingIntervalSeconds,
			final boolean timeMe){
		this.reportingEnabled = reportingEnabled;
		this.reportingIntervalMillis = reportingIntervalSeconds * 1000;
		this.reportingStartedAtMillis = System.currentTimeMillis();
		this.lastReportedAtMillis = System.currentTimeMillis();
		this.timeMe = timeMe;
	}

	public final void print(final Logger logger, final boolean force){
		if(reportingEnabled || force){
			final long elapsedTimeSinceStartMillis = System.currentTimeMillis() - reportingStartedAtMillis;
			final long elapsedTimeSinceIntervalMillis = System.currentTimeMillis() - lastReportedAtMillis;
			if(elapsedTimeSinceIntervalMillis >= reportingIntervalMillis || force){
				intervalNumber++;
				logger.log(Level.INFO, "Storage STATS Start [interval="+intervalNumber+"]");
				
				logger.log(Level.INFO, vertexCount.format(elapsedTimeSinceStartMillis, elapsedTimeSinceIntervalMillis));
				logger.log(Level.INFO,
						vertexCacheMiss.format(elapsedTimeSinceStartMillis, elapsedTimeSinceIntervalMillis));
				logger.log(Level.INFO,
						vertexCacheHit.format(elapsedTimeSinceStartMillis, elapsedTimeSinceIntervalMillis));
				logger.log(Level.INFO, vertexDbHit.format(elapsedTimeSinceStartMillis, elapsedTimeSinceIntervalMillis));

				logger.log(Level.INFO, edgeCount.format(elapsedTimeSinceStartMillis, elapsedTimeSinceIntervalMillis));
				logger.log(Level.INFO,
						edgeCacheMiss.format(elapsedTimeSinceStartMillis, elapsedTimeSinceIntervalMillis));
				logger.log(Level.INFO,
						edgeCacheHit.format(elapsedTimeSinceStartMillis, elapsedTimeSinceIntervalMillis));
				logger.log(Level.INFO, edgeDbHit.format(elapsedTimeSinceStartMillis, elapsedTimeSinceIntervalMillis));

				logger.log(Level.INFO,
						pendingTasksIncoming.format(elapsedTimeSinceStartMillis, elapsedTimeSinceIntervalMillis));
				logger.log(Level.INFO,
						pendingTasksOutgoing.format(elapsedTimeSinceStartMillis, elapsedTimeSinceIntervalMillis));

				logger.log(Level.INFO,
						String.format("JVM Heap Size In Use: %.3f GB",
								((double)(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()))
										/ (1024.0 * 1024.0 * 1024.0)));

				logger.log(Level.INFO, "Storage STATS End [interval="+intervalNumber+"]");

				if(timeMe){
					for(Map.Entry<String, ActionTimer> entry : actionTimers.entrySet()){
						final String key = entry.getKey();
						final ActionTimer value = entry.getValue();
						if(key != null && value != null){
							if(value.count() > 0){
								logger.log(Level.INFO, "Storage action timer: key=" + entry.getKey() + ", " + entry.getValue());
							}
						}
					}
				}
				
				vertexCount.newInterval();
				vertexCacheMiss.newInterval();
				vertexCacheHit.newInterval();
				vertexDbHit.newInterval();

				edgeCount.newInterval();
				edgeCacheMiss.newInterval();
				edgeCacheHit.newInterval();
				edgeDbHit.newInterval();

				pendingTasksIncoming.newInterval();
				pendingTasksOutgoing.newInterval();
				
				this.lastReportedAtMillis = System.currentTimeMillis();
			}
		}
	}

	public final void startActionTimer(final String key){
		if(timeMe){
			if(key != null){
				ActionTimer timer = actionTimers.get(key);
				if(timer == null){
					timer = new ActionTimer();
					actionTimers.put(key, timer);
				}
				timer.start();
			}
		}
	}

	public final void stopActionTimer(final String key){
		if(timeMe){
			if(key != null){
				ActionTimer timer = actionTimers.get(key);
				if(timer != null){
					timer.stop();
				}
			}
		}
	}
	
	public final static class StorageStat{
		private final String name;
		private long valueSinceEpoch = 0;
		private long valueSinceLastInterval = 0;

		private StorageStat(final String name){
			this.name = name;
		}

		public synchronized final void increment(){
			this.valueSinceLastInterval++;
			this.valueSinceEpoch++;
		}

		synchronized final void newInterval(){
			this.valueSinceLastInterval = 0;
		}

		synchronized final double currentIntervalRatePerMin(long elapsedTimeMillis){
			return getRatePerMin(this.valueSinceLastInterval, elapsedTimeMillis);
		}

		synchronized final double currentOverallRatePerMin(long elapsedTimeMillis){
			return getRatePerMin(this.valueSinceEpoch, elapsedTimeMillis);
		}

		synchronized String format(final long elapsedTimeSinceStartMillis, final long elapsedTimeSinceIntervalMillis){
			return String.format("%s. Rate per minute [Overall=%.3f; Interval=%.3f]. Absolute [Overall=%s, Interval=%s]", this.name,
					currentOverallRatePerMin(elapsedTimeSinceStartMillis),
					currentIntervalRatePerMin(elapsedTimeSinceIntervalMillis),
					valueSinceEpoch, valueSinceLastInterval);
		}

		synchronized final double getRatePerMin(long value, long elapsedTimeMillis){
			final double minutes = ((((double)elapsedTimeMillis) / 1000.0) / 60.0);
			if(minutes <= 0){
				return Double.NaN;
			}else{
				return ((double)value) / minutes;
			}
		}
	}
	
	public final static class ActionTimer{
		private long count = 0;
		private long sum = 0;
		private long lastStart = -1;

		synchronized long count(){
			return count;
		}

		public synchronized void start(){
			lastStart = System.currentTimeMillis();
		}

		public synchronized void stop(){
			if(lastStart > 0){
				sum += (System.currentTimeMillis() - lastStart);
				lastStart = -1;
				count++;
			}
		}

		@Override
		public synchronized String toString(){
			return String.format("count=%s, sum=%.3f seconds, avg=%.6f seconds", count, (((double)sum) / 1000.000),
					(count == 0 ? 0.000 : (((double)sum) / 1000.000) / ((double)count)));
		}
	}
}
