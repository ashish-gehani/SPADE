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
package spade.utility.map.external.store;

import java.math.BigInteger;

import spade.utility.profile.ReportingArgument;

/**
 * Profiled store with instrumentation for measuring time for get, put, contains, and remove.
 *
 * @param <K>
 * @param <V>
 */
public class ProfiledStore<K, V> extends Store<K, V>{

	private final StoreProfile profile;
	
	private Store<K, V> store;
	
	public ProfiledStore(Store<K, V> store, ReportingArgument reportingArgument){
		super(store.keyConverter, store.valueConverter);
		this.store = store;
		
		final String id = reportingArgument.id;
		final long millis = reportingArgument.intervalMillis;
		
		profile = new StoreProfile(id, millis);
	}

	@Override
	public void put(K key, V value) throws Exception{
		try{
			profile.putStart();
			store.put(key, value);
		}catch(Exception e){
			throw e;
		}finally{
			profile.putStop();
		}
	}

	@Override
	public V get(K key) throws Exception{
		try{
			profile.getStart();
			return store.get(key);
		}catch(Exception e){
			throw e;
		}finally{
			profile.getStop();
		}
	}

	@Override
	public void remove(K key) throws Exception{
		try{
			profile.removeStart();
			store.remove(key);
		}catch(Exception e){
			throw e;
		}finally{
			profile.removeStop();
		}
	}
	
	@Override
	public boolean contains(K key) throws Exception{
		try{
			profile.containsStart();
			return store.contains(key);
		}catch(Exception e){
			throw e;
		}finally{
			profile.containsStop();
		}
	}

	@Override
	public void close() throws Exception{
		try{
			profile.stopAll();
		}catch(Exception e){
			
		}
		try{
			store.close();
		}catch(Exception e){
			throw e;
		}
	}
	
	@Override
	public void clear() throws Exception{
		try{
			store.clear();
		}catch(Exception e){
			throw e;
		}
	}

	@Override
	public BigInteger getSizeOnDiskInBytes() throws Exception{
		return store.getSizeOnDiskInBytes();
	}

}
