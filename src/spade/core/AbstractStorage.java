/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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
package spade.core;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.scaffold.Scaffold;
import spade.query.scaffold.ScaffoldFactory;


/**
 * This is the base class from which concrete storage types inherit.
 *
 * @author Dawood Tariq and Raza Ahmad
 */
public abstract class AbstractStorage
{
	// Screens only accessible from package and self (not even children can view it)
	final Object screensLock = new Object();
	final List<AbstractScreen> screens = new ArrayList<AbstractScreen>();

	final void addScreen(final AbstractScreen screen) throws IllegalArgumentException{
		if(screen == null){
			throw new IllegalArgumentException("NULL screen cannot be added");
		}
		synchronized(screensLock){
			screens.add(screen);
		}
	}

	final void addScreens(final List<AbstractScreen> screens) throws IllegalArgumentException{
		if(screens == null){
			throw new IllegalArgumentException("NULL screens cannot be added");
		}
		for(final AbstractScreen screen : screens){
			addScreen(screen);
		}
	}

	final List<AbstractScreen> getScreens(){
		synchronized(screensLock){
			return new ArrayList<AbstractScreen>(screens);
		}
	}

	final void clearScreens(){
		synchronized(screensLock){
			screens.clear();
		}
	}
	
	public final AbstractScreen findScreen(final Class<? extends AbstractScreen> screenClass){
		if(screenClass != null){
			synchronized(screensLock){
				for(final AbstractScreen screen : screens){
					if(screen != null){
						if(screen.getClass().equals(screenClass)){
							return screen;
						}
					}
				}	
			}
		}
		return null;
	}

	public final boolean putVertex(final AbstractVertex vertex){
		boolean block = false;
		if(vertex == null){
			block = true;
		}else{
			synchronized(screensLock){
				for(final AbstractScreen screen : screens){
					if(screen.blockVertex(vertex)){
						block = true;
						break;
					}
				}
			}
		}
		if(block){
			return false;
		}else{
			return storeVertex(vertex);
		}
	}

	public final boolean putEdge(final AbstractEdge edge){
		boolean block = false;
		if(edge == null){
			block = true;
		}else{
			synchronized(screensLock){
				for(final AbstractScreen screen : screens){
					if(screen.blockEdge(edge)){
						block = true;
						break;
					}
				}
			}
		}
		if(block){
			return false;
		}else{
			return storeEdge(edge);
		}
	}

	////////////////

    public static final String PRIMARY_KEY = "hash";
    public static final String CHILD_VERTEX_KEY = "childVertexHash";
    public static final String PARENT_VERTEX_KEY = "parentVertexHash";
    public static final String DIRECTION = "direction";
    public static final String MAX_DEPTH = "maxDepth";
    public static final String MAX_LENGTH = "maxLength";
    public static final String DIRECTION_ANCESTORS = "ancestors";
    public static final String DIRECTION_DESCENDANTS = "descendants";
    public static final String DIRECTION_BOTH = "both";
    protected Logger logger;

    /**
     * The arguments with which this storage was initialized.
     */
    public String arguments;
    /**
     * The number of vertices that this storage instance has successfully
     * received.
     */
    protected long vertexCount;
    /**
     * The number of edges that this storage instance has successfully received.
     */
    protected long edgeCount;

    protected static Properties databaseConfigs = new Properties();

    /**
     * Variables and functions for computing performance stats
     */
    protected boolean reportingEnabled = false;
    protected long reportingInterval;
    protected long reportEveryMs;
    protected long startTime, lastReportedTime;
    protected long lastReportedVertexCount, lastReportedEdgeCount;

    private static String configFile = Settings.getDefaultConfigFilePath(AbstractStorage.class);
    /**
     * Variables and functions for managing scaffold storage
     */
    public static Scaffold scaffold = null;
    public static boolean BUILD_SCAFFOLD;
    public static String SCAFFOLD_PATH;
    public static String SCAFFOLD_DATABASE_NAME;
    static
    {
        try
        {
            databaseConfigs.load(new FileInputStream(configFile));
            BUILD_SCAFFOLD = Boolean.parseBoolean(databaseConfigs.getProperty("build_scaffold"));
            SCAFFOLD_PATH = Settings.getPathRelativeToSPADERoot(databaseConfigs.getProperty("scaffold_path"));
            SCAFFOLD_DATABASE_NAME = databaseConfigs.getProperty("scaffold_database_name");
            if(BUILD_SCAFFOLD)
            {
                scaffold = ScaffoldFactory.createScaffold(SCAFFOLD_DATABASE_NAME);
                if(!scaffold.initialize(SCAFFOLD_PATH))
                {
                    Logger.getLogger(AbstractStorage.class.getName()).log(Level.WARNING, "Scaffold not set!");
                }
            }
        }
        catch(Exception ex)
        {
            // default settings
            BUILD_SCAFFOLD = false;
            SCAFFOLD_PATH = Settings.getPathRelativeToSPADERoot("db", "scaffold");
            SCAFFOLD_DATABASE_NAME = "BerkeleyDB";
            Logger.getLogger(AbstractStorage.class.getName()).log(Level.WARNING,
            "Loading scaffold configurations from file '" + configFile + "' " +
                    " unsuccessful! Falling back to default settings", ex);
        }
    }

