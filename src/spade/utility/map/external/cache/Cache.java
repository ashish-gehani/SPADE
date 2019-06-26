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

/**
 * Cache interface for external map
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface Cache<K, V>{

	public void put(K key, V value);
	public V get(K key);
	/**
	 * Doesn't update the cache metadata
	 * @param key
	 * @return
	 */
	public boolean contains(K key);
	public V remove(K key);
	/**
	 * Always returns an entry unless the cache is empty
	 * @return null/cache entry
	 */
	public CacheEntry<K, V> evict();
	/**
	 * Current number of elements in the cache
	 * @return
	 */
	public int getCurrentSize();
	/**
	 * Maximum number of elements set at creation of cache
	 * @return
	 */
	public int getMaximumSize();
	/**
	 * Returns whether there are more elements than allowed
	 * 
	 * @return true/false
	 */
	public boolean hasExceededMaximumSize();
	public void clear();
	/**
	 * Do any destruction work necessary
	 */
	public void close();
	
	/**
	 * Cache entry to be extended by other cache strategies and to be returned by evict
	 * 
	 * @param <K> key type
	 * @param <V> value type
	 */
	public static class CacheEntry<K, V>{
		public final K key;
		private V value;
		public CacheEntry(K key, V value){
			this.key = key;
			this.value = value;
		}
		public V getValue(){
			return value;
		}
		public void setValue(V value){
			this.value = value;
		}
		@Override
		public int hashCode(){
			final int prime = 31;
			int result = 1;
			result = prime * result + ((key == null) ? 0 : key.hashCode());
			result = prime * result + ((value == null) ? 0 : value.hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj){
			if(this == obj)
				return true;
			if(obj == null)
				return false;
			if(getClass() != obj.getClass())
				return false;
			@SuppressWarnings("rawtypes")
			CacheEntry other = (CacheEntry)obj;
			if(key == null){
				if(other.key != null)
					return false;
			}else if(!key.equals(other.key))
				return false;
			if(value == null){
				if(other.value != null)
					return false;
			}else if(!value.equals(other.value))
				return false;
			return true;
		}
		@Override
		public String toString(){
			return "CacheEntry [key=" + key + ", value=" + value + "]";
		}
	}
}
