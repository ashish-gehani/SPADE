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
package spade.reporter.audit.core.provenance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import spade.reporter.audit.core.provenance.event.Event;
import spade.reporter.audit.core.provenance.type.AbstractContext;

public final class Manager{

	private final ManagerContext managerContext;

	public Manager(
		final VertexGenerator vertexGenerator,
		final EdgeGenerator edgeGenerator
	){
		if(vertexGenerator == null){
			throw new IllegalArgumentException("vertexGenerator cannot be NULL");
		}
		if(edgeGenerator == null){
			throw new IllegalArgumentException("edgeGenerator cannot be NULL");
		}
		this.managerContext = new ManagerContext(vertexGenerator, edgeGenerator);
	}

	public List<ProvenanceElement> handle(final AbstractContext context, final List<Event> events){
		if(context == null){
			throw new IllegalArgumentException("context cannot be NULL");
		}
		if(events == null){
			throw new IllegalArgumentException("events cannot be NULL");
		}
		final List<ProvenanceElement> result = new ArrayList<>();
		for(final Event event : events){
			if(event == null){
				throw new IllegalArgumentException("event in list cannot be NULL");
			}
			result.addAll(event.handle(context, managerContext));
		}
		return Collections.unmodifiableList(result);
	}

}
