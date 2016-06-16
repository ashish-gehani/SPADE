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


public class CachedMap<K, V extends Serializable>{
	
	private Logger logger = Logger.getLogger(CachedMap.class.getName());

	private BloomFilter<K> bloomFilter;
	
	//default hasher
	private Hasher<K> keyHasher = new Hasher<K>(){
		public String getHash(K k){
			return String.valueOf(k.hashCode());
		}
	};
	
	private Map<K, Node<K, V>> lruCache;

	private Node<K, V> head, tail;
	
	private CacheStore<V> cacheStore;
	
	private int cacheMaxSize = 0;
	
	public CachedMap(int cacheMaxSize, CacheStore<V> cacheStore, double falsePositiveProbability, int expectedNumberOfElements){
		 lruCache = new HashMap<>();
		 bloomFilter = new BloomFilter<>(falsePositiveProbability, expectedNumberOfElements);
		 this.cacheMaxSize = cacheMaxSize;
		 this.cacheStore = cacheStore;
		 
		 head = new Node<K, V>(null, null);
		 tail = new Node<K, V>(null, null);
		 head.next = tail;
		 tail.previous = head;
	}
	
	//set this if you don't want to use the default hash function
	public void setKeyHashFunction(Hasher<K> hasher){
		this.keyHasher = hasher;
	}
	
	public int getCacheMaxSize(){
		return cacheMaxSize;
	}
	
	public int size() {
		return lruCache.size();
	}

	public V get(Object key) {
		try{
			K k = (K)key;
			if(bloomFilter.contains(k)){ //bloomfilter contains the key
				if(lruCache.get(k) != null){ //exists in cache
					Node<K, V> node = lruCache.get(k); //get from cache
					Node.makeNodeHead(node, head); //make this node the head
					return node.value; //return the value
				}else{ //doesn't exist in cache
					String hash = keyHasher.getHash(k);
					V value = cacheStore.get(hash); //get from db
					if(value == null){ //if not in DB
						return null; //was false positive
					}else{ //if in DB
						
						if(size() >= getCacheMaxSize()){ //evict tail from cache if cache full
							lruCache.remove(tail.previous.key);
							Node.removeNode(tail.previous);
						}
						
						Node<K, V> node = new Node<>(k, value);
						Node.makeNodeHead(node, head); //make this node the head
						
						lruCache.put(k, node); //put in cache
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

	public V put(K key, V value) {
		try{
			bloomFilter.add(key);
			Node<K, V> node = lruCache.get(key);
			String hash = keyHasher.getHash((K)key);
			if(node == null){ //if not in cache
				if(size() >= getCacheMaxSize()){ //going to put in cache so evict the lru if cache full
					lruCache.remove(tail.previous.key);
					Node.removeNode(tail.previous);
				}
				node = new Node<K, V>(key, value); //create the node
				lruCache.put(key, node); //put in cache
				cacheStore.put(hash, value); //put in db
			}else{ //if node exists in cache
				if(!node.value.equals(value)){ //i.e. new value for the same key. so, update.
					node.value = value;
					//update in db too
					cacheStore.put(hash, value);
				}
			}
			Node.makeNodeHead(node, head);
			return value;
		}catch(Exception e){
			logger.log(Level.SEVERE, null, e);
			return null;
		}
	}

	public V remove(Object key) {
		try{
			K k = (K)key;
			String hash = keyHasher.getHash(k);
			if(bloomFilter.contains(k)){
				V value = null;
				if(lruCache.get(k) != null){
					Node<K, V> node = lruCache.get(k);
					Node.removeNode(node);
					value = node.value;
				}else{
					value = cacheStore.get(hash); //get from DB
				}
				lruCache.remove(key);
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
	
	public void clear() {
		lruCache.clear();
		bloomFilter.clear();
		try{
			cacheStore.clear();
		}catch(Exception e){
			logger.log(Level.SEVERE, null, e);
		}
		head = new Node<K, V>(null, null);
		tail = new Node<K, V>(null, null);
	}
}

class Node<K, V>{
	
	public K key;
	public V value;
	public Node<K, V> next, previous;
	
	public Node(K key, V value){
		this.key = key;
		this.value = value;
	}
		
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
	
	public static <K, V> void makeNodeHead(Node<K, V> node, Node<K, V> head){
		removeNode(node);
		node.next = head.next;
		node.previous = head;
		head.next.previous = node;
		head.next = node;
	}
	
}
