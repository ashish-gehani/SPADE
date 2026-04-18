/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International

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
package spade.reporter.audit.core.util.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VersionedMap<T, V> implements java.util.Map<VersionedKey<T>, V>{

	private final Map<VersionedKey<T>, V> map = new HashMap<>();
	// Tracks the highest version seen for each base key
	private final Map<T, Long> latestVersion = new HashMap<>();

	// -----------------------------------------------------------------------
	// VersionedMap-specific API
	// -----------------------------------------------------------------------

	/**
	 * Inserts {@code value} under the next available version for {@code key}.
	 * The first insertion uses version 0; each subsequent call increments by 1.
	 */
	public V putNext(final T key, final V value){
		if(key == null){
			throw new IllegalArgumentException("key cannot be NULL");
		}
		final long nextVer = latestVersion.containsKey(key) ? latestVersion.get(key) + 1 : 0L;
		latestVersion.put(key, nextVer);
		return map.put(new VersionedKey<>(key, nextVer), value);
	}

	/**
	 * Returns the {@link VersionedKey} with the highest version for {@code key},
	 * or {@code null} if no entry exists for that base key.
	 */
	public VersionedKey<T> getLatestKey(final T key){
		if(!latestVersion.containsKey(key)){
			return null;
		}
		return new VersionedKey<>(key, latestVersion.get(key));
	}

	/**
	 * Returns the value mapped to the highest version of {@code key},
	 * or {@code null} if no entry exists.
	 */
	public V getLatest(final T key){
		final VersionedKey<T> vk = getLatestKey(key);
		return vk == null ? null : map.get(vk);
	}

	/**
	 * Removes all entries for {@code key} whose version is below the latest,
	 * retaining only the most recent entry.  Does nothing if {@code key} has
	 * zero or one entry.
	 */
	public void removeOldVersions(final T key){
		if(key == null){
			throw new IllegalArgumentException("key cannot be NULL");
		}
		final Long latest = latestVersion.get(key);
		if(latest == null){
			return;
		}
		map.keySet().removeIf(vk -> key.equals(vk.getKey()) && vk.getVersion() < latest);
	}

	/**
	 * Returns all entries whose base key equals {@code key}, sorted by
	 * ascending version.  Returns an empty list when none exist.
	 */
	public List<Map.Entry<VersionedKey<T>, V>> getAllVersions(final T key){
		if(key == null){
			return Collections.emptyList();
		}
		final List<Map.Entry<VersionedKey<T>, V>> result = new ArrayList<>();
		for(final Map.Entry<VersionedKey<T>, V> entry : map.entrySet()){
			if(key.equals(entry.getKey().getKey())){
				result.add(entry);
			}
		}
		result.sort((a, b) -> Long.compare(a.getKey().getVersion(), b.getKey().getVersion()));
		return Collections.unmodifiableList(result);
	}

	// -----------------------------------------------------------------------
	// java.util.Map interface
	// -----------------------------------------------------------------------

	@Override
	public int size(){
		return map.size();
	}

	@Override
	public boolean isEmpty(){
		return map.isEmpty();
	}

	@Override
	public boolean containsKey(final Object key){
		return map.containsKey(key);
	}

	@Override
	public boolean containsValue(final Object value){
		return map.containsValue(value);
	}

	@Override
	public V get(final Object key){
		return map.get(key);
	}

	/**
	 * Inserts the entry and updates the latest-version index if the key's
	 * version is higher than any previously seen for the same base key.
	 */
	@Override
	public V put(final VersionedKey<T> key, final V value){
		if(key == null){
			throw new IllegalArgumentException("key cannot be NULL");
		}
		final Long current = latestVersion.get(key.getKey());
		if(current == null || key.getVersion() > current){
			latestVersion.put(key.getKey(), key.getVersion());
		}
		return map.put(key, value);
	}

	@Override
	public V remove(final Object key){
		final V removed = map.remove(key);
		if(removed != null && key instanceof VersionedKey){
			@SuppressWarnings("unchecked")
			final VersionedKey<T> vk = (VersionedKey<T>) key;
			final Long current = latestVersion.get(vk.getKey());
			if(current != null && current == vk.getVersion()){
				// Scan remaining entries to find the new highest version
				long newLatest = Long.MIN_VALUE;
				boolean found = false;
				for(final VersionedKey<T> k : map.keySet()){
					if(vk.getKey().equals(k.getKey())){
						if(!found || k.getVersion() > newLatest){
							newLatest = k.getVersion();
							found = true;
						}
					}
				}
				if(found){
					latestVersion.put(vk.getKey(), newLatest);
				}else{
					latestVersion.remove(vk.getKey());
				}
			}
		}
		return removed;
	}

	@Override
	public void putAll(final Map<? extends VersionedKey<T>, ? extends V> m){
		for(final Map.Entry<? extends VersionedKey<T>, ? extends V> entry : m.entrySet()){
			put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public void clear(){
		map.clear();
		latestVersion.clear();
	}

	@Override
	public java.util.Set<VersionedKey<T>> keySet(){
		return map.keySet();
	}

	@Override
	public java.util.Collection<V> values(){
		return map.values();
	}

	@Override
	public java.util.Set<Map.Entry<VersionedKey<T>, V>> entrySet(){
		return map.entrySet();
	}

}
