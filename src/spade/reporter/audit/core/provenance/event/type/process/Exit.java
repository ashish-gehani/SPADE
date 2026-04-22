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
package spade.reporter.audit.core.provenance.event.type.process;

import java.util.ArrayList;
import java.util.List;

import spade.core.AbstractVertex;
import spade.reporter.audit.core.provenance.Config;
import spade.reporter.audit.core.provenance.ProvenanceElement;
import spade.reporter.audit.core.provenance.event.Event;
import spade.reporter.audit.core.provenance.event.ID;
import spade.reporter.audit.core.provenance.event.ProcessType;
import spade.reporter.audit.core.provenance.type.ProvenanceContext;
import spade.reporter.audit.core.provenance.type.Process;

public abstract class Exit<C extends ProvenanceContext> extends Event<C>{

	private final Process<C> process;

	public Exit(final ID id, final Process<C> process){
		super(ProcessType.EXIT, id);
		if(process == null){
			throw new IllegalArgumentException("process cannot be NULL");
		}
		this.process = process;
	}

	public Process<C> getProcess(){
		return process;
	}

	@Override
	public List<ProvenanceElement> handle(final C provContext, final Config managerConfig){
		final AbstractVertex processVertex = managerConfig.getVertexGenerator().generate();
		processVertex.addAnnotations(process.getKeyAnnotations(provContext));
		processVertex.addAnnotations(process.getExtraAnnotations(provContext));

		final List<ProvenanceElement> elements = new ArrayList<>();
		elements.add(ProvenanceElement.of(processVertex));
		return elements;
	}

}
