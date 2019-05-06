/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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
package spade.filter;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.reporter.audit.OPMConstants;
import spade.vertex.prov.Activity;
import spade.vertex.prov.Agent;
import spade.vertex.prov.Entity;

import java.util.Map;

/**
 *
 * @author Dawood Tariq
 */
public abstract class OPM2ProvVertexEdge extends AbstractFilter {

    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        putInNextFilter(createProvVertex(incomingVertex));
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
        AbstractVertex childVertex = createProvVertex(incomingEdge.getChildVertex());
        AbstractVertex parentVertex = createProvVertex(incomingEdge.getParentVertex());
        AbstractEdge newEdge = null;
        if (incomingEdge instanceof spade.edge.opm.Used) {
            newEdge = new spade.edge.prov.Used((Activity) childVertex, (Entity) parentVertex);
        } else if (incomingEdge instanceof spade.edge.opm.WasControlledBy) {
            newEdge = new spade.edge.prov.WasAssociatedWith((Activity) childVertex, (Agent) parentVertex);
        } else if (incomingEdge instanceof spade.edge.opm.WasDerivedFrom) {
            newEdge = new spade.edge.prov.WasDerivedFrom((Entity) childVertex, (Entity) parentVertex);
        } else if (incomingEdge instanceof spade.edge.opm.WasGeneratedBy) {
            newEdge = new spade.edge.prov.WasGeneratedBy((Entity) childVertex, (Activity) parentVertex);
        } else if (incomingEdge instanceof spade.edge.opm.WasTriggeredBy) {
            newEdge = new spade.edge.prov.WasInformedBy((Activity) childVertex, (Activity) parentVertex);
        }
        for (Map.Entry<String, String> entry : incomingEdge.getAnnotations().entrySet()) {
        	if(entry.getKey().equals(OPMConstants.TYPE))
        		continue;
            newEdge.addAnnotation(entry.getKey(), entry.getValue());
        }
        putInNextFilter(newEdge);
    }

    private AbstractVertex createProvVertex(AbstractVertex vertex) {
        AbstractVertex newVertex = null;
        if (vertex instanceof spade.vertex.opm.Agent) {
            newVertex = new spade.vertex.prov.Agent();
        } else if (vertex instanceof spade.vertex.opm.Artifact) {
            newVertex = new spade.vertex.prov.Entity();
        } else if (vertex instanceof spade.vertex.opm.Process) {
            newVertex = new spade.vertex.prov.Activity();
        }
        for (Map.Entry<String, String> entry : vertex.getAnnotations().entrySet()) {
        	if(entry.getKey().equals(OPMConstants.TYPE))
        		continue;
            newVertex.addAnnotation(entry.getKey(), entry.getValue());
        }
        return newVertex;
    }
}
