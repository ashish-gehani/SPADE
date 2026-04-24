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
package spade.reporter.audit.linux.provenance.event.handler;

import java.util.ArrayList;
import java.util.List;

import spade.core.AbstractVertex;
import spade.reporter.audit.core.provenance.ProvenanceElement;
import spade.reporter.audit.linux.provenance.PlatformProcess;

public class ProcessExit implements spade.reporter.audit.core.provenance.event.handler.Handler<spade.reporter.audit.linux.provenance.event.ProcessExit, Context>{

	@Override
	public List<ProvenanceElement> handle(final spade.reporter.audit.linux.provenance.event.ProcessExit event, final Context provContext){
		final PlatformProcess provProcess = event.getProcess();

		final AbstractVertex processVertex = provContext.getVertexGenerator().generate();
		processVertex.addAnnotations(provProcess.getKeyAnnotations(provContext));
		processVertex.addAnnotations(provProcess.getExtraAnnotations(provContext));

		final List<ProvenanceElement> elements = new ArrayList<>();
		elements.add(ProvenanceElement.of(processVertex));
		return elements;
	}

}
