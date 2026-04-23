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
package spade.reporter.audit.linux.source.audit.event.record.type.ubsi;

import java.util.ArrayList;
import java.util.List;

import spade.reporter.audit.linux.source.audit.event.record.MalformedRecordException;
import spade.reporter.audit.linux.source.audit.event.record.helper.StringHelper;

/**
 * Holds the fields of a UBSI unit block.
 *
 * Example unit block: pid=701 thread_time=1601572509.571 unitid=901 iteration=0 time=1601572509.571 count=0
 */
public final class Unit{

	/** Process ID of the unit's thread. */
	private final String pid;
	/** Timestamp at which the thread started. */
	private final String threadStartTime;
	/** Unit identifier. */
	private final String id;
	/** Iteration number of the unit. */
	private final String iteration;
	/** Timestamp of the unit. */
	private final String time;
	/** Count value of the unit. */
	private final String count;

	public Unit(
		final String pid,
		final String threadStartTime,
		final String id,
		final String iteration,
		final String time,
		final String count
	){
		this.pid = pid;
		this.threadStartTime = threadStartTime;
		this.id = id;
		this.iteration = iteration;
		this.time = time;
		this.count = count;
	}

	/**
	 * Parse a unit block from a raw audit record string.
	 *
	 * Expected format: unitKey=(pid=X thread_time=Y unitid=Z iteration=W time=T count=C)
	 *
	 * @param data the full record data string
	 * @param unitKey the unit key name (e.g., "unit" or "dep")
	 * @return the parsed Unit
	 * @throws MalformedRecordException if required fields are missing
	 */
	public static Unit parse(final String data, final String unitKey) throws MalformedRecordException{
		final String block = StringHelper.substringBetween(data, unitKey + "=(", ")");
		if(block == null){
			throw new MalformedRecordException(
				"Record doesn't contain the unit in the format '" + unitKey + "=(<key-value-pairs>)'", data);
		}

		final List<String> missing = new ArrayList<>();
		final String pid = StringHelper.substringBetween(block, "pid=", " ");
		if(pid == null){ missing.add("pid"); }
		final String threadTime = StringHelper.substringBetween(block, " thread_time=", " ");
		if(threadTime == null){ missing.add("thread_time"); }
		final String unitId = StringHelper.substringBetween(block, " unitid=", " ");
		if(unitId == null){ missing.add("unitid"); }
		final String iteration = StringHelper.substringBetween(block, " iteration=", " ");
		if(iteration == null){ missing.add("iteration"); }
		final String time = StringHelper.substringBetween(block, " time=", " ");
		if(time == null){ missing.add("time"); }
		final String count = StringHelper.substringAfter(block, " count=");
		if(count == null){ missing.add("count"); }

		if(!missing.isEmpty()){
			throw new MalformedRecordException(
				"Record doesn't contain the unit in the format '"
					+ unitKey
					+ "=(pid=<int> thread_time=<float> unitid=<int> iteration=<int> time=<float> count=<int>)'."
					+ " Missing fields: " + missing,
				data);
		}

		return new Unit(pid, threadTime, unitId, iteration, time, count);
	}

	public String getPid(){
		return pid;
	}

	public String getThreadStartTime(){
		return threadStartTime;
	}

	public String getId(){
		return id;
	}

	public String getIteration(){
		return iteration;
	}

	public String getTime(){
		return time;
	}

	public String getCount(){
		return count;
	}
}
