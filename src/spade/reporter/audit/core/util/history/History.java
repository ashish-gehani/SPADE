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
package spade.reporter.audit.core.util.history;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * An ordered sequence of (time -> value) pairs. Supports looking up the value
 * associated with the largest key that is <= the query key (best match).
 */
public class History<T extends Comparable<T>, V> {

	private final TreeMap<T, V> entries = new TreeMap<>();

	public void add(final T key, final V value) {
		if (key == null) {
			throw new IllegalArgumentException("key cannot be NULL");
		}
		if (value == null) {
			throw new IllegalArgumentException("value cannot be NULL");
		}
		// Overwriting if the key already existed.
		// Want to keep the last given the same time.
		entries.put(key, value);
	}

	public Set<V> getValues() {
		return new HashSet<>(entries.values());
	}

	/**
	 * Returns the value whose key is the largest key <= val, or null if none.
	 */
	public V closestTo(final T val) {
		if (val == null) {
			throw new IllegalArgumentException("val cannot be NULL");
		}
		final Map.Entry<T, V> entry = entries.floorEntry(val);
		return entry == null ? null : entry.getValue();
	}

	public boolean has(final V value) {
		return entries.containsValue(value);
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	public int size() {
		return entries.size();
	}

}
