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
package spade.storage;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sleepycat.bind.EntryBinding;
import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;


public class BerkeleyDB extends AbstractStorage
{
    private static Environment myDbEnvironment = null;
    private static Database myDatabase = null;
    private static Database vertexDatabase = null;
    private static Database edgeDatabase = null;
    private static String directoryPath = null;

    /**
     * This method is invoked by the kernel to initialize the storage.
     *
     * @param arguments The arguments with which this storage is to be
     *                  initialized.
     * @return True if the storage was initialized successfully.
     */
    @Override
    public boolean initialize(String arguments)
    {
        directoryPath = arguments;
        myDbEnvironment = null;
        myDatabase = null;
        try
        {
            EnvironmentConfig envConfig = new EnvironmentConfig();
            envConfig.setAllowCreate(true);
            envConfig.setTransactional(true);
            myDbEnvironment = new Environment(new File(directoryPath), envConfig);

            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setAllowCreate(true);
            myDatabase = myDbEnvironment.openDatabase(null, "spade_berkeleyDb", dbConfig);

            // databases to store class information
            vertexDatabase = myDbEnvironment.openDatabase(null, "spade_vertexDb", dbConfig);
            edgeDatabase = myDbEnvironment.openDatabase(null, "spade_edgeDb", dbConfig);

            return true;

        }
        catch(DatabaseException ex)
        {
            Logger.getLogger(BerkeleyDB.class.getName()).log(Level.WARNING, null, ex);
        }

        return false;
    }

    /**
     * This method is invoked by the kernel to shut down the storage.
     *
     * @return True if the storage was shut down successfully.
     */
    @Override
    public boolean shutdown()
    {
        try
        {
            if (myDatabase != null)
                myDatabase.close();
            if (vertexDatabase != null)
                vertexDatabase.close();
            if(edgeDatabase != null)
                edgeDatabase.close();
            if (myDbEnvironment != null)
                myDbEnvironment.close();
            return true;
        }
        catch(DatabaseException ex)
        {
            Logger.getLogger(BerkeleyDB.class.getName()).log(Level.WARNING, null, ex);
        }

        return false;
    }

    /**
     * This function inserts the given edge into the underlying storage(s) and
     * updates the cache(s) accordingly.
     *
     * @param incomingEdge edge to insert into the storage
     * @return returns true if the insertion is successful. Insertion is considered
     * not successful if the edge is already present in the storage.
     */
    @Override
    public boolean storeEdge(AbstractEdge incomingEdge)
    {
        String hash = incomingEdge.getChildVertex().bigHashCode() + incomingEdge.getParentVertex().bigHashCode();
        try
        {
            // Instantiate class catalog
            StoredClassCatalog edgeCatalog = new StoredClassCatalog(edgeDatabase);
            // Create the binding
            EntryBinding<AbstractEdge> edgeBinding = new SerialBinding<>(edgeCatalog, AbstractEdge.class);
            // Create DatabaseEntry for the key
            DatabaseEntry key = new DatabaseEntry(hash.getBytes("UTF-8"));
            // Create the DatabaseEntry for the data.
            DatabaseEntry data = new DatabaseEntry();
            edgeBinding.objectToEntry(incomingEdge, data);
            // Insert it in database
            myDatabase.put(null, key, data);

            return true;
        }
        catch (UnsupportedEncodingException ex)
        {
            Logger.getLogger(BerkeleyDB.class.getName()).log(Level.WARNING, null, ex);
        }

        return false;
    }

    /**
     * This function inserts the given vertex into the underlying storage(s) and
     * updates the cache(s) accordingly.
     *
     * @param incomingVertex vertex to insert into the storage
     * @return returns true if the insertion is successful. Insertion is considered
     * not successful if the vertex is already present in the storage.
     */
    @Override
    public boolean storeVertex(AbstractVertex incomingVertex)
    {
        try
        {
            // Instantiate class catalog
            StoredClassCatalog vertexCatalog = new StoredClassCatalog(vertexDatabase);
            // Create the binding
            EntryBinding<AbstractVertex> vertexBinding = new SerialBinding<>(vertexCatalog, AbstractVertex.class);
            // Create DatabaseEntry for the key
            DatabaseEntry key = new DatabaseEntry(incomingVertex.bigHashCode().getBytes("UTF-8"));
            // Create the DatabaseEntry for the data.
            DatabaseEntry data = new DatabaseEntry();
            vertexBinding.objectToEntry(incomingVertex, data);
            // Insert it in database
            myDatabase.put(null, key, data);

            return true;
        }
        catch (UnsupportedEncodingException ex)
        {
            Logger.getLogger(BerkeleyDB.class.getName()).log(Level.WARNING, null, ex);
        }

        return false;
    }

    @Override
    public ResultSet executeQuery(String query)
    {
        return null;
    }
}
