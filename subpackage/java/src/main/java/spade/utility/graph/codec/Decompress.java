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

package spade.utility.graph.codec;

public abstract class Decompress {

    protected final Properties properties;

    public Decompress(final Properties properties) {
        this.properties = properties;
    }

    public abstract spade.core.Graph graph(final Graph compressedGraph);

    public static Decompress create(final Properties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("NULL properties");
        } else if (properties.getType() == null) {
            throw new IllegalArgumentException("NULL codec type in properties: " + properties);
        }

        switch (properties.getType()) {
            case TOKENIZE:
                return new spade.utility.graph.codec.tokenize.Decompress(properties);
            case UNCOMPRESSED:
                return new spade.utility.graph.codec.uncompressed.Decompress(properties);
            default:
                throw new IllegalArgumentException("Unknown codec type: " + properties.getType());
        }
    }

}
