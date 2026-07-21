/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2019 SRI International

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
package spade.utility.profile;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * A convenience class for recording how much time was spent doing a task
 */
public class TimeProfile{

	/**
	 * To identify the state of the profile. Default is 'STOPPED'.
	 */
	private enum State{
		STARTED, STOPPED
	}

	private State state = State.STOPPED;
	/**
	 * The time when the profiling started last
	 */
	private long lastStartedTime = 0;
	/**
	 * Total time spent between different start and stop calls
	 */
	private long totalTimeSpent = 0;
	/**
	 * Number of times start and stop calls combination was called
	 */
	private BigInteger startStopCount = BigInteger.ZERO;
	
	/**
	 * @return Number of times start and stop calls combination was called
	 */
	public BigInteger getStartStopCount(){
		return startStopCount;
	}
	
	/**
	 * @return Total time spent between different start and stop calls
	 */
	public long getTotalTimeSpent(){
		return totalTimeSpent;
	}
	
	/**
	 * @return Returns 0 if nothing done yet else (total time spent / number of times)
	 */
	public double getAverageTimeSpent(){
		final long totalTemp = totalTimeSpent;
		final BigInteger countTemp = startStopCount;
		return divide(totalTemp, countTemp);
	}
	
	/**
	 * Convenience function to take care of the '0' case
	 * 
	 * @param a dividend
	 * @param b divisor
	 * @return 0 or divided value
	 */
	private double divide(long a, BigInteger b){
		if(b == BigInteger.ZERO){
			return 0;
		}else{
			return new BigDecimal(String.valueOf(a))
					.divide(new BigDecimal(b.toString()), 3, RoundingMode.HALF_UP)
					.doubleValue();
		}
	}
	
	public String toString(){
		final long totalTemp = totalTimeSpent;
		final BigInteger countTemp = startStopCount;
		String str = String.format(
				"(%.3f/%s)=%.3f", 
				divide(totalTemp, new BigInteger("1000")),
				countTemp,
				divide(totalTemp, countTemp));
		return str;
	}
	
	/**
	 * Resets all values to zero
	 */
	public synchronized void reset(){
		state = State.STOPPED;
		lastStartedTime = totalTimeSpent = 0;
		startStopCount = BigInteger.ZERO;
	}
	
	/**
	 * If called again after start then just overwrites the values set by the last call
	 */
	public synchronized void start(){
		state = State.STARTED;
		lastStartedTime = System.currentTimeMillis();
	}
	
	/**
	 * If not started then this call just stops it without updating anything.
	 * Otherwise increments the start stop counter and adds the time since the last start to the total time.
	 */
	public synchronized void stop(){
		switch(state){
			case STARTED:
			{
				long timeSpent = System.currentTimeMillis() - lastStartedTime;
				totalTimeSpent += timeSpent;
				startStopCount = startStopCount.add(BigInteger.ONE);
				lastStartedTime = 0;
			}
			default:
			{
				state = State.STOPPED;
			}
		}
	}
}
