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
    public Recursive(Graph pgraph, String func, int d, String dir)
    {
        super(pgraph, func, d, dir);
    }

    @Override
    public void run()
    {
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
                Graph remoteGraph = null;
                for (Map.Entry<AbstractVertex, Integer> currentEntry : currentNetworkMap.entrySet())
                {
                    AbstractVertex networkVertex = currentEntry.getKey();
                    int currentDepth = currentEntry.getValue();
                    // Execute remote query
                    remoteGraph = queryNetworkVertex(networkVertex, depth - currentDepth, direction);
                    // Update the depth values of all network artifacts in the
                    // remote network map to reflect current level of iteration
                    if(remoteGraph != null)
                    {
                        for(Map.Entry<AbstractVertex, Integer> currentNetworkEntry : remoteGraph.networkMap().entrySet())
                        {
                            AbstractVertex tempNetworkVertex = currentNetworkEntry.getKey();
                            int updatedDepth = currentDepth + currentNetworkEntry.getValue();
                            remoteGraph.putNetworkVertex(tempNetworkVertex, updatedDepth);
                        }
                    }
                }
                currentNetworkMap.clear();
                if(remoteGraph != null)
                {
                    finalGraph.add(remoteGraph);
                    // Set the networkMap to network vertexes of the newly create remoteGraph
                    currentNetworkMap = remoteGraph.networkMap();
                }
            }
        }
        catch(Exception ex)
        {
            Logger.getLogger(Recursive.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
