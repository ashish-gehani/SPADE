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
import java.util.logging.Logger;

/**
 *
 * @author Dawood Tariq
 */
public abstract class OPM2ProvVertexEdge extends AbstractFilter {

    public final Logger logger = Logger.getLogger(this.getClass().getName());

    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        final AbstractVertex newVertex = createProvVertex(incomingVertex);
        if (newVertex == null) {
            return;
        }
        putInNextFilter(newVertex);
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
        if (incomingEdge == null){
            logger.warning("Unexpectedly received NULL edge");
            return;
        }
        AbstractVertex childVertex = createProvVertex(incomingEdge.getChildVertex());
        AbstractVertex parentVertex = createProvVertex(incomingEdge.getParentVertex());
        AbstractEdge newEdge = null;
        if (
            incomingEdge instanceof spade.edge.opm.Used
            || spade.edge.opm.Used.typeValue.equals(incomingEdge.type())
        ) {
            newEdge = new spade.edge.prov.Used((Activity) childVertex, (Entity) parentVertex);
        } else if (
            incomingEdge instanceof spade.edge.opm.WasControlledBy
            || spade.edge.opm.WasControlledBy.typeValue.equals(incomingEdge.type())
        ) {
            newEdge = new spade.edge.prov.WasAssociatedWith((Activity) childVertex, (Agent) parentVertex);
        } else if (
            incomingEdge instanceof spade.edge.opm.WasDerivedFrom
            || spade.edge.opm.WasDerivedFrom.typeValue.equals(incomingEdge.type())
        ) {
            newEdge = new spade.edge.prov.WasDerivedFrom((Entity) childVertex, (Entity) parentVertex);
        } else if (
            incomingEdge instanceof spade.edge.opm.WasGeneratedBy
            || spade.edge.opm.WasGeneratedBy.typeValue.equals(incomingEdge.type())
        ) {
            newEdge = new spade.edge.prov.WasGeneratedBy((Entity) childVertex, (Activity) parentVertex);
        } else if (
            incomingEdge instanceof spade.edge.opm.WasTriggeredBy
            || spade.edge.opm.WasTriggeredBy.typeValue.equals(incomingEdge.type())
        ) {
            newEdge = new spade.edge.prov.WasInformedBy((Activity) childVertex, (Activity) parentVertex);
        } else {
            logger.warning("Unhandled edge type: " + incomingEdge.getClass().getName());
            return;
        }
        for (Map.Entry<String, String> entry : incomingEdge.getCopyOfAnnotations().entrySet()) {
        	if(entry.getKey().equals(OPMConstants.TYPE))
        		continue;
            newEdge.addAnnotation(entry.getKey(), entry.getValue());
        }
        putInNextFilter(newEdge);
    }

    private AbstractVertex createProvVertex(AbstractVertex vertex) {
        if (vertex == null){
            logger.warning("Unexpectedly received NULL vertex");
            return null;
        }
        AbstractVertex newVertex = null;
        if (
            vertex instanceof spade.vertex.opm.Agent
            || spade.vertex.opm.Agent.typeValue.equals(vertex.type())
        ) {
            newVertex = new spade.vertex.prov.Agent();
        } else if (
            vertex instanceof spade.vertex.opm.Artifact
            || spade.vertex.opm.Artifact.typeValue.equals(vertex.type())
        ) {
            newVertex = new spade.vertex.prov.Entity();
        } else if (
            vertex instanceof spade.vertex.opm.Process
            || spade.vertex.opm.Process.typeValue.equals(vertex.type())
        ) {
            newVertex = new spade.vertex.prov.Activity();
        } else {
            logger.warning("Unhandled vertex type: " + vertex.getClass().getName());
            return null;
        }
        Map<String, String> annotationsCopy = vertex.getCopyOfAnnotations();
        for (Map.Entry<String, String> entry : annotationsCopy.entrySet()) {
        	if(entry.getKey().equals(OPMConstants.TYPE))
        		continue;
            newVertex.addAnnotation(entry.getKey(), entry.getValue());
        }
        return newVertex;
    }
}
