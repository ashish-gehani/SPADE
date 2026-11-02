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

package spade.external.com.somethingsimilar.opposite_of_a_bloom_filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.somethingsimilar.opposite_of_a_bloom_filter.ByteArrayFilter;
import org.junit.jupiter.api.Test;

import spade.core.Edge;
import spade.core.Vertex;

public class ByteArrayFilterTest {

    @Test
    public void recognizesRepeatedVertexAndEdgeHashes() {
        final ByteArrayFilter filter = new ByteArrayFilter(100);
        final Vertex child = new Vertex();
        child.addAnnotation("name", "child");
        final Vertex parent = new Vertex();
        parent.addAnnotation("name", "parent");
        final Edge edge = new Edge(child, parent);

        // Match Deduplicate's construction and containsAndAdd usage.
        assertFalse(filter.containsAndAdd(child.bigHashCodeBytes()));
        assertTrue(filter.containsAndAdd(child.bigHashCodeBytes()));
        assertFalse(filter.containsAndAdd(edge.bigHashCodeBytes()));
        assertTrue(filter.containsAndAdd(edge.bigHashCodeBytes()));
    }
}
