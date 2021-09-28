/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2021 SRI International

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
package spade.filter.clamprov;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;

public class Buffer<T>{

	private final long windowMillis;
	private Long oldestEventMillis = null;

	private final Map<BufferKey, TreeMap<Long, PriorityQueue<T>>> events = new HashMap<>();

	public Buffer(final long windowMillis){
		this.windowMillis = windowMillis;
	}

	public synchronized final void add(final BufferKey bufferKey, final long eventMillis, final T value){
		if(oldestEventMillis == null){
			oldestEventMillis = eventMillis;
		}
		if(oldestEventMillis < eventMillis - windowMillis){
			oldestEventMillis = removeOlderThan(eventMillis - windowMillis);
		}

		TreeMap<Long, PriorityQueue<T>> times = events.get(bufferKey);
		if(times == null){
			times = new TreeMap<Long, PriorityQueue<T>>();
			events.put(bufferKey, times);
		}
		PriorityQueue<T> values = times.get(eventMillis);
		if(values == null){
			values = new PriorityQueue<T>();
			times.put(eventMillis, values);
		}
		values.add(value);
	}

	public synchronized final T get(final BufferKey bufferKey, final long eventMillis){
		final TreeMap<Long, PriorityQueue<T>> times = events.get(bufferKey);
		if(times == null){
			return null;
		}

		final long eventMinMillis = eventMillis - (windowMillis / 2);
		final long eventMaxMillis = eventMillis + (windowMillis / 2);

		long minDiffMillis = Long.MAX_VALUE;

		Long keyForValues = eventMillis;
		PriorityQueue<T> values = times.get(keyForValues);
		if(values == null){
			for(final Long keyMillis : times.keySet()){
				if(keyMillis >= eventMaxMillis){
					break;
				}
				if(keyMillis < eventMinMillis){
					continue;
				}
				final long currentDiffMillis = Math.abs(eventMillis - keyMillis);
				if(currentDiffMillis < minDiffMillis){
					minDiffMillis = currentDiffMillis;
					keyForValues = keyMillis;
					values = times.get(keyForValues);
				}
			}
		}
		if(values == null){
			return null;
		}
		final T value = values.poll();
		// Cleanup
		if(values.isEmpty()){
			times.remove(keyForValues);
			if(events.get(bufferKey).isEmpty()){
				events.remove(bufferKey);
			}
		}
		return value;
	}

	public synchronized final Long removeOlderThan(final long timeMillis){
		Long oldestMillisFound = Long.MAX_VALUE;

		final Set<BufferKey> outerKeysToRemove = new HashSet<>();

		for(final Map.Entry<BufferKey, TreeMap<Long, PriorityQueue<T>>> outerEntry : events.entrySet()){
			final Set<Long> innerKeysToRemove = new HashSet<Long>();

			final TreeMap<Long, PriorityQueue<T>> times = outerEntry.getValue();
			for(final Map.Entry<Long, PriorityQueue<T>> innerEntry : times.entrySet()){
				final boolean doRemove;
				final Long keyMillis = innerEntry.getKey();
				if(keyMillis >= timeMillis){
					doRemove = innerEntry.getValue().isEmpty();
				}else{
					doRemove = true;
				}
				if(doRemove){
					innerKeysToRemove.add(innerEntry.getKey());
				}

				if(keyMillis < oldestMillisFound){
					oldestMillisFound = keyMillis;
				}
			}
			for(final Long innerKeyToRemove : innerKeysToRemove){
				times.remove(innerKeyToRemove);
			}
			if(times.isEmpty()){
				outerKeysToRemove.add(outerEntry.getKey());
			}
		}
		for(final BufferKey outerKeyToRemove : outerKeysToRemove){
			events.remove(outerKeyToRemove);
		}
		if(oldestMillisFound == Long.MAX_VALUE){
			return null;
		}else{
			return oldestMillisFound;
		}
	}

	public synchronized final void clear(){
		events.clear();
	}
}
