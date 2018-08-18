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
import spade.core.Graph;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.AbstractStorage.SCAFFOLD_PATH;


public class InMemory extends Scaffold
{
    private static final int INITIAL_CACHE_CAPACITY = 500;
    private static final int DEFAULT_ENTRY_SIZE = 200;
    private Map<String, StringBuilder> scaffold = new HashMap<>(INITIAL_CACHE_CAPACITY);
    private Map<String, StringBuilder> childScaffold = new HashMap<>(INITIAL_CACHE_CAPACITY);
    private Map<String, StringBuilder> parentScaffold = new HashMap<>(INITIAL_CACHE_CAPACITY);
    private static final String LIST_SEPARATOR = "-";
    private static final String HASH_SEPARATOR = ",";

    private static Map<String, Set<String>> childList = new HashMap<>();
    private static Map<String, Set<String>> parentList = new HashMap<>();
    private static Logger logger = Logger.getLogger(InMemory.class.getName());

    /**
     * This method is invoked by the kernel to initialize the storage.
     *
     * @param arguments The directory path of the scaffold storage.
     * @return True if the storage was initialized successfully.
     */
    @Override
    public boolean initialize(String arguments)
    {
        logger.log(Level.INFO, "Scaffold initialized");
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
        // store scaffold
        String file_name = SCAFFOLD_PATH + "scaffold";
        File file = new File(file_name);
        try
        {
            FileOutputStream fos = new FileOutputStream(file);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(scaffold);
            oos.flush();
            oos.close();
            fos.close();
            logger.log(Level.INFO, "Scaffold persisted for future usage.");
        }
        catch(IOException ex)
        {
            logger.log(Level.WARNING, "Unable to store scaffold to file!", ex);
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

    public boolean insertEntry2(AbstractEdge incomingEdge)
    {
        try
        {
            String childVertexHash = incomingEdge.getChildVertex().bigHashCode();
            String parentVertexHash = incomingEdge.getParentVertex().bigHashCode();
            /*
                processing child vertex
            */
            StringBuilder childEntry = childScaffold.get(childVertexHash);
            if(childEntry == null)
            {
                childEntry = new StringBuilder(DEFAULT_ENTRY_SIZE);
            }
            childEntry.append(parentVertexHash);
            childScaffold.put(childVertexHash, childEntry);
            /*
                processing parent vertex
            */
            StringBuilder parentEntry = parentScaffold.get(parentVertexHash);
            if(parentEntry == null)
            {
                parentEntry = new StringBuilder(DEFAULT_ENTRY_SIZE);
            }
            parentEntry.append(childVertexHash);
            parentScaffold.put(parentVertexHash, parentEntry);
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Scaffold entry insertion not successful!", ex);
            return false;
        }

        return true;
    }

    public boolean insertEntry1(AbstractEdge incomingEdge)
    {
        try
        {
            String childVertexHash = incomingEdge.getChildVertex().bigHashCode();
            String parentVertexHash = incomingEdge.getParentVertex().bigHashCode();
            /*
                processing child vertex
            */
            StringBuilder childEntry = scaffold.get(childVertexHash);
            if(childEntry == null)
            {
                childEntry = new StringBuilder(DEFAULT_ENTRY_SIZE);
                childEntry.append(LIST_SEPARATOR).append(parentVertexHash);
                scaffold.put(childVertexHash, childEntry);
            }
            else
            {
                String[] neighborList = childEntry.toString().split(LIST_SEPARATOR, -1);
                String parentHashList = neighborList[1];
                if(parentHashList.isEmpty())
                {
                    childEntry.append(parentVertexHash);
                } else
                {
                    childEntry.append(HASH_SEPARATOR).append(parentVertexHash);
                }
            }

            /*
                processing parent vertex
            */
            StringBuilder parentEntry = scaffold.get(parentVertexHash);
            if(parentEntry == null)
            {
                parentEntry = new StringBuilder(DEFAULT_ENTRY_SIZE);
                parentEntry.append(childVertexHash).append(LIST_SEPARATOR);
                scaffold.put(parentVertexHash, parentEntry);
            }
            else
            {
                String[] neighborList = parentEntry.toString().split(LIST_SEPARATOR, -1);
                String childHashList = neighborList[0];
                if(childHashList.isEmpty())
                {
                    // insert childVertexHash in the beginning
                    parentEntry.insert(0, childVertexHash);
                } else
                {
                    // insert childVertexHash at the end of existing childList,
                    // or before the LIST_SEPARATOR
                    int separatorIndex = parentEntry.indexOf(LIST_SEPARATOR);
                    parentEntry.insert(separatorIndex, HASH_SEPARATOR + childVertexHash);
                }
            }
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Scaffold entry insertion not successful!", ex);
            return false;
        }

        return true;
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
        String childHash = incomingEdge.getChildVertex().bigHashCode();
        String parentHash = incomingEdge.getParentVertex().bigHashCode();
        try
        {
            /*
                processing child vertex
             */
            Set<String> childEntry = childList.get(childHash);
            if(childEntry == null)
            {
                childEntry = new HashSet<>();
                childList.put(childHash, childEntry);
            }
            childEntry.add(parentHash);
            /*
                processing parent vertex
             */
            Set<String> parentEntry = parentList.get(parentHash);
            if(parentEntry == null)
            {
                parentEntry = new HashSet<>();
                parentList.put(parentHash, parentEntry);
            }
            parentEntry.add(childHash);

            // stats computation
            long size = 1000000;
            if(childList.size() % size == 0)
            {
                Runtime runtime = Runtime.getRuntime();
                long usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024*1024);
                logger.log(Level.INFO, "childList size: " + childList.size() + ". JVM memory in use: "+ usedMemoryMB + " MB");
            }
            if(parentList.size() % size == 0)
            {
                Runtime runtime = Runtime.getRuntime();
                long usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024*1024);
                logger.log(Level.INFO, "parentList size: " + parentList.size() + ". JVM memory in use: "+ usedMemoryMB);
            }
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Error inserting scaffold entry!", ex);
            return false;
        }

        return true;
    }

    @Override
    public Graph queryManager(Map<String, List<String>> params)
    {
        return null;
    }
}

























