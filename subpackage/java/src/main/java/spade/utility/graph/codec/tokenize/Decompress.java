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

package spade.utility.graph.codec.tokenize;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.utility.graph.codec.Properties;
import spade.utility.graph.codec.tokenize.token.Map;
import spade.utility.graph.codec.tokenize.token.Phrase;
import spade.utility.graph.codec.tokenize.token.Token;

public class Decompress extends spade.utility.graph.codec.Decompress {

    public Decompress(final Properties properties) {
        super(properties);
    }

    @Override
    public spade.core.Graph graph(final spade.utility.graph.codec.Graph compressedGraph) {
        if (compressedGraph == null) {
            throw new IllegalArgumentException("NULL graph");
        }

        final Graph tokenizedGraph = (Graph) compressedGraph;

        final spade.core.Graph graph = new spade.core.Graph();
        final java.util.Map<Token, AbstractVertex> vertices = decompressVertices(tokenizedGraph, graph);
        decompressEdges(tokenizedGraph, graph, vertices);
        return graph;
    }

    private java.util.Map<Token, AbstractVertex> decompressVertices(
            final Graph compressedGraph, final spade.core.Graph graph) {
        final Map tokenMap = compressedGraph.getTokenMap();

        final java.util.Map<Token, AbstractVertex> vertices = new java.util.HashMap<>();
        for (final java.util.Map.Entry<Token, Vertex> entry : compressedGraph.getVertices().entrySet()) {
            final Token id = entry.getKey();
            final Vertex compressedVertex = entry.getValue();
            if (id == null) {
                throw new IllegalArgumentException("NULL vertex token in graph");
            } else if (compressedVertex == null) {
                throw new IllegalArgumentException("NULL vertex in graph for token: " + id.getValue());
            }

            final AbstractVertex vertex = decompressVertex(id, compressedVertex, tokenMap);
            vertices.put(id, vertex);
            graph.putVertex(vertex);
        }

        return vertices;
    }

    private void decompressEdges(
            final Graph compressedGraph, final spade.core.Graph graph,
            final java.util.Map<Token, AbstractVertex> vertices) {
        final Map tokenMap = compressedGraph.getTokenMap();

        for (final java.util.Map.Entry<Token, Edge> entry : compressedGraph.getEdges().entrySet()) {
            final Token id = entry.getKey();
            final Edge compressedEdge = entry.getValue();
            if (id == null) {
                throw new IllegalArgumentException("NULL edge token in graph");
            } else if (compressedEdge == null) {
                throw new IllegalArgumentException("NULL edge in graph for token: " + id.getValue());
            }

            final AbstractEdge edge = decompressEdge(id, compressedEdge, tokenMap, vertices);
            graph.putEdge(edge);
        }
    }

    private AbstractVertex decompressVertex(final Token id, final Vertex vertex, final Map tokenMap) {
        // Materializing a vertex always rebuilds it as a reference vertex fixed to the
        // persisted identifier, regardless of whether the original was a content or
        // reference vertex - see AbstractVertex.md. Any 'id' annotation that was present
        // is restored below along with the rest of the annotations, same as it would
        // already be part of the original annotation map.
        final AbstractVertex decompressedVertex = new spade.core.Vertex(phrase(tokenMap, id));

        for (final java.util.Map.Entry<Token, Token> annotation : vertex.getAnnotations().entrySet()) {
            final String key = phrase(tokenMap, annotation.getKey());
            final String value = phrase(tokenMap, annotation.getValue());
            decompressedVertex.addAnnotation(key, value);
        }

        return decompressedVertex;
    }

    private AbstractEdge decompressEdge(
            final Token id, final Edge edge, final Map tokenMap,
            final java.util.Map<Token, AbstractVertex> vertices) {
        if (edge.getChild() == null) {
            throw new IllegalArgumentException("NULL child vertex token in edge for token: " + id.getValue());
        } else if (edge.getParent() == null) {
            throw new IllegalArgumentException("NULL parent vertex token in edge for token: " + id.getValue());
        }

        final AbstractVertex child = vertices.get(edge.getChild());
        final AbstractVertex parent = vertices.get(edge.getParent());
        if (child == null) {
            throw new IllegalArgumentException("Unresolved child vertex in edge for token: " + id.getValue());
        } else if (parent == null) {
            throw new IllegalArgumentException("Unresolved parent vertex in edge for token: " + id.getValue());
        }

        final AbstractEdge decompressedEdge = new spade.core.Edge(phrase(tokenMap, id), child, parent);

        for (final java.util.Map.Entry<Token, Token> annotation : edge.getAnnotations().entrySet()) {
            final String key = phrase(tokenMap, annotation.getKey());
            final String value = phrase(tokenMap, annotation.getValue());
            decompressedEdge.addAnnotation(key, value);
        }

        return decompressedEdge;
    }

    private String phrase(final Map tokenMap, final Token token) {
        if (token == null) {
            throw new IllegalArgumentException("NULL token");
        }

        final Phrase phrase = tokenMap.getPhrase(token);
        if (phrase == null) {
            throw new IllegalArgumentException("Unresolved token: " + token.getValue());
        }

        return phrase.getValue();
    }

}
