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
package spade.storage;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.AbstractStorage;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.RelationshipIndex;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.helpers.collection.MapUtil;

public class SketchManager extends AbstractStorage {

    private final int TRANSACTION_LIMIT = 10000;
    private GraphDatabaseService graphDb;
    private Index<Node> vertexIndex;
    private RelationshipIndex edgeIndex;
    private Transaction transaction;
    private int transactionCount;

    public enum MyRelationshipTypes implements RelationshipType {

        EDGE
    }

    // This method initializes the neo4j database at the path given as the argument.
    @Override
    public boolean initialize(String arguments) {
        try {
            graphDb = new EmbeddedGraphDatabase(arguments);
            transactionCount = 0;
            vertexIndex = graphDb.index().forNodes("vertexIndex", MapUtil.stringMap("provider", "lucene", "type", "fulltext"));
            edgeIndex = graphDb.index().forRelationships("edgeIndex", MapUtil.stringMap("provider", "lucene", "type", "fulltext"));
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    // This method is triggered when a vertex is received.
    @Override
    public boolean putVertex(AbstractVertex incomingVertex) {
        if (transactionCount == 0) {
            transaction = graphDb.beginTx();
        }

        // Do necessary work here

        checkTransactionCount();
        return true;
    }

    // This method is triggered when an edge is received.
    @Override
    public boolean putEdge(AbstractEdge incomingEdge) {
        if (transactionCount == 0) {
            transaction = graphDb.beginTx();
        }

        // Do necessary work here

        checkTransactionCount();
        return true;
    }

    // This method is used to batch transactions and commit them when the limit is reached.
    private void checkTransactionCount() {
        transactionCount++;
        if (transactionCount == TRANSACTION_LIMIT) {
            transactionCount = 0;
            try {
                transaction.success();
                transaction.finish();
            } catch (Exception exception) {
                // Do nothing
            }
        }
    }

    // This method is triggered by the kernel to flush transactions before any query
    // to ensure that the data in memory has been committed to persistent storage.
    @Override
    public boolean flushTransactions() {
        if (transaction != null) {
            transaction.success();
            transaction.finish();
            transaction = graphDb.beginTx();
            transactionCount = 0;
        }
        return true;
    }

    // This method commits all pending transactions and cleanly shuts down the database.
    @Override
    public boolean shutdown() {
        if (transaction != null) {
            transaction.success();
            transaction.finish();
        }
        graphDb.shutdown();
        return true;
    }

}
