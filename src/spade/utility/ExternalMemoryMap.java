/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2012 SRI International

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
package spade.utility;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.BloomFilter;

/**
 * A map that keeps specified number of elements in memory and kicks out the least recently
 * used ones to a external storage and pulls back the element from disk to memory
 * when specified.
 *
 * This class uses a bloomfilter to keep a track of elements that have been added. This avoids
 * the expensive calls to disk to get an element if the element wasn't found in memory.
 * 
 * @params <K> Any object type
 * @params <V> Object type must implement the Serializable interface 
 * 
 */

public class ExternalMemoryMap<K, V extends Serializable>{
	
	private Logger logger = Logger.getLogger(ExternalMemoryMap.class.getName());

	//bloomfilter to check if the element exists in memory and/or external storage
	private BloomFilter<K> bloomFilter;
	
	//default hasher using the hashCode function.
	private Hasher<K> keyHasher = new Hasher<K>(){
		public String getHash(K k){
			return String.valueOf(k.hashCode());
		}
	};
	
	//main in-memory map to keep items that have been used recently
	private Map<K, Node<K, V>> leastRecentlyUsedCache;

	//data structure to keep track of least recently and most recently used elements
	private Node<K, V> head, tail;
	
	//external storage for least recently used elements
	private ExternalStore<V> cacheStore;
	
	//max in-memory map size
	private int cacheMaxSize = 0;
	
	/**
	 * Main constructor to create the map
	 * @param cacheMaxSize Size of the in-memory map. Must be greater than 0.
	 * @param cacheStore External storage to use for least recently used elements. Cannot be null
	 * @param falsePositiveProbability is the desired false positive probability. Range [0-1]
     * @param expectedNumberOfElements is the expected number of elements in the Bloom filter. Must be greater than 0
	 */
	
	public ExternalMemoryMap(int cacheMaxSize, ExternalStore<V> cacheStore, double falsePositiveProbability, int expectedNumberOfElements) throws Exception{
		
		if(cacheMaxSize < 1){
			throw new IllegalArgumentException("Cache size cannot be less than 1");
		}
		
		if(cacheStore == null){
			throw new IllegalArgumentException("External cache store cannot be null");
		}
		
		if(falsePositiveProbability < 0 || falsePositiveProbability > 1){
			throw new IllegalArgumentException("False positive probability must be in the range [0-1]");
		}
		
		if(expectedNumberOfElements < 1){
			throw new IllegalArgumentException("Expected number of elements cannot be less than 1");
		}
		
		leastRecentlyUsedCache = new HashMap<>();
		bloomFilter = new BloomFilter<>(falsePositiveProbability, expectedNumberOfElements);
		this.cacheMaxSize = cacheMaxSize;
		this.cacheStore = cacheStore;
		
		head = new Node<K, V>(null, null);
		tail = new Node<K, V>(null, null);
		head.next = tail;
		tail.previous = head;
	}
	
	/**
	 * Replaces the default Object.hashCode() function with the provided one. If null this function does nothing.
	 * 
	 * @param hasher Class to use to get a custom hash of the key
	 */
	public void setKeyHashFunction(Hasher<K> hasher){
		if(hasher != null){
			this.keyHasher = hasher;
		}
	}
	
	/**
	 * Returns the max size of the in-memory map as set in the constructor
	 * 
	 * @return In-memory map size
	 */	
	public int getCacheMaxSize(){
		return cacheMaxSize;
	}
	
	/**
	 * Returns the current number of key value pairs in the in-memory map
	 * 
	 * @return current number of key value pairs in the in-memory map
	 */	
	public int size() {
		return leastRecentlyUsedCache.size();
	}
	
	/**
	 * Checks if the current number of elements exceed the max size of the in-memory map and
	 * evicts the least recently used one from in-memory map to the external storage.
	 * 
	 */
	private void evictLeastRecentlyUsed(){
		if(size() >= getCacheMaxSize()){ //evict tail from cache if cache full. 
			leastRecentlyUsedCache.remove(tail.previous.key);
			Node<K, V> node = Node.removeNode(tail.previous);
			try{
				cacheStore.put(keyHasher.getHash(node.key), node.value); //update in db before pushing it out of memory
			}catch(Exception e){
				logger.log(Level.WARNING, "Failed to update cache element in cachestore", e);
			}
		}
	}

