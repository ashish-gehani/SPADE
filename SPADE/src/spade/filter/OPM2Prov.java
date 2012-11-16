/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package spade.filter;

import java.util.Map;
import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.vertex.prov.Activity;
import spade.vertex.prov.Agent;
import spade.vertex.prov.Entity;

/**
 *
 * @author dawood
 */
public class OPM2Prov extends AbstractFilter {

    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        putInNextFilter(createProvVertex(incomingVertex));
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
        AbstractVertex sourceVertex = createProvVertex(incomingEdge.getSourceVertex());
        AbstractVertex destinationVertex = createProvVertex(incomingEdge.getDestinationVertex());
        AbstractEdge newEdge = null;
        if (incomingEdge instanceof spade.edge.opm.Used) {
            newEdge = new spade.edge.prov.Used((Activity) sourceVertex, (Entity) destinationVertex);
        } else if (incomingEdge instanceof spade.edge.opm.WasControlledBy) {
            newEdge = new spade.edge.prov.WasAssociatedWith((Activity) sourceVertex, (Agent) destinationVertex);
        } else if (incomingEdge instanceof spade.edge.opm.WasDerivedFrom) {
            newEdge = new spade.edge.prov.WasDerivedFrom((Entity) sourceVertex, (Entity) destinationVertex);
        } else if (incomingEdge instanceof spade.edge.opm.WasGeneratedBy) {
            newEdge = new spade.edge.prov.WasGeneratedBy((Entity) sourceVertex, (Activity) destinationVertex);
        } else if (incomingEdge instanceof spade.edge.opm.WasTriggeredBy) {
            newEdge = new spade.edge.prov.WasInformedBy((Activity) sourceVertex, (Activity) destinationVertex);
        }
        for (Map.Entry<String, String> entry : incomingEdge.getAnnotations().entrySet()) {
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
            newVertex.addAnnotation(entry.getKey(), entry.getValue());
        }
        return newVertex;
    }
}
