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

public class GraphFinesse extends AbstractFilter {

    // 'ancestors' maps a given vertex to its ancestors.
    private final Map<AbstractVertex, Set<AbstractVertex>> ancestors;
    // 'descendants' maps a given vertex to its descendants. We use this to update
    // the ancestors map when an edge is added.
    private final Map<AbstractVertex, Set<AbstractVertex>> descendants;
    // 'passedVertices' maps a given vertex to a boolean indicating whether it has
    // been passed to the next filter or not.
    private final Map<AbstractVertex, Boolean> passedVertices;
    // 'passedEdges' maps a given edge to a boolean indicating whether it has been 
    // passed to the next filter or not.
    private final Map<AbstractEdge, Boolean> passedEdges;
    // 'vertexVersions' maps a given vertex to it's version number.
    private final Map<AbstractVertex, Integer> vertexVersions;
    // 'vertexStrings' maps a string representation of a vertex to the actual vertex.
    private final Map<String, AbstractVertex> vertexStrings;
    private final int initialVersion = 0;
    private final String versionAnnotation = "GFVersion";

    public GraphFinesse() {
        ancestors = new HashMap<>();
        descendants = new HashMap<>();
        passedVertices = new HashMap<>();
        passedEdges = new HashMap<>();
        vertexVersions = new HashMap<>();
        vertexStrings = new HashMap<>();
    }

    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        if (vertexStrings.containsKey(incomingVertex.toString())) {
            // We've already seen this vertex.
            return;
        }
        AbstractVertex copy = incomingVertex.copyAsVertex();
        copy.addAnnotation(versionAnnotation, Integer.toString(initialVersion));
        // For comparison purposes, we store the string representation of the 
        // original vertex since the filter may change the version.
        vertexStrings.put(incomingVertex.toString(), copy);
        passedVertices.put(copy, Boolean.FALSE);
        vertexVersions.put(copy, initialVersion);
    }

    // Given an incoming edge A->B, GF uses the following rules:
    // 1) If A->B already exists, then it is a duplicate --> discard.
    // 2) If A exists in the ancestors of B, then this edge will create a
    //    cycle --> create a new version A' and add edge A'->B.
    // 3) If rules (1) and (2) are not met, then add the edge as a normal edge.
    @Override
    public void putEdge(AbstractEdge edge) {
        AbstractVertex source = vertexStrings.get(edge.getChildVertex().toString());
        AbstractVertex destination = vertexStrings.get(edge.getParentVertex().toString());
        AbstractEdge copyEdge = copyEdge(edge);
        copyEdge.setChildVertex(source);
        copyEdge.setParentVertex(destination);

        // Check for rule 1
        if (passedEdges.containsKey(copyEdge)) // Check for rule 2
        {
            return;
        }

        // Check for rule 2
        if (ancestors.containsKey(destination) && ancestors.get(destination).contains(source)) {
            // Rule 2 is hit, update the vertex number.
            int newVersion = vertexVersions.get(source) + 1;
            source.removeAnnotation(versionAnnotation);
            source.addAnnotation(versionAnnotation, Integer.toString(newVersion));
            vertexVersions.put(source, newVersion);
        }

        updateAncestors(source, destination);
        updateDescendants(source, destination);

        // Pass the edges and vertices.
        checkVertexCache(source);
        checkVertexCache(destination);
        putInNextFilter(copyEdge);
    }

    private void updateAncestors(AbstractVertex source, AbstractVertex destination) {
        // Update the ancestors for this vertex and its descendants.
        if (!ancestors.containsKey(source)) {
            HashSet<AbstractVertex> vertexAncestors = new HashSet<>();
            vertexAncestors.add(destination);
            ancestors.put(source, vertexAncestors);
        }
        // Update the ancestors for this vertex by adding all the ancestors
        // of the destination vertex.
        ancestors.get(source).addAll(ancestors.get(destination));
        // Update the ancestors of the descendants of this vertex since this
        // edge may have created new descendants.
        for (AbstractVertex descendant : descendants.get(source)) {
            if (ancestors.containsKey(descendant)) {
                ancestors.get(descendant).add(source);
                ancestors.get(descendant).addAll(ancestors.get(source));
            }
        }
    }

    private void updateDescendants(AbstractVertex source, AbstractVertex destination) {
        // Update the descendants for this vertex and its ancestors.
        if (!descendants.containsKey(destination)) {
            HashSet<AbstractVertex> vertexDescendants = new HashSet<>();
            vertexDescendants.add(source);
            descendants.put(destination, vertexDescendants);
        }
        // Update the descendants for this vertex by adding all the descendants
        // of the source vertex.
        descendants.get(destination).addAll(descendants.get(source));
        // Update the descendants of the ancestors of this vertex since this
        // edge may have created new ancestors.
        for (AbstractVertex ancestor : ancestors.get(source)) {
            if (descendants.containsKey(ancestor)) {
                descendants.get(ancestor).add(source);
                descendants.get(ancestor).addAll(descendants.get(source));
            }
        }
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
