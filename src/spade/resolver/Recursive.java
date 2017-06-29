package spade.resolver;

import spade.core.AbstractResolver;
import spade.core.AbstractVertex;
import spade.core.Graph;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author raza
 */
public class Recursive extends AbstractResolver
{
    public Recursive(Graph graph, String func, int d, String dir)
    {
        super(graph, func, d, dir);
    }

    @Override
    public void run()
    {
        Graph remoteGraph = new Graph();
        Map<AbstractVertex, Integer> currentNetworkMap = partialGraph.networkMap();
        try
        {
            // Perform remote queries until the network map is exhausted
            while (!currentNetworkMap.isEmpty())
            {
                // Perform remote query on current network vertex and union
                // the result with the remoteGraph. This also adds the network
                // vertexes to the remoteGraph as well, so that deeper level
                // network queries are resolved iteratively.
                for (Map.Entry<AbstractVertex, Integer> currentEntry : currentNetworkMap.entrySet())
                {
                    AbstractVertex networkVertex = currentEntry.getKey();
                    int currentDepth = currentEntry.getValue();
                    // Execute remote query
                    Graph tempRemoteGraph = queryNetworkVertex(networkVertex, depth - currentDepth, direction);
                    // Update the depth values of all network artifacts in the
                    // remote network map to reflect current level of iteration
                    if(tempRemoteGraph != null)
                    {
                        for(Map.Entry<AbstractVertex, Integer> currentNetworkEntry : tempRemoteGraph.networkMap().entrySet())
                        {
                            AbstractVertex tempNetworkVertex = currentNetworkEntry.getKey();
                            int updatedDepth = currentDepth + currentNetworkEntry.getValue();
                            tempRemoteGraph.putNetworkVertex(tempNetworkVertex, updatedDepth);
                        }
                        // Add the lineage of the current network node to the
                        // overall result
                        remoteGraph = Graph.union(remoteGraph, tempRemoteGraph);
                    }
                }
                currentNetworkMap.clear();
                // Set the networkMap to network vertexes of the newly
                // create remoteGraph
                currentNetworkMap = remoteGraph.networkMap();
            }

            finalGraph = Graph.union(partialGraph, remoteGraph);
        }
        catch(Exception ex)
        {
            Logger.getLogger(Recursive.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
