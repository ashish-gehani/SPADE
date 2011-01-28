/*
--------------------------------------------------------------------------------
SPADE - Support for Provenance Auditing in Distributed Environments.
Copyright (C) 2011 SRI International

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

import java.util.*;
import java.util.Set;

public class TestQuery {

    public static void main(String args[]) {
        Neo4jQuery t = new Neo4jQuery();
        t.initialize("db/testdb");

        try {
            Set<Vertex> testSet1 = t.getVertices("(pid:{* TO 11} NOT pidname:in?t) OR (startime:[* TO 1296236913060] AND cmdline:/sbin/*)");
            Iterator i1 = testSet1.iterator();
            Lineage lin1 = new Lineage();
            while (i1.hasNext()) {
                lin1.putVertex((Vertex) i1.next());
            }
            Lineage.exportDOT(lin1.getGraph(), "graph1.dot");
        } catch (Exception e) {
            e.printStackTrace();
        }

        t.shutdown();

    }
}
