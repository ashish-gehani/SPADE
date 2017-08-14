package spade.core;

import org.apache.commons.collections.CollectionUtils;
import spade.reporter.audit.OPMConstants;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.AbstractQuery.DEFAULT_MAX_LIMIT;
import static spade.core.AbstractQuery.OPERATORS;
import static spade.core.AbstractStorage.PRIMARY_KEY;

/**
 * @author raza
 */
public abstract class AbstractResolver implements Runnable
{
    public static final String SOURCE_HOST = "source_host";
    public static final String SOURCE_PORT = "source_port";
    public static final String DESTINATION_HOST = "destination_host";
    public static final String DESTINATION_PORT = "destination_port";

    // fields required to fetch and return remote parts of result graph
    protected Set<Graph> finalGraph = new HashSet<>();
    protected Graph partialGraph;
    protected int depth;
    protected String direction;
    protected String function;

    protected AbstractResolver(Graph pgraph, String func, int d, String dir)
    {
        partialGraph = pgraph;
        depth = d;
        direction = dir;
        function = func;
    }

    public Set<Graph> getFinalGraph()
    {
        return finalGraph;
    }

    @Override
    public abstract void run();

    /**
     * Method used to get remote lineage of a network vertex.
     *
     * @param networkVertex The input network vertex.
     * @param depth Depth of lineage.
     * @param direction Direction of lineage.
     * @return The result represented by a Graph object.
     */
    protected static Graph queryNetworkVertex(AbstractVertex networkVertex, int depth, String direction)
    {
        Graph resultGraph = null;
        try
        {
            // Establish a connection to the remote host
            String host = networkVertex.getAnnotation(OPMConstants.ARTIFACT_REMOTE_ADDRESS);
            int port = Integer.parseInt(Settings.getProperty("dig_query_port"));
            SSLSocket remoteSocket = (SSLSocket) Kernel.sslSocketFactory.createSocket(host, port);

            OutputStream outStream = remoteSocket.getOutputStream();
            InputStream inStream = remoteSocket.getInputStream();
            ObjectInputStream graphInputStream = new ObjectInputStream(inStream);
            PrintWriter remoteSocketOut = new PrintWriter(outStream, true);

            String networkVertexQuery = "GetVertex(" +
                    OPMConstants.ARTIFACT_LOCAL_ADDRESS +
                    OPERATORS.EQUALS +
                    networkVertex.getAnnotation(OPMConstants.ARTIFACT_REMOTE_ADDRESS) +
                    " AND " +
                    OPMConstants.ARTIFACT_LOCAL_PORT +
                    OPERATORS.EQUALS +
                    networkVertex.getAnnotation(OPMConstants.ARTIFACT_REMOTE_PORT) +
                    " AND " +
                    OPMConstants.ARTIFACT_REMOTE_ADDRESS +
                    OPERATORS.EQUALS +
                    networkVertex.getAnnotation(OPMConstants.ARTIFACT_LOCAL_ADDRESS) +
                    " AND " +
                    OPMConstants.ARTIFACT_REMOTE_PORT +
                    OPERATORS.EQUALS +
                    networkVertex.getAnnotation(OPMConstants.ARTIFACT_LOCAL_PORT) +
                    " AND " +
                    OPMConstants.SOURCE +
                    OPERATORS.EQUALS +
                    OPMConstants.SOURCE_AUDIT_NETFILTER +
                    ", null" +
                    ")";

            remoteSocketOut.println(networkVertexQuery);
            // Check whether the remote query server returned a vertex set in response
            Set<AbstractVertex> vertexSet = (Set<AbstractVertex>) graphInputStream.readObject();
            AbstractVertex targetVertex;
            if(!CollectionUtils.isEmpty(vertexSet))
                targetVertex = vertexSet.iterator().next();
            else
                return null;
            String targetVertexHash = targetVertex.getAnnotation(PRIMARY_KEY);

            String lineageQuery = "GetLineage(" +
                    PRIMARY_KEY +
                    OPERATORS.EQUALS +
                    targetVertexHash +
                    ", " +
                    DEFAULT_MAX_LIMIT +
                    ", " +
                    depth +
                    ", " +
                    direction +
                    ")";
            remoteSocketOut.println(lineageQuery);
            resultGraph = (Graph) graphInputStream.readObject();

            remoteSocketOut.println("exit");
            remoteSocketOut.close();
            graphInputStream.close();
            inStream.close();
            outStream.close();
            remoteSocket.close();
        }
        catch (NumberFormatException | IOException | ClassNotFoundException exception)
        {
            Logger.getLogger(AbstractResolver.class.getName()).log(Level.SEVERE, "Remote resolution unsuccessful!", exception);
            return null;
        }

        return resultGraph;
    }
}

