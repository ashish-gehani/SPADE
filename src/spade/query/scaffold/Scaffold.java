package spade.query.scaffold;

import com.sleepycat.bind.EntryBinding;
import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import spade.core.AbstractEdge;
import spade.core.AbstractQuery;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;
import spade.core.Vertex;
import spade.query.sql.postgresql.PostgreSQL;

import java.io.File;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.AbstractStorage.PRIMARY_KEY;
import static spade.query.sql.postgresql.PostgreSQL.EDGE_TABLE;
import static spade.query.sql.postgresql.PostgreSQL.VERTEX_TABLE;


/**
 * @author raza
 */
public class Scaffold extends AbstractQuery
{
    public static long serial_number = 1;
    public static long start_time;
    private static Environment scaffoldDbEnvironment = null;
    private static Database scaffoldDatabase = null;
    private static Database neighborDatabase = null;
    private static final String PARENTS = "parents";
    private static final String CHILDREN = "children";

    /**
     * This method is invoked by the kernel to initialize the storage.
     *
     * @param arguments The arguments with which this storage is to be
     *                  initialized.
     * @return True if the storage was initialized successfully.
     */
    public boolean initialize(String arguments)
    {
        String directoryPath = arguments;
        scaffoldDbEnvironment = null;
        scaffoldDatabase = null;
        try
        {
            EnvironmentConfig envConfig = new EnvironmentConfig();
            envConfig.setAllowCreate(true);
            envConfig.setTransactional(true);
            scaffoldDbEnvironment = new Environment(new File(directoryPath), envConfig);

            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setAllowCreate(true);
            scaffoldDatabase = scaffoldDbEnvironment.openDatabase(null, "scaffold_berkeleydb", dbConfig);

            // databases to store class information
            neighborDatabase = scaffoldDbEnvironment.openDatabase(null, "neighbor_berkeleydb", dbConfig);

            start_time = System.nanoTime();
            return true;

        }
        catch(DatabaseException ex)
        {
            Logger.getLogger(Scaffold.class.getName()).log(Level.WARNING, null, ex);
        }

        return false;
    }

    /**
     * This method is invoked by the kernel to shut down the storage.
     *
     * @return True if the storage was shut down successfully.
     */
    public boolean shutdown()
    {
        try
        {
            if (scaffoldDatabase != null)
                scaffoldDatabase.close();
            if (scaffoldDbEnvironment != null)
                scaffoldDbEnvironment.close();
            return true;
        }
        catch(DatabaseException ex)
        {
            Logger.getLogger(Scaffold.class.getName()).log(Level.SEVERE, "Database closure error!", ex);
        }

        return false;
    }

    public Set<String> getChildren(String parentHash)
    {
        try
        {
            // Instantiate class catalog
            StoredClassCatalog neighborCatalog = new StoredClassCatalog(neighborDatabase);
            // Create the binding
            EntryBinding<Neighbors> neighborBinding = new SerialBinding<>(neighborCatalog, Neighbors.class);
            // Create DatabaseEntry for the key
            DatabaseEntry key = new DatabaseEntry(parentHash.getBytes("UTF-8"));
            // Create the DatabaseEntry for the data.
            DatabaseEntry data = new DatabaseEntry();
            // query database to get the key-value
            OperationStatus operationStatus = scaffoldDatabase.get(null, key, data, LockMode.DEFAULT);
            if(operationStatus != OperationStatus.NOTFOUND)
            {
                Neighbors neighbors = neighborBinding.entryToObject(data);
                return neighbors.children;
            }
        }
        catch (UnsupportedEncodingException ex)
        {
            Logger.getLogger(Scaffold.class.getName()).log(Level.SEVERE, "Scaffold entry insertion error!", ex);
        }
        return null;
    }

    public Set<String> getParents(String childHash)
    {
        try
        {
            // Instantiate class catalog
            StoredClassCatalog neighborCatalog = new StoredClassCatalog(neighborDatabase);
            // Create the binding
            EntryBinding<Neighbors> neighborBinding = new SerialBinding<>(neighborCatalog, Neighbors.class);
            // Create DatabaseEntry for the key
            DatabaseEntry key = new DatabaseEntry(childHash.getBytes("UTF-8"));
            // Create the DatabaseEntry for the data.
            DatabaseEntry data = new DatabaseEntry();
            // query database to get the key-value
            OperationStatus operationStatus = scaffoldDatabase.get(null, key, data, LockMode.DEFAULT);
            if(operationStatus != OperationStatus.NOTFOUND)
            {
                Neighbors neighbors = neighborBinding.entryToObject(data);
                return neighbors.parents;
            }
        }
        catch (UnsupportedEncodingException ex)
        {
            Logger.getLogger(Scaffold.class.getName()).log(Level.SEVERE, "Scaffold entry insertion error!", ex);
        }
        return null;
    }

