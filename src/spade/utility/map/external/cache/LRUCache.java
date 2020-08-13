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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import spade.utility.DoublyLinkedList;

/**
 * LRU cache implementation
 *
 * @param <K> key type
 * @param <V> value type
 */
public class LRUCache<K, V> implements Cache<K, V>{

	private final int maximumSize;
	
	private Map<K, DoublyLinkedList<CacheEntry<K, V>>.DoublyLinkedListNode<CacheEntry<K, V>>> map 
		= new HashMap<K, DoublyLinkedList<CacheEntry<K, V>>.DoublyLinkedListNode<CacheEntry<K, V>>>();
	private DoublyLinkedList<CacheEntry<K, V>> accessOrderedList = new DoublyLinkedList<CacheEntry<K, V>>();
	
	public LRUCache(int maximumSize){
		this.maximumSize = maximumSize;
	}
	
	@Override
	public void put(K key, V value){
		DoublyLinkedList<CacheEntry<K, V>>.DoublyLinkedListNode<CacheEntry<K, V>> node = map.get(key);
		if(node == null){
			CacheEntry<K, V> cacheEntry = createCacheEntry(key, value);
			node = accessOrderedList.addFirst(cacheEntry);
			map.put(key, node);
		}else{
			node.getValue().setValue(value); // Update value in case different
			accessOrderedList.makeFirst(node);
		}
	}

	@Override
	public V get(K key){
		DoublyLinkedList<CacheEntry<K, V>>.DoublyLinkedListNode<CacheEntry<K, V>> node = map.get(key);
		if(node == null){
			return null;
		}else{
			V value = node.getValue().getValue();
			accessOrderedList.makeFirst(node);
			return value;
		}
	}

	public List<K> getKeysInLRUAccessOrder(){
		final List<K> list = new ArrayList<K>();
		DoublyLinkedList<CacheEntry<K, V>>.DoublyLinkedListNode<CacheEntry<K, V>> node = accessOrderedList.getFirst();
		while(node != null){
			list.add(node.getValue().key);
			node = accessOrderedList.getNext(node);
		}
		return list;
	}

	// Doesn't modify access list
	@Override
	public boolean contains(K key){
		return map.containsKey(key);
	}

	@Override
	public V remove(K key){
		DoublyLinkedList<CacheEntry<K, V>>.DoublyLinkedListNode<CacheEntry<K, V>> node = map.remove(key);
		if(node == null){
			return null;
		}else{
			accessOrderedList.removeNode(node);
			return node.getValue().getValue();
		}
	}

	@Override
	public CacheEntry<K, V> evict(){
		DoublyLinkedList<CacheEntry<K, V>>.DoublyLinkedListNode<CacheEntry<K, V>> lastNode = accessOrderedList.removeLast();
		if(lastNode == null){
			return null;
		}else{
			map.remove(lastNode.getValue().key);
			return lastNode.getValue();
		}
	}

	@Override
	public int getCurrentSize(){
		return map.size();
	}

	@Override
	public int getMaximumSize(){
		return maximumSize;
	}

	@Override
	public boolean hasExceededMaximumSize(){
		return map.size() > maximumSize;
	}
	
	@Override
	public void clear(){
		map.clear();
		accessOrderedList.clear();
	}
	
	@Override
	public void close(){}
	
	private CacheEntry<K, V> createCacheEntry(K key, V value){
		return new CacheEntry<K, V>(key, value);
	}
}
