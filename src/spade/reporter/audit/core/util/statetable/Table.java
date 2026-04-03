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
package spade.reporter.audit.core.util.statetable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public abstract class Table<T extends Indexable<T>, S extends State<T>>{

	private final Map<T, S> map = new HashMap<>();

	public void put(final T id, final S state){
		if(id == null){
			throw new IllegalArgumentException("ID cannot be NULL");
		}
		if(state == null){
			throw new IllegalArgumentException("State cannot be NULL");
		}
		map.put(id, state);
	}

	public S get(final T id){
		if(id == null){
			throw new IllegalArgumentException("ID cannot be NULL");
		}
		return map.get(id);
	}

	public S remove(final T id){
		if(id == null){
			throw new IllegalArgumentException("ID cannot be NULL");
		}
		return map.remove(id);
	}

	public boolean contains(final T id){
		if(id == null){
			throw new IllegalArgumentException("ID cannot be NULL");
		}
		return map.containsKey(id);
	}

	public Set<T> ids(){
		return Collections.unmodifiableSet(map.keySet());
	}

	public int size(){
		return map.size();
	}

}