    public Set<String> getNeighbors(String hash)
    {
        Set<String> neighbors = getChildren(hash);
        neighbors.addAll(getParents(hash));
        return neighbors;
    }

    public Map<String, Set<String>> getLineage(String hash, String direction, int maxDepth)
    {
        try
        {
            // Instantiate class catalog
            StoredClassCatalog neighborCatalog = new StoredClassCatalog(neighborDatabase);
            // Create the binding
            EntryBinding<Neighbors> neighborBinding = new SerialBinding<>(neighborCatalog, Neighbors.class);
            // Create DatabaseEntry for the key
            DatabaseEntry key = new DatabaseEntry(hash.getBytes("UTF-8"));
            // Create the DatabaseEntry for the data.
            DatabaseEntry data = new DatabaseEntry();
            // query database to get the key-value
            OperationStatus operationStatus = scaffoldDatabase.get(null, key, data, LockMode.DEFAULT);
            List<String> ancestors = new LinkedList<>();
            Map<String, Set<String>> lineageMap = new HashMap<>();
            if(operationStatus != OperationStatus.NOTFOUND)
            {
                ancestors.add(hash);
                int i = 0;
                int depth = 0;
                while(i < ancestors.size())
                {
                    if(depth >= maxDepth)
                        break;
                    String current_hash = ancestors.get(i);
                    Set<String> neighbors = null;
                    if(direction.startsWith(DIRECTION_ANCESTORS))
                        neighbors = getParents(current_hash);
                    else if(direction.startsWith(DIRECTION_DESCENDANTS))
                        neighbors = getChildren(current_hash);

                    if(neighbors != null)
                    {
                        lineageMap.put(current_hash, neighbors);
                        ancestors.addAll(neighbors);
                    }

                    i++;
                    depth++;
                }

                return lineageMap;
            }
        }
        catch(UnsupportedEncodingException ex)
        {
            Logger.getLogger(Scaffold.class.getName()).log(Level.SEVERE, "Scaffold Get Lineage error!", ex);
        }

        return null;
    }

    public Map<String, Set<String>> getPaths(String source_hash, String destination_hash, int maxLength)
    {

        Map<String, Set<String>> lineageUp = getLineage(source_hash, DIRECTION_ANCESTORS, maxLength);
        Map<String, Set<String>> lineageDown = getLineage(destination_hash, DIRECTION_DESCENDANTS, maxLength);
        Map<String, Set<String>> paths = new HashMap<>();
        Set<String> keys = lineageUp.keySet();
        keys.retainAll(lineageDown.keySet());

        for(String key: keys)
        {
            Set<String> pathEntry = paths.get(key);
            if(pathEntry == null)
            {
                pathEntry = new HashSet<>();
            }
            pathEntry.addAll(lineageUp.get(key));
            pathEntry.addAll(lineageDown.get(key));
        }

        return paths;
    }


    /**
     * This function inserts hashes of the end vertices of given edge
     * into the scaffold storage.
     *
     * @param incomingEdge edge whose end points to insert into the storage
     * @return returns true if the insertion is successful. Insertion is considered
     * not successful if the vertex is already present in the storage.
     */
    public boolean insertEntry(AbstractEdge incomingEdge)
    {
        AbstractVertex childVertex = incomingEdge.getChildVertex();
        AbstractVertex parentVertex = incomingEdge.getParentVertex();
        String childHash = childVertex.bigHashCode();
        String parentHash = parentVertex.bigHashCode();
        try
        {
            // Instantiate class catalog
            StoredClassCatalog neighborCatalog = new StoredClassCatalog(neighborDatabase);
            // Create the binding
            EntryBinding<Neighbors> neighborBinding = new SerialBinding<>(neighborCatalog, Neighbors.class);
            // Create DatabaseEntry for the key
            DatabaseEntry key = new DatabaseEntry(childHash.getBytes("UTF-8"));
            // Create the DatabaseEntry for the data.
            DatabaseEntry data = new DatabaseEntry();
            // query database to get the key-value
            OperationStatus operationStatus = scaffoldDatabase.get(null, key, data, LockMode.DEFAULT);
            addItem(operationStatus, PARENTS , key, data, neighborBinding, parentHash);

            // now do the reverse too
            key = new DatabaseEntry(parentHash.getBytes("UTF-8"));
            data = new DatabaseEntry();
            operationStatus = scaffoldDatabase.get(null, key, data, LockMode.DEFAULT);
            addItem(operationStatus, CHILDREN, key, data, neighborBinding, childHash);

            // stats calculation
            serial_number++;
            if(serial_number % 10000 == 0)
            {
                long elapsed_time = System.nanoTime() - start_time;
                Logger.getLogger(Scaffold.class.getName()).log(Level.INFO, "Items Inserted: " + serial_number);
                Logger.getLogger(Scaffold.class.getName()).log(Level.INFO, "Time Duration: " + elapsed_time / 1000000000.0);
                start_time = System.nanoTime();
            }

            return true;
        }
        catch (UnsupportedEncodingException ex)
        {
            Logger.getLogger(Scaffold.class.getName()).log(Level.SEVERE, "Scaffold entry insertion error!", ex);
        }

        return false;
    }

