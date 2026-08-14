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

package spade.utility.graph.codec.uncompressed;

import spade.utility.graph.codec.Properties;

public class Decompress extends spade.utility.graph.codec.Decompress {

    public Decompress(final Properties properties) {
        super(properties);
    }

    @Override
    public spade.core.Graph graph(final spade.utility.graph.codec.Graph compressedGraph) {
        if (compressedGraph == null) {
            throw new IllegalArgumentException("NULL graph");
        }

        final Graph uncompressedGraph = (Graph) compressedGraph;
        return uncompressedGraph.getGraph();
    }

}
