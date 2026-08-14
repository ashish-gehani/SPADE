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
package spade.utility.map.external.cache;

import java.util.logging.Level;
import java.util.logging.Logger;

import spade.utility.profile.Intervaler;
import spade.utility.profile.TimeProfile;

/**
 * Profile of the cache.
 * Tracks time taken by different operations: get, put, contains, remove.
 * Reports the profile at interval according to the reporting argument.
 */
public class CacheProfile{
	
	private static final Logger logger = Logger.getLogger(CacheProfile.class.getName());
	
	private final TimeProfile putProfile, getProfile, containsProfile, removeProfile;
	
	private final Intervaler intervaler;
	
	private final String id;
	
	protected CacheProfile(String id, long reportingInterval){
		this.id = id;
		
		intervaler = new Intervaler(reportingInterval);
		
		putProfile = new TimeProfile();
		getProfile = new TimeProfile();
		containsProfile = new TimeProfile();
		removeProfile = new TimeProfile();
	}
	
	/**
	 * Call before put
	 */
	protected void putStart(){
		printStats();
		putProfile.start();
	}
	
	/**
	 * Call after put
	 */
	protected void putStop(){
		printStats();
		putProfile.stop();
	}
	
	/**
	 * Call before get
	 */
	protected void getStart(){
		printStats();
		getProfile.start();
	}
	
	/**
	 * Call after get
	 */
	protected void getStop(){
		printStats();
		getProfile.stop();
	}

	/**
	 * Call before contains
	 */
	protected void containsStart(){
		printStats();
		containsProfile.start();
	}
	
	/**
	 * Call after contains
	 */
	protected void containsStop(){
		printStats();
		containsProfile.stop();
	}
	
	/**
	 * Call before remove
	 */
	protected void removeStart(){
		printStats();
		removeProfile.start();
	}
	
	/**
	 * Call after remove
	 */
	protected void removeStop(){
		printStats();
		removeProfile.stop();
	}
	
	/**
	 * Forcefully print stats and do any other necessary cleanup
	 */
	protected void stopAll(){
		printStats(true);
	}

	/**
	 * Call print stat and only prints if interval exceeded
	 */
	private void printStats(){
		printStats(false);
	}
	
	/**
	 * Print stats if interval to report exceeded or if force is 'true'
	 * 
	 * @param force print stats no matter what
	 */
	private void printStats(boolean force){
		if(force || intervaler.check()){
			logger.log(Level.INFO, 
					String.format("%s: GET[%s], PUT[%s], CONTAINS[%s], REMOVE[%s]",
							id, getProfile, putProfile, containsProfile, removeProfile)
					);
		}
	}
}