    /**
     * Helper method to add an item to the storage
     * @param status status of last get operation at the caller
     * @param direction wither parent or children
     * @param key key of the element to insert data into
     * @param neighborBinding binding object to bind the data object to
     * @param hash hash of the parent or child to add to the data list
     */
    private void addItem(OperationStatus status, String direction, DatabaseEntry key, DatabaseEntry data,
                         EntryBinding<Neighbors> neighborBinding, String hash)
    {
        Neighbors neighbors;
        if(status == OperationStatus.NOTFOUND)
        {
            // if key is not present, add a new object
            neighbors = new Neighbors();
        }
        else
        {
            // if key is present, retrieve corresponding data object
            neighbors = neighborBinding.entryToObject(data);
        }
        if(direction.equalsIgnoreCase(PARENTS))
            neighbors.parents.add(hash);
        else if(direction.equalsIgnoreCase(CHILDREN))
            neighbors.children.add(hash);
        data = new DatabaseEntry();
        neighborBinding.objectToEntry(neighbors, data);
        scaffoldDatabase.put(null, key, data);
    }

    public Graph queryManager(Map<String, List<String>> params)
    {
        Graph result = new Graph();
        String hash = params.get(PRIMARY_KEY).get(COL_VALUE);
        String direction = params.get("direction").get(0);
        int maxDepth = Integer.parseInt(params.get("maxDepth").get(0));
        Map<String, Set<String>> lineageMap = getLineage(hash, direction, maxDepth);

        StringBuilder vertex_query = new StringBuilder(500);
        StringBuilder edge_query = new StringBuilder(500);
        try
        {
            vertex_query.append("SELECT * FROM ");
            vertex_query.append(VERTEX_TABLE);
            vertex_query.append(" WHERE ");
            vertex_query.append(PRIMARY_KEY + " IN (");
            edge_query.append("SELECT * FROM ");
            edge_query.append(EDGE_TABLE);
            edge_query.append(" WHERE ");
            for (Map.Entry<String, Set<String>> entry : lineageMap.entrySet())
            {
                String vertexHash = entry.getKey();
                Set<String> neighbors = entry.getValue();
                vertex_query.append(vertexHash);
            }
            vertex_query.append(")");
            vertex_query.append(";");
            edge_query.append(";");

            Logger.getLogger(Scaffold.class.getName()).log(Level.INFO, "Following query: " + vertex_query.toString());
            Logger.getLogger(Scaffold.class.getName()).log(Level.INFO, "Following query: " + edge_query.toString());

            Set<AbstractVertex> vertexSet = PostgreSQL.prepareVertexSetFromSQLResult(vertex_query.toString());
            Set<AbstractEdge> edgeSet = PostgreSQL.prepareEdgeSetFromSQLResult(edge_query.toString());
            result.vertexSet().addAll(vertexSet);
            result.edgeSet().addAll(edgeSet);
        }
        catch(Exception ex)
        {
            Logger.getLogger(Scaffold.class.getName()).log(Level.SEVERE, "Error in Query Manager!", ex);
            return null;
        }

        return result;
    }

    public static void main(String args[])
    {
        // testing code
        Scaffold scaffold = new Scaffold();
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

    @Override
    public Object execute(Object parameters, Integer limit)
    {
        return null;
    }

    private static class Neighbors implements Serializable
    {
        public Set<String> parents = new HashSet<>();
        public Set<String> children = new HashSet<>();

        @Override
        public boolean equals(Object otherObject)
        {
            if (this == otherObject)
            {
                return true;
            }
            if (!(otherObject instanceof Neighbors))
            {
                return false;
            }
            Neighbors otherNeighbor = (Neighbors) otherObject;
            return this.parents.equals(otherNeighbor.parents) &&
                    this.children.equals(otherNeighbor.children);
        }

        @Override
        public int hashCode()
        {
            final int seed1 = 5;
            final int seed2 = 97;
            int hashCode = seed1;
            hashCode = seed2 * hashCode + parents.hashCode();
            hashCode = seed2 * hashCode + children.hashCode();
            return hashCode;
        }

        @Override
        public String toString()
        {
            return "Neighbors{" +
                    "parents=" + parents +
                    ", children=" + children +
                    '}';
        }
    }

}


