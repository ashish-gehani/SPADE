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
            // Perform remote query on network vertices.
            for (Map.Entry<AbstractVertex, Integer> currentEntry : currentNetworkMap.entrySet())
            {
                Graph remoteGraph;
                AbstractVertex networkVertex = currentEntry.getKey();
                int currentDepth = currentEntry.getValue();
                // Execute remote query
                remoteGraph = queryNetworkVertex(networkVertex, depth - currentDepth, direction);
                if(remoteGraph != null)
                {
                    finalGraph.add(remoteGraph);
                }
            }
        }
        catch(Exception ex)
        {
            Logger.getLogger(Recursive.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
