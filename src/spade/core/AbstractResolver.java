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
            int port = Integer.parseInt(Settings.getProperty("commandline_query_port"));
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
                    ")";

            remoteSocketOut.println(networkVertexQuery);
            String returnType = (String) graphInputStream.readObject();
            // Check whether the remote query server returned a vertex set in response
            Set<AbstractVertex> vertexSet;
            if(returnType.equals(Set.class.getName()))
            {
                vertexSet = (Set<AbstractVertex>) graphInputStream.readObject();
            }
            else
            {
                return null;
            }
            AbstractVertex targetNetworkVertex;
            if(!CollectionUtils.isEmpty(vertexSet))
                targetNetworkVertex = vertexSet.iterator().next();
            else
                return null;
            String targetNetworkVertexHash = targetNetworkVertex.bigHashCode();

            String lineageQuery = "GetLineage(" +
                    PRIMARY_KEY +
                    OPERATORS.EQUALS +
                    targetNetworkVertexHash +
                    ", " +
                    depth +
                    ", " +
                    direction +
                    ")";
            remoteSocketOut.println(lineageQuery);
            returnType = (String) graphInputStream.readObject();
            if(returnType.equals(Graph.class.getName()))
            {
                AbstractEdge localToRemoteEdge = new Edge(networkVertex, targetNetworkVertex);
                localToRemoteEdge.addAnnotation("type", "WasDerivedFrom");
                AbstractEdge remoteToLocalEdge = new Edge(targetNetworkVertex, networkVertex);
                remoteToLocalEdge.addAnnotation("type", "WasDerivedFrom");
                resultGraph = (Graph) graphInputStream.readObject();
                resultGraph.putEdge(localToRemoteEdge);
                resultGraph.putEdge(remoteToLocalEdge);
            }

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

