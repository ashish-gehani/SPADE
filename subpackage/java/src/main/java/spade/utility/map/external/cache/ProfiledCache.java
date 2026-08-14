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

import spade.utility.profile.ReportingArgument;

public class ProfiledCache<K, V> implements Cache<K, V>{
		
	private final CacheProfile profile;
	
	private final Cache<K, V> cache;
	
	public ProfiledCache(Cache<K, V> cache, ReportingArgument reportingArgument){
		this.cache = cache;
		
		final String id = reportingArgument.id;
		final long millis = reportingArgument.intervalMillis;
		
		profile = new CacheProfile(id, millis);
	}
	
	@Override
	public void put(K key, V value){
		try{
			profile.putStart();
			cache.put(key, value);
		}catch(Exception e){
			throw e;
		}finally{
			profile.putStop();
		}
	}
	
	@Override
	public V get(K key){
		try{
			profile.getStart();
			return cache.get(key);
		}catch(Exception e){
			throw e;
		}finally{
			profile.getStop();
		}
	}
	
	@Override
	public boolean contains(K key){
		try{
			profile.containsStart();
			return cache.contains(key);
		}catch(Exception e){
			throw e;
		}finally{
			profile.containsStop();
		}
	}
	
	@Override
	public V remove(K key){
		try{
			profile.removeStart();
			return cache.remove(key);
		}catch(Exception e){
			throw e;
		}finally{
			profile.removeStop();
		}
	}

	@Override
	public CacheEntry<K, V> evict(){
		return cache.evict();
	}

	@Override
	public int getCurrentSize(){
		return cache.getCurrentSize();
	}

	@Override
	public int getMaximumSize(){
		return cache.getMaximumSize();
	}

	@Override
	public boolean hasExceededMaximumSize(){
		return cache.hasExceededMaximumSize();
	}

	@Override
	public void clear(){
		cache.clear();
	}

	@Override
	public void close(){
		try{
			profile.stopAll();
		}catch(Exception e){
			
		}
		try{
			cache.clear();
		}catch(Exception e){
			throw e;
		}
	}
}