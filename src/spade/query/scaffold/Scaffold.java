/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2017 SRI International
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
package spade.query.scaffold;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;
import spade.core.Vertex;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * @author raza
 */
public abstract class Scaffold
{
    protected static final String PARENTS = "parents";
    protected static final String CHILDREN = "children";
    protected String directoryPath;

    public static int GLOBAL_TX_SIZE = 1000;
    protected static int globalTxCount = 0;
    protected int MAX_WAIT_TIME_BEFORE_FLUSH = 15000; // ms
    protected Date lastFlushTime;

    public void setGLOBAL_TX_SIZE(int globalTxSize)
    {
        GLOBAL_TX_SIZE = globalTxSize;
    }

    /**
     * This method is invoked by the kernel to initialize the storage.
     *
     * @param arguments The directory path of the scaffold storage.
     * @return True if the storage was initialized successfully.
     */
    public abstract boolean initialize(String arguments);

    protected abstract void globalTxCheckin(boolean forcedFlush);

    /**
     * This method is invoked by the AbstractStorage to shut down the storage.
     *
     * @return True if scaffold was shut down successfully.
     */
    public abstract boolean shutdown();

    public abstract Set<String> getChildren(String parentHash);

    public abstract Set<String> getParents(String childHash);

    public abstract Set<String> getNeighbors(String hash);

    public abstract Map<String, Set<String>> getLineage(String hash, String direction, int maxDepth);

    public abstract Map<String, Set<String>> getPaths(String source_hash, String destination_hash, int maxLength);

    /**
     * This function inserts hashes of the end vertices of given edge
     * into the scaffold storage.
     *
     * @param incomingEdge edge whose end points to insert into the storage
     * @return returns true if the insertion is successful. Insertion is considered
     * not successful if the vertex is already present in the storage.
     */
    public abstract boolean insertEntry(AbstractEdge incomingEdge);

    public abstract Graph queryManager(Map<String, List<String>> params);

    public static void main(String args[])
    {
        // testing code
        Scaffold scaffold = new BerkeleyDB();
        scaffold.initialize("");

        AbstractVertex v1 = new Vertex();
        v1.addAnnotation("name", "v1");

        AbstractVertex v2 = new Vertex();
        v2.addAnnotation("name", "v2");

        AbstractVertex v3 = new Vertex();
        v3.addAnnotation("name", "v3");

        AbstractVertex v4 = new Vertex();
        v4.addAnnotation("name", "v4");

        AbstractEdge e1 = new Edge(v1, v2);
        e1.addAnnotation("name", "e1");

        AbstractEdge e2 = new Edge(v2, v3);
        e2.addAnnotation("name", "e2");

        AbstractEdge e3 = new Edge(v1, v3);
        e3.addAnnotation("name", "e3");

        AbstractEdge e4 = new Edge(v2, v4);
        v4.addAnnotation("name", "e4");

        AbstractEdge e5 = new Edge(v1, v4);
        e5.addAnnotation("name", "e5");

        AbstractEdge e6 = new Edge(v3, v2);
        e6.addAnnotation("name", "e6");

        AbstractEdge e7 = new Edge(v3, v4);
        e7.addAnnotation("name", "e7");

        AbstractEdge e8 = new Edge(v4, v2);
        e8.addAnnotation("name", "e8");

        scaffold.insertEntry(e1);
        scaffold.insertEntry(e2);
        scaffold.insertEntry(e3);
        scaffold.insertEntry(e4);
        scaffold.insertEntry(e5);
        scaffold.insertEntry(e6);
        scaffold.insertEntry(e7);
        scaffold.insertEntry(e8);

        System.out.println("v1: " + v1.bigHashCode());
        System.out.println("v2: " + v2.bigHashCode());
        System.out.println("v3: " + v3.bigHashCode());
        System.out.println("v4: " + v4.bigHashCode());

        System.out.println(scaffold.getPaths(v1.bigHashCode(), v2.bigHashCode(), 1));
    }
}


