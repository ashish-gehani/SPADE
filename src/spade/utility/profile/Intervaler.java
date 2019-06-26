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

/**
 * Used to check if the interval has been exceeded.
 * To be used in the same thread instead of creating a new thread.
 */
public class Intervaler{

	// Everything in millis
	public final long intervalTime;
	
	/**
	 * The time when the interval was exceeded last time
	 */
	private long lastIntervalMarker = 0;
	
	/**
	 * @param intervalTime time span of interval
	 */
	public Intervaler(long intervalTime){
		this.intervalTime = intervalTime;
	}
	
	/**
	 * Returns true if the interval has been exceeded. Also updates the time if exceeded.
	 * Otherwise false.
	 * 
	 * @return true/false
	 */
	public boolean check(){
		long current = System.currentTimeMillis();
		if(current - lastIntervalMarker >= intervalTime){
			lastIntervalMarker = current;
			return true;
		}
		return false;
	}
	
}
