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

import redis.clients.jedis.Jedis;
import spade.core.AbstractEdge;
import spade.core.Graph;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Redis extends Scaffold
{
    private static Logger logger = Logger.getLogger(Redis.class.getName());
    private static Jedis childScaffold;
    private static Jedis parentScaffold;

    /**
     * This method is invoked by the kernel to initialize the storage.
     *
     * @param arguments The directory path of the scaffold storage.
     * @return True if the storage was initialized successfully.
     */
    @Override
    public boolean initialize(String arguments)
    {
        try
        {
            childScaffold = new Jedis("localhost");
            parentScaffold = new Jedis("localhost");

            childScaffold.configSet("dir", "/space/spade/tc/SPADE_v3/db/scaffold/");
            childScaffold.configSet("maxmemory", "5GB" );
            childScaffold.configSet("maxmemory-policy", "allkeys-lfu" );

            logger.log(Level.INFO, "Scaffold initialized successfully!");
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Unable to initialize scaffold!", ex);
            return false;
        }

        return true;
    }

    @Override
    protected void globalTxCheckin(boolean forcedFlush)
    {

    }

    /**
     * This method is invoked by the AbstractStorage to shut down the storage.
     *
     * @return True if scaffold was shut down successfully.
     */
    @Override
    public boolean shutdown()
    {
        try
        {
            childScaffold.close();
            parentScaffold.close();
            logger.log(Level.INFO, "Scaffold closed successfully!");
        }
        catch(Exception ex)
        {
            logger.log(Level.WARNING, "Error shutting down scaffold!");
            return false;
        }

        return true;
    }

    @Override
    public Set<String> getChildren(String parentHash)
    {
        return null;
    }

    @Override
    public Set<String> getParents(String childHash)
    {
        return null;
    }

    @Override
    public Set<String> getNeighbors(String hash)
    {
        return null;
    }

    @Override
    public Map<String, Set<String>> getLineage(String hash, String direction, int maxDepth)
    {
        return null;
    }

    @Override
    public Map<String, Set<String>> getPaths(String source_hash, String destination_hash, int maxLength)
    {
        return null;
    }

    /**
     * This function inserts hashes of the end vertices of given edge
     * into the scaffold storage.
     *
     * @param incomingEdge edge whose end points to insert into the storage
     * @return returns true if the insertion is successful. Insertion is considered
     * not successful if the vertex is already present in the storage.
     */
    @Override
    public boolean insertEntry(AbstractEdge incomingEdge)
    {
        try
        {
            String childVertexHash = incomingEdge.getChildVertex().bigHashCode();
            String parentVertexHash = incomingEdge.getParentVertex().bigHashCode();
            childScaffold.sadd(childVertexHash, parentVertexHash);
            parentScaffold.sadd(parentVertexHash, childVertexHash);
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Scaffold entry insertion not successful!", ex);
            return false;
        }
        return true;
    }

    @Override
    public Graph queryManager(Map<String, List<String>> params)
    {
        return null;
    }

    public static void main(String[] args)
    {
    }
}
