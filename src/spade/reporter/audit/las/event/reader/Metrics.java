/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International

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
package spade.reporter.audit.las.event.reader;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tracks and reports reading performance metrics.
 *
 * Reports records read per second at configurable intervals.
 * Only active when enabled via MetricsConfig.
 */
public class Metrics{

	private final Logger logger = Logger.getLogger(Metrics.class.getName());

	private final MetricsConfig config;

	private long startTime;
	private long lastReportedTime;
	private long recordCount;
	private long lastReportedRecordCount;

	public Metrics(final MetricsConfig config){
		this.config = config;
	}

	/**
	 * Initialize timing. Called once when reading starts.
	 */
	public void start(){
		if(config.isEnabled()){
			startTime = System.currentTimeMillis();
			lastReportedTime = startTime;
			recordCount = 0;
			lastReportedRecordCount = 0;
		}
	}

	/**
	 * Increment record count. Called per raw line read.
	 */
	public void recordRead(){
		if(config.isEnabled()){
			recordCount++;
		}
	}

	/**
	 * Check if it's time to report, and if so, log stats.
	 */
	public void checkAndReport(){
		if(!config.isEnabled()){
			return;
		}
		final long currentTime = System.currentTimeMillis();
		if((currentTime - lastReportedTime) >= config.getReportEveryMs()){
			printStats();
			lastReportedTime = currentTime;
			lastReportedRecordCount = recordCount;
		}
	}

	/**
	 * Force print final stats. Called on close.
	 */
	public void printFinalStats(){
		if(config.isEnabled()){
			printStats();
		}
	}

	private void printStats(){
		final long currentTime = System.currentTimeMillis();
		final float overallTimeSec = (float)(currentTime - startTime) / 1000;
		final float intervalTimeSec = (float)(currentTime - lastReportedTime) / 1000;
		if(overallTimeSec > 0 && intervalTimeSec > 0){
			final float overallRate = (float)recordCount / overallTimeSec;
			final float intervalRate = (float)(recordCount - lastReportedRecordCount) / intervalTimeSec;
			logger.log(Level.INFO,
					"Overall rate: {0} records/sec in {1} seconds. Interval rate: {2} records/sec in {3} seconds.",
					new Object[]{overallRate, overallTimeSec, intervalRate, intervalTimeSec});
		}
	}

	public long getRecordCount(){
		return recordCount;
	}

	public boolean isEnabled(){
		return config.isEnabled();
	}
}
