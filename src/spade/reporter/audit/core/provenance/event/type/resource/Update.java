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
package spade.reporter.audit.core.provenance.event.type.resource;

import java.util.ArrayList;
import java.util.List;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.reporter.audit.core.provenance.Context;
import spade.reporter.audit.core.provenance.ProvenanceElement;
import spade.reporter.audit.core.provenance.event.Event;
import spade.reporter.audit.core.provenance.event.ID;
import spade.reporter.audit.core.provenance.event.ResourceType;
import spade.reporter.audit.core.provenance.type.AbstractContext;
import spade.reporter.audit.core.provenance.type.AbstractProcess;
import spade.reporter.audit.core.provenance.type.AbstractResource;

public abstract class Update<C extends AbstractContext> extends Event<C>{

	private final AbstractProcess<C> updater;
	private final AbstractResource<C> oldVersion;
	private final AbstractResource<C> newVersion;

	public Update(
		final ID id,
		final AbstractProcess<C> updater,
		final AbstractResource<C> oldVersion,
		final AbstractResource<C> newVersion
	){
		super(ResourceType.UPDATE, id);
		if(updater == null){
			throw new IllegalArgumentException("updater cannot be NULL");
		}
		if(oldVersion == null){
			throw new IllegalArgumentException("oldVersion cannot be NULL");
		}
		if(newVersion == null){
			throw new IllegalArgumentException("newVersion cannot be NULL");
		}
		this.updater = updater;
		this.oldVersion = oldVersion;
		this.newVersion = newVersion;
	}

	public AbstractProcess<C> getUpdater(){
		return updater;
	}

	public AbstractResource<C> getOldVersion(){
		return oldVersion;
	}

	public AbstractResource<C> getNewVersion(){
		return newVersion;
	}

	@Override
	public List<ProvenanceElement> handle(final C provContext, final Context managerContext){
		final AbstractVertex updaterVertex = managerContext.getVertexGenerator().generate();
		updaterVertex.addAnnotations(updater.getKeyAnnotations(provContext));
		updaterVertex.addAnnotations(updater.getExtraAnnotations(provContext));

		final AbstractVertex oldVertex = managerContext.getVertexGenerator().generate();
		oldVertex.addAnnotations(oldVersion.getKeyAnnotations(provContext));
		oldVertex.addAnnotations(oldVersion.getExtraAnnotations(provContext));

		final AbstractVertex newVertex = managerContext.getVertexGenerator().generate();
		newVertex.addAnnotations(newVersion.getKeyAnnotations(provContext));
		newVertex.addAnnotations(newVersion.getExtraAnnotations(provContext));

		final AbstractEdge updaterToNew = managerContext.getEdgeGenerator().generate(updaterVertex, newVertex);
		updaterToNew.addAnnotations(getKeyAnnotations(provContext));
		updaterToNew.addAnnotations(getExtraAnnotations(provContext));

		final AbstractEdge newToOld = managerContext.getEdgeGenerator().generate(newVertex, oldVertex);
		newToOld.addAnnotations(getKeyAnnotations(provContext));
		newToOld.addAnnotations(getExtraAnnotations(provContext));

		final List<ProvenanceElement> elements = new ArrayList<>();
		elements.add(ProvenanceElement.of(updaterVertex));
		elements.add(ProvenanceElement.of(oldVertex));
		elements.add(ProvenanceElement.of(newVertex));
		elements.add(ProvenanceElement.of(updaterToNew));
		elements.add(ProvenanceElement.of(newToOld));
		return elements;
	}

}
