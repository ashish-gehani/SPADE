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
package spade.utility.map.external.screen;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;

import spade.core.BloomFilter;
import spade.utility.HelperFunctions;

/**
 * Bloom filter screen
 * 
 * @param <K> key type
 */
public class BloomFilterScreen<K> implements Screen<K>{
	/**
	 * Path to save the bloomfilter to on close
	 */
	public final String savePath;
	/**
	 * Bloomfilter object
	 */
	private final BloomFilter<K> bloomFilter;
	
	protected BloomFilterScreen(String savePath, BloomFilter<K> bloomFilter){
		this.savePath = savePath;
		this.bloomFilter = bloomFilter;
	}
	
	@Override
	public void add(K key){
		if(key == null){
			bloomFilter.add(String.valueOf(key).getBytes());
		}else{
			bloomFilter.add(key);
		}
	}
	
	@Override
	public boolean contains(K key){
		if(key == null){
			return bloomFilter.contains(String.valueOf(key).getBytes());
		}else{
			return bloomFilter.contains(key);
		}
	}

	@Override
	public boolean remove(K key){
		return false;
	}

	@Override
	public void clear(){
		bloomFilter.clear();
	}

	@Override
	public void close() throws Exception{
		if(!HelperFunctions.isNullOrEmpty(savePath)){
			FileOutputStream fos = new FileOutputStream(new File(savePath));
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(bloomFilter);
			oos.close();
			fos.close();
		}
	}

	@Override
	public long size(){
		return bloomFilter.count();
	}
}