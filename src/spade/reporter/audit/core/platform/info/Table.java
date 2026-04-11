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
package spade.reporter.audit.core.platform.info;

import java.util.HashMap;
import java.util.Map;

import spade.reporter.audit.core.platform.ID;

/**
 * Registry of predefined, known-good platform configurations.
 *
 * Entries are sourced from {@link Predefined}.  To add a new configuration,
 * update {@link Predefined} only — this class requires no changes.
 */
public final class Table{

	private final Map<ID, Info> map = new HashMap<>();

	public Table(){
		for(final Info info : Predefined.entries()){
			map.put(info.getId(), info);
		}
	}

	public Info get(final ID id){
		return map.get(id);
	}

	public boolean contains(final ID id){
		return map.containsKey(id);
	}

}
