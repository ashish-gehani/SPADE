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
package spade.resolver;

import org.apache.commons.collections.CollectionUtils;
import spade.core.AbstractEdge;
import spade.core.AbstractQuery;
import spade.core.AbstractResolver;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;
import spade.core.Kernel;
import spade.core.Settings;
import spade.reporter.audit.OPMConstants;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static spade.core.AbstractStorage.PRIMARY_KEY;

/**
 * @author raza
 */
public class Recursive extends AbstractResolver
{
    private static final int NTHREADS = 10;
    private static final Logger logger = Logger.getLogger(Recursive.class.getName());

    public Recursive(Graph partialGraph, String function, int depth, String direction)
    {
        super(partialGraph, function, depth, direction);
    }

    @Override
    public void run()
    {
        Map<AbstractVertex, Integer> currentNetworkMap = partialGraph.networkMap();
        logger.log(Level.INFO, "network Map" + currentNetworkMap.toString());
        try
        {
            // Perform remote query on network vertices.
            ExecutorService  executor = Executors.newFixedThreadPool(NTHREADS);
            List<Future<Graph>> futures = new ArrayList<>();
            for (Map.Entry<AbstractVertex, Integer> currentEntry : currentNetworkMap.entrySet())
            {
                AbstractVertex networkVertex = currentEntry.getKey();
                if(!networkVertex.getAnnotation(OPMConstants.SOURCE).equals(OPMConstants.SOURCE_AUDIT_NETFILTER))
                    continue;
                int currentDepth = currentEntry.getValue();

                // Execute remote query
                Callable<Graph> worker = new ContactRemote(networkVertex, depth - currentDepth, direction);
                Future<Graph> submit = executor.submit(worker);
                futures.add(submit);
            }
            for(Future<Graph> future: futures)
            {
                try
                {
                    Graph remoteGraph = future.get();
                    if(remoteGraph != null)
                    {
                        finalGraph.add(remoteGraph);
                    }
                }
                catch(Exception ex)
                {
                    logger.log(Level.SEVERE, "Error in fetching the result from callable future", ex);
                }
            }
            executor.shutdown();
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Error in contacting remote hosts for query resolution", ex);
        }
    }
}

class ContactRemote implements Callable<Graph>
{
    private AbstractVertex networkVertex;
    private int depth;
    private String direction;
    private static final Logger logger = Logger.getLogger(ContactRemote.class.getName());

    ContactRemote(AbstractVertex networkVertex, int depth, String direction)
    {
        this.networkVertex = networkVertex;
        this.depth = depth;
        this.direction = direction;
    }

    /**
     * Computes a result, or throws an exception if unable to do so.
     *
     * @return computed result
     * @throws Exception if unable to compute a result
     */
    @Override
    public Graph call() throws Exception
    {
        Graph resultGraph = null;
        try
        {
            // Establish a connection to the remote host
            String host = networkVertex.getAnnotation(OPMConstants.ARTIFACT_REMOTE_ADDRESS);
            int port = Integer.parseInt(Settings.getProperty("commandline_query_port"));
            logger.log(Level.INFO, "network Vertex: " + networkVertex);
            SSLSocket remoteSocket = (SSLSocket) Kernel.sslSocketFactory.createSocket();
            int connectTimeOut = 5000; // 5 sec
            remoteSocket.connect(new InetSocketAddress(host, port), connectTimeOut);
//            SSLSocket remoteSocket = (SSLSocket) Kernel.sslSocketFactory.createSocket(host, port);

            OutputStream outStream = remoteSocket.getOutputStream();
            InputStream inStream = remoteSocket.getInputStream();
            ObjectInputStream graphInputStream = new ObjectInputStream(inStream);
            PrintWriter remoteSocketOut = new PrintWriter(outStream, true);

            String networkVertexQuery = "GetVertex(" +
                    OPMConstants.ARTIFACT_LOCAL_ADDRESS +
                    AbstractQuery.OPERATORS.EQUALS +
                    networkVertex.getAnnotation(OPMConstants.ARTIFACT_REMOTE_ADDRESS) +
                    " AND " +
                    OPMConstants.ARTIFACT_LOCAL_PORT +
                    AbstractQuery.OPERATORS.EQUALS +
                    networkVertex.getAnnotation(OPMConstants.ARTIFACT_REMOTE_PORT) +
                    " AND " +
                    OPMConstants.ARTIFACT_REMOTE_ADDRESS +
                    AbstractQuery.OPERATORS.EQUALS +
                    networkVertex.getAnnotation(OPMConstants.ARTIFACT_LOCAL_ADDRESS) +
                    " AND " +
                    OPMConstants.ARTIFACT_REMOTE_PORT +
                    AbstractQuery.OPERATORS.EQUALS +
                    networkVertex.getAnnotation(OPMConstants.ARTIFACT_LOCAL_PORT) +
                    " AND " +
                    OPMConstants.SOURCE +
                    AbstractQuery.OPERATORS.EQUALS +
                    OPMConstants.SOURCE_AUDIT_NETFILTER +
                    ")";

            remoteSocketOut.println(networkVertexQuery);
            logger.log(Level.INFO, "remote vertex query: " + networkVertexQuery);
            String returnType = (String) graphInputStream.readObject();
            // Check whether the remote query server returned a vertex set in response
            Set<AbstractVertex> vertexSet;
            if(returnType.equals(Set.class.getName()))
            {
                vertexSet = (Set<AbstractVertex>) graphInputStream.readObject();
            }
            else
            {
                logger.log(Level.INFO, "Return type not Set!");
                return null;
            }
            AbstractVertex targetNetworkVertex;
            if(!CollectionUtils.isEmpty(vertexSet))
            {
                targetNetworkVertex = vertexSet.iterator().next();
            }
            else
            {
                logger.log(Level.INFO, "TargetNetworkVertex empty!");
                return null;
            }
            String targetNetworkVertexHash = targetNetworkVertex.bigHashCode();

            String lineageQuery = "GetLineage(" +
                    PRIMARY_KEY +
                    AbstractQuery.OPERATORS.EQUALS +
                    targetNetworkVertexHash +
                    ", " +
                    depth +
                    ", " +
                    direction +
                    ")";
            remoteSocketOut.println(lineageQuery);
            logger.log(Level.INFO, "remote lineage query: " + lineageQuery);

            returnType = (String) graphInputStream.readObject();
            if(returnType.equals(Graph.class.getName()))
            {
                AbstractEdge localToRemoteEdge = new Edge(networkVertex, targetNetworkVertex);
                localToRemoteEdge.addAnnotation("type", "WasDerivedFrom");
                AbstractEdge remoteToLocalEdge = new Edge(targetNetworkVertex, networkVertex);
                remoteToLocalEdge.addAnnotation("type", "WasDerivedFrom");
                resultGraph = (Graph) graphInputStream.readObject();
                resultGraph.putVertex(networkVertex);
                resultGraph.putEdge(localToRemoteEdge);
                resultGraph.putEdge(remoteToLocalEdge);
            }
            else
            {
                logger.log(Level.INFO, "Return type not Graph!");
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
            logger.log(Level.SEVERE, "Remote resolution unsuccessful!", exception);
            return null;
        }

        logger.log(Level.INFO, "Remote resolution successful!");
        return resultGraph;
    }
}
