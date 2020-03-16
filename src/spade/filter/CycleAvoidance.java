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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.core.Edge;

public class CycleAvoidance extends AbstractFilter {

    // 'ancestors' maps a given vertex to its ancestors.
    private final Map<AbstractVertex, Set<AbstractVertex>> ancestors;
    // 'passedVertices' maps a given vertex to a boolean indicating whether it has
    // been passed to the next filter or not.
    private final Map<AbstractVertex, Boolean> passedVertices;
    private final int initialVersion = 0;
    private final String versionAnnotation = "Version";

    public CycleAvoidance() {
        ancestors = new HashMap<>();
        passedVertices = new HashMap<>();
    }

    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        if (passedVertices.containsKey(incomingVertex)) {
            // We've already seen this vertex.
            return;
        }
        if (incomingVertex.getAnnotation(versionAnnotation) == null) {
            incomingVertex.addAnnotation(versionAnnotation, Integer.toString(initialVersion));
        }
        passedVertices.put(incomingVertex, Boolean.FALSE);
    }

    // Given an incoming edge A->B(i), CA uses the following rules:
    // 1) If no B exists, then add the edge.
    // 2) If B(j) exists and j==i, then discard the edge.
    // 3) If B(j) exists and j>i, then discard the edge.
    // 3) If B(j) exists and j<i, then create a new A' and add A'->B(i).
    @Override
    public void putEdge(AbstractEdge edge) {
        AbstractVertex source = edge.getChildVertex();
        AbstractVertex destination = edge.getParentVertex();
        AbstractEdge copyEdge = copyEdge(edge);
        copyEdge.setChildVertex(source);
        copyEdge.setParentVertex(destination);

        if (!ancestors.containsKey(source)) {
            HashSet<AbstractVertex> vertexAncestors = new HashSet<>();
            ancestors.put(source, vertexAncestors);
        }

        Map<String, String> currentAnnotations = new HashMap<>();
        currentAnnotations.putAll(destination.getCopyOfAnnotations());
        int currentVersion = Integer.parseInt(currentAnnotations.remove(versionAnnotation));
        // Look for ancestor vertex.
        for (AbstractVertex ancestor : ancestors.get(source)) {
            Map<String, String> annotations = new HashMap<>();
            annotations.putAll(ancestor.getCopyOfAnnotations());
            int existingVersion = Integer.parseInt(annotations.remove(versionAnnotation));
            if (currentAnnotations.equals(annotations)) {
                if (currentVersion == existingVersion || currentVersion < existingVersion) {
                    return;
                } else {
                    currentVersion++;
                    destination = destination.copyAsVertex();
                    destination.addAnnotation(versionAnnotation, Integer.toString(currentVersion));
                    copyEdge.setParentVertex(destination);
                }
                break;
            }
        }

        checkVertexCache(source);
        checkVertexCache(destination);
        putInNextFilter(copyEdge);
    }

    private void checkVertexCache(AbstractVertex vertex) {
        if (Objects.equals(passedVertices.get(vertex), Boolean.FALSE)) {
            putInNextFilter(vertex);
            passedVertices.put(vertex, Boolean.TRUE);
        }
    }

    private AbstractEdge copyEdge(AbstractEdge edge) {
        AbstractEdge copy = new Edge(edge.getChildVertex(), edge.getParentVertex());
        copy.addAnnotations(edge.getCopyOfAnnotations());
        return copy;
    }
}