    protected boolean insertScaffoldEntry(AbstractEdge incomingEdge)
    {
        return scaffold.insertEntry(incomingEdge);
    }

    /* For testing purposes only. Set scaffold through Settings file normally. */
    public static void setScaffold(Scaffold scaffold)
    {
        AbstractStorage.scaffold = scaffold;
        BUILD_SCAFFOLD = true;
    }

    /**
     * This method is invoked by the kernel to initialize the storage.
     *
     * @param arguments The arguments with which this storage is to be
     * initialized.
     * @return True if the storage was initialized successfully.
     */
    public abstract boolean initialize(String arguments);

    /**
     * This method is invoked by the kernel to shut down the storage.
     *
     * @return True if the storage was shut down successfully.
     */
    public boolean shutdown()
    {
        if(BUILD_SCAFFOLD)
        {
            scaffold.shutdown();
        }

        return true;
    }

    protected void computeStats()
    {
        long currentTime = System.currentTimeMillis();
        if((currentTime - lastReportedTime) >= reportEveryMs)
        {
            printStats();
            lastReportedTime = currentTime;
            lastReportedVertexCount = vertexCount;
            lastReportedEdgeCount = edgeCount;
        }
    }

    protected void printStats()
    {
        long currentTime = System.currentTimeMillis();
        float overallTime = (float) (currentTime - startTime) / 1000; // # in secs
        float intervalTime = (float) (currentTime - lastReportedTime) / 1000; // # in secs
        if(overallTime > 0 && intervalTime > 0)
        {
            // # records/sec
            float overallVertexVolume = (float) vertexCount / overallTime;
            float overallEdgeVolume = (float) edgeCount / overallTime;
            // # records/sec

            long intervalVertexCount = vertexCount - lastReportedVertexCount;
            long intervalEdgeCount = edgeCount - lastReportedEdgeCount;
            float intervalVertexVolume = (float) (intervalVertexCount) / intervalTime;
            float intervalEdgeVolume = (float) (intervalEdgeCount) / intervalTime;
            logger.log(Level.INFO, "Overall Stats => rate: {0} vertex/sec and {1} edge/sec. count: vertices: {2} and edges: {3}. In total {4} seconds.\n" +
                            "Interval Stats => rate: {5} vertex/sec and {6} edge/sec. count: vertices: {7} and edges: {8}. In {9} seconds.",
                    new Object[]{overallVertexVolume, overallEdgeVolume, vertexCount, edgeCount, overallTime, intervalVertexVolume,
                            intervalEdgeVolume, intervalVertexCount, intervalEdgeCount, intervalTime});
        }
    }


    /**
     * This method returns current edge count.
     *
     * @return edge count
     */
    public long getEdgeCount(){
        return edgeCount;
    }

    /**
     * This method returns current vertex count.
     *
     * @return vertex count
     */
    public long getVertexCount(){
        return vertexCount;
    }

    /**
     * This method is triggered by the Kernel to flush transactions.
     *
     * @return True if the transactions were flushed successfully.
     */
    public boolean flushTransactions(boolean force) {
        return true;
    }

    /**
     * This function inserts the given edge into the underlying storage(s) and
     * updates the cache(s) accordingly.
     * @param incomingEdge edge to insert into the storage
     * @return returns true if the insertion is successful. Insertion is considered
     * not successful if the edge is already present in the storage.
     */
    public abstract boolean storeEdge(AbstractEdge incomingEdge);

    /**
     * This function inserts the given vertex into the underlying storage(s) and
     * updates the cache(s) accordingly.
     * @param incomingVertex vertex to insert into the storage
     * @return returns true if the insertion is successful. Insertion is considered
     * not successful if the vertex is already present in the storage.
     */
    public abstract boolean storeVertex(AbstractVertex incomingVertex);

    public abstract Object executeQuery(String query);
    
    public QueryInstructionExecutor getQueryInstructionExecutor(){
    	throw new RuntimeException("Storage does not support querying!");
    }
    
}