	/**
	 * Gets the current Object of the paired against the key (if any) 
	 * 
	 * Pseudocode 
	 * 
	 * 1) exists in bloomfilter
	 * 2) exists in in-memory map then return
	 * 3) doesn't exist in in-memory map then check in external storage
	 * 4) exists in external storage then return
	 * 5) doesn't exist in external storage so a false positive. return null 
	 * 6) doesn't exist in bloomfilter then return null
	 * 
	 * @param Object to get
	 * @return Value paired against the provided key. Null if doesn't exist
	 */
	public V get(Object key) {
		try{
			K k = (K)key;
			if(bloomFilter.contains(k)){ //bloomfilter contains the key
				if(leastRecentlyUsedCache.get(k) != null){ //exists in cache
					Node<K, V> node = leastRecentlyUsedCache.get(k); //get from cache
					Node.makeNodeHead(node, head); //make this node the head
					return node.value; //return the value
				}else{ //doesn't exist in cache
					String hash = keyHasher.getHash(k);
					V value = cacheStore.get(hash); //get from db
					if(value == null){ //if not in DB
						return null; //was false positive
					}else{ //if in DB
						
						evictLeastRecentlyUsed(); //if need be
						
						Node<K, V> node = new Node<>(k, value);
						Node.makeNodeHead(node, head); //make this node the head
						
						leastRecentlyUsedCache.put(k, node); //put in cache
						return value;
					}
				}
			}else{ //not in bloomfilter so not anywhere since no false negative
				return null;
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, null, e);
			return null;
		}
	}

	/**
	 * Inserts/Updates a key-value pair in the in-memory map as well as the external storage 
	 * Pseudocode:
	 * 
	 * 1) add in bloomfilter
	 * 2) if doesn't exists in in-memory map
	 * 3) if exists in map then compare values. if same then nothing. if different then update in in-memory map
	 *  
	 * @param Object to be used as key
	 * @param Object to be inserted against the key
	 * @return Inserted object
	 */	
	public V put(K key, V value) {
		try{
			bloomFilter.add(key);
			Node<K, V> node = leastRecentlyUsedCache.get(key);
//			String hash = keyHasher.getHash((K)key);
			if(node == null){ //if not in cache
				
				evictLeastRecentlyUsed(); //if need be
				
				node = new Node<K, V>(key, value); //create the node
				leastRecentlyUsedCache.put(key, node); //put in cache
				//cacheStore.put(hash, value); //no need to put in db. will be put in when evicted
			}else{ //if node exists in cache
				if(!node.value.equals(value)){ //i.e. new value for the same key. so, update.
					node.value = value;
//					cacheStore.put(hash, value); //no need to put in db. will be put in when evicted
				}
			}
			Node.makeNodeHead(node, head);
			return value;
		}catch(Exception e){
			logger.log(Level.SEVERE, null, e);
			return null;
		}
	}
	
	/**
	 * Removes the key and the value paired against it from the in-memory map and the external storage
	 * 
	 * Pseudocode:
	 * 
	 * 1) If exists in bloomfilter then remove from in-memory map and from external storage
	 * 
	 * @param Object to be removed
	 * @return Removed value paired against the provided key
	 * 
	 */
	public V remove(Object key) {
		try{
			K k = (K)key;
			String hash = keyHasher.getHash(k);
			if(bloomFilter.contains(k)){
				V value = null;
				if(leastRecentlyUsedCache.get(k) != null){
					Node<K, V> node = leastRecentlyUsedCache.get(k);
					Node.removeNode(node);
					value = node.value;
				}else{
					value = cacheStore.get(hash); //get from DB
				}
				leastRecentlyUsedCache.remove(key);
				//remove value from DB
				cacheStore.remove(hash);
				return value;
			}else{
				return null;
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, null, e);
			return null;
		}
	}
	
	/**
	 * Removes all key-value pairing from the bloomfilter, in-memory map and the external storage
	 */
	public void clear() {
		leastRecentlyUsedCache.clear();
		bloomFilter.clear();
		try{
			cacheStore.clear();
		}catch(Exception e){
			logger.log(Level.SEVERE, null, e);
		}
		head = new Node<K, V>(null, null);
		tail = new Node<K, V>(null, null);
	}
	
	/**
	 * A function to close the external store being used
	 */
	public void close(){
		try{
			if(cacheStore != null){
				cacheStore.close();
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to close cache store", e);
		}
	}
}

/**
 * Basic node class to build a doubly linked list along with a few utility functions to act on nodes of doubly linked list
 * 
 * Note: Doubly linked list assumes that the head and tail of the linked list are dummy nodes
 *
 * @param Object key in the key-value pair
 * @param Object value in the key-value pair
 */

class Node<K, V>{
	
	public K key;
	public V value;
	public Node<K, V> next, previous;
	
	public Node(K key, V value){
		this.key = key;
		this.value = value;
	}
		
	/**
	 * Removes the given node from the doubly linked list
	 * @param node to remove
	 * @return removed node with null next and previous pointers
	 */	
	public static <K, V> Node<K, V> removeNode(Node<K, V> node){
		if(node != null){
			if(node.previous != null){
				node.previous.next = node.next;
			}
			if(node.next != null){
				node.next.previous = node.previous;
			}
			node.previous = null;
			node.next = null;
			return node;
		}
		return null;
	}
	
	/**
	 * Given the head of the doubly linked list, this function makes the given node the head of the list.
	 * 
	 * Note: Head is a dummy node and never tinkered with.
	 * 
	 * @param node node to move to head
	 * @param head the dummy head node
	 */
	public static <K, V> void makeNodeHead(Node<K, V> node, Node<K, V> head){
		removeNode(node);
		node.next = head.next;
		node.previous = head;
		head.next.previous = node;
		head.next = node;
	}
	
}
