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

public class Compress extends spade.utility.graph.codec.Compress {

    public Compress(final Properties properties) {
        super(properties);
    }

    @Override
    public Graph graph(final spade.core.Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("NULL graph");
        }

        final Graph compressedGraph = new Graph();
        compressVertices(graph, compressedGraph);
        compressEdges(graph, compressedGraph);
        return compressedGraph;
    }

    private void compressVertices(final spade.core.Graph graph, final Graph compressedGraph) {
        final Map tokenMap = compressedGraph.getTokenMap();
        for (final AbstractVertex vertex : graph.vertexSet()) {
            if (vertex == null) {
                throw new IllegalArgumentException("NULL vertex in graph");
            }

            // A vertex may or may not carry an explicit 'id' annotation - getIdentifierForExport()
            // falls back to the content/reference hash when it doesn't, so it's used as the
            // token key rather than assuming either is always present.
            final Token id = token(tokenMap, vertex.getIdentifierForExport());
            final Vertex compressedVertex = compressVertex(vertex, tokenMap);
            compressedGraph.putVertex(id, compressedVertex);
        }
    }

    private void compressEdges(final spade.core.Graph graph, final Graph compressedGraph) {
        final Map tokenMap = compressedGraph.getTokenMap();
        for (final AbstractEdge edge : graph.edgeSet()) {
            if (edge == null) {
                throw new IllegalArgumentException("NULL edge in graph");
            }

            final Token id = token(tokenMap, edge.getIdentifierForExport());
            final Edge compressedEdge = compressEdge(edge, compressedGraph, tokenMap);
            compressedGraph.putEdge(id, compressedEdge);
        }
    }

    private Vertex compressVertex(final AbstractVertex vertex, final Map tokenMap) {
        final java.util.Map<Token, Token> annotations = new java.util.HashMap<>();
        for (final java.util.Map.Entry<String, String> annotation : vertex.getCopyOfAnnotations().entrySet()) {
            final Token key = token(tokenMap, annotation.getKey());
            final Token value = token(tokenMap, annotation.getValue());
            annotations.put(key, value);
        }

        return new Vertex(annotations);
    }

    private Edge compressEdge(final AbstractEdge edge, final Graph compressedGraph, final Map tokenMap) {
        if (edge.getChildVertex() == null) {
            throw new IllegalArgumentException("NULL child vertex in edge: " + edge);
        } else if (edge.getParentVertex() == null) {
            throw new IllegalArgumentException("NULL parent vertex in edge: " + edge);
        }

        // The edge's endpoints aren't guaranteed to already be in the graph's vertex set -
        // compress them here on first sight rather than assuming compressVertices already
        // covered them.
        final Token child = compressVertexIfAbsent(edge.getChildVertex(), compressedGraph, tokenMap);
        final Token parent = compressVertexIfAbsent(edge.getParentVertex(), compressedGraph, tokenMap);

        final java.util.Map<Token, Token> annotations = new java.util.HashMap<>();
        for (final java.util.Map.Entry<String, String> annotation : edge.getCopyOfAnnotations().entrySet()) {
            final Token key = token(tokenMap, annotation.getKey());
            final Token value = token(tokenMap, annotation.getValue());
            annotations.put(key, value);
        }

        return new Edge(child, parent, annotations);
    }

    private Token compressVertexIfAbsent(final AbstractVertex vertex, final Graph compressedGraph, final Map tokenMap) {
        final Token id = token(tokenMap, vertex.getIdentifierForExport());
        if (!compressedGraph.getVertices().containsKey(id)) {
            final Vertex compressedVertex = compressVertex(vertex, tokenMap);
            compressedGraph.putVertex(id, compressedVertex);
        }
        return id;
    }

    private Token token(final Map tokenMap, final String value) {
        return tokenMap.getToken(new Phrase(value));
    }

}
