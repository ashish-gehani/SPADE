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

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.reporter.audit.core.provenance.Config;
import spade.reporter.audit.core.provenance.ProvenanceElement;
import spade.reporter.audit.core.provenance.event.Event;
import spade.reporter.audit.core.provenance.event.ID;
import spade.reporter.audit.core.provenance.event.ProcessType;
import spade.reporter.audit.core.provenance.type.ProvenanceContext;
import spade.reporter.audit.core.provenance.type.Process;

public abstract class Control<C extends ProvenanceContext> extends Event<C>{

	private final Process<C> controller;
	private final Process<C> target;

	public Control(final ID id, final Process<C> controller, final Process<C> target){
		super(ProcessType.CONTROL, id);
		if(controller == null){
			throw new IllegalArgumentException("controller cannot be NULL");
		}
		if(target == null){
			throw new IllegalArgumentException("target cannot be NULL");
		}
		this.controller = controller;
		this.target = target;
	}

	public Process<C> getController(){
		return controller;
	}

	public Process<C> getTarget(){
		return target;
	}

	@Override
	public List<ProvenanceElement> handle(final C provContext, final Config managerConfig){
		final AbstractVertex controllerVertex = managerConfig.getVertexGenerator().generate();
		controllerVertex.addAnnotations(controller.getKeyAnnotations(provContext));
		controllerVertex.addAnnotations(controller.getExtraAnnotations(provContext));

		final AbstractVertex targetVertex = managerConfig.getVertexGenerator().generate();
		targetVertex.addAnnotations(target.getKeyAnnotations(provContext));
		targetVertex.addAnnotations(target.getExtraAnnotations(provContext));

		final AbstractEdge edge = managerConfig.getEdgeGenerator().generate(controllerVertex, targetVertex);
		edge.addAnnotations(getKeyAnnotations(provContext));
		edge.addAnnotations(getExtraAnnotations(provContext));

		final List<ProvenanceElement> elements = new ArrayList<>();
		elements.add(ProvenanceElement.of(controllerVertex));
		elements.add(ProvenanceElement.of(targetVertex));
		elements.add(ProvenanceElement.of(edge));
		return elements;
	}

}
