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

public class Compress extends spade.utility.graph.codec.Compress {

    public Compress(final Properties properties) {
        super(properties);
    }

    @Override
    public Graph graph(final spade.core.Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("NULL graph");
        }

        return new Graph(graph);
    }

}
