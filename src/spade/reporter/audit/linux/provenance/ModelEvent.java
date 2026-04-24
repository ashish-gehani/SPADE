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
package spade.reporter.audit.linux.provenance;

import java.util.HashMap;
import java.util.Map;

import spade.reporter.audit.linux.provenance.event.handler.Context;
import spade.reporter.audit.linux.source.audit.event.ID;

public class ModelEvent extends spade.reporter.audit.core.provenance.ModelEvent<Context>{

	private final ID id;

	public ModelEvent(final ID id){
		if(id == null){
			throw new IllegalArgumentException("id cannot be NULL");
		}
		this.id = id;
	}

	@Override
	public Map<String, String> getKeyAnnotations(final Context context){
		final Map<String, String> map = new HashMap<>();
		map.put("event_id", String.valueOf(id.getNum().getValue()));
		map.put("time", id.getTimestamp().getSecondsInAuditFormat());
		return map;
	}

	@Override
	public Map<String, String> getExtraAnnotations(final Context context){
		return new HashMap<>();
	}

}
