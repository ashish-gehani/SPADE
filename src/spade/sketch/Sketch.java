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
package spade.sketch;

import spade.core.AbstractEdge;
import spade.core.AbstractSketch;
import spade.core.AbstractVertex;
import spade.core.BloomFilter;
import spade.core.Graph;
import spade.core.Kernel;
import spade.core.MatrixFilter;
import spade.core.Settings;
import spade.query.common.GetLineage;
import spade.query.postgresql.GetVertex;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.AbstractQuery.OPERATORS;
import static spade.core.AbstractResolver.DESTINATION_HOST;
import static spade.core.AbstractResolver.DESTINATION_PORT;
import static spade.core.AbstractResolver.SOURCE_HOST;
import static spade.core.AbstractResolver.SOURCE_PORT;
import static spade.core.AbstractStorage.PRIMARY_KEY;

// TODO: Work in Progress here.
public class Sketch extends AbstractSketch
{

    private static final double falsePositiveProbability = 0.1;
    private static final int expectedSize = 20;
    private static final Logger logger = Logger.getLogger(Sketch.class.getName());
    private static final boolean USE_CACHE = false;

    public Sketch()
    {
        matrixFilter = new MatrixFilter(falsePositiveProbability, expectedSize);
        objects = new HashMap<>();
    }

    @Override
    public void putVertex(AbstractVertex incomingVertex) {}

    @Override
    public void putEdge(AbstractEdge incomingEdge)
    {
        try
        {
            if (incomingEdge.type().equalsIgnoreCase("Used")
                    && incomingEdge.getParentVertex().getAnnotation("network").equalsIgnoreCase("true"))
            {
                // Connection was created to this host
                AbstractVertex networkVertex = incomingEdge.getParentVertex();
                String remoteHost = networkVertex.getAnnotation("destination host");
                String localHost = networkVertex.getAnnotation("source host");
                if (!USE_CACHE || (USE_CACHE && !Kernel.remoteSketches.containsKey(remoteHost)))
                {
                    logger.log(Level.INFO, "concreteSketch - Attempting to receive sketches from {0}", remoteHost);
                    int port = Integer.parseInt(Settings.getProperty("remote_sketch_port"));
                    SSLSocket remoteSocket = (SSLSocket) Kernel.sslSocketFactory.createSocket(remoteHost, port);

                    OutputStream outStream = remoteSocket.getOutputStream();
                    InputStream inStream = remoteSocket.getInputStream();
                    ObjectOutputStream clientObjectOutputStream = new ObjectOutputStream(outStream);
                    ObjectInputStream clientObjectInputStream = new ObjectInputStream(inStream);

                    clientObjectOutputStream.writeObject("giveSketch");
                    clientObjectOutputStream.flush();
                    AbstractSketch tmpSketch = (AbstractSketch) clientObjectInputStream.readObject();
                    Map<String, AbstractSketch> receivedSketches = (Map<String, AbstractSketch>) clientObjectInputStream.readObject();
                    Kernel.remoteSketches.put(remoteHost, tmpSketch);
                    receivedSketches.remove(localHost);
                    Kernel.remoteSketches.putAll(receivedSketches);

                    logger.log(Level.INFO, "concreteSketch - Received sketches from {0}", remoteHost);
                    clientObjectOutputStream.writeObject("close");
                    clientObjectOutputStream.flush();
                    clientObjectOutputStream.close();
                    clientObjectInputStream.close();
                    outStream.close();
                    inStream.close();
                    remoteSocket.close();
                }
                // Update sketch bloom filters
                BloomFilter newAncestors = Kernel.remoteSketches.get(remoteHost).matrixFilter.get(networkVertex);
                if (newAncestors != null)
                {
                    logger.log(Level.INFO, "concreteSketch - Found Bloom filter for networkVertex");
                    Runnable update = new updateMatrixThread(this, networkVertex, incomingEdge.type());
                    new Thread(update).start();
                }
            }
            else if (incomingEdge.type().equalsIgnoreCase("WasGeneratedBy")
                    && incomingEdge.getChildVertex().getAnnotation("network").equalsIgnoreCase("true"))
            {
                AbstractVertex networkVertex = incomingEdge.getChildVertex();
                Runnable update = new updateMatrixThread(this, networkVertex, incomingEdge.type());
                new Thread(update).start();
            }
        }
        catch (NumberFormatException | IOException | ClassNotFoundException exception)
        {
            Logger.getLogger(Sketch.class.getName()).log(Level.SEVERE, null, exception);
        }
    }
}

class updateMatrixThread implements Runnable
{

    private final AbstractSketch sketch;
    private final AbstractVertex vertex;
    private final String type;
    private static final Logger logger = Logger.getLogger(updateMatrixThread.class.getName());
    private static final String ID_STRING = Settings.getProperty("storage_identifier");

    public updateMatrixThread(AbstractSketch workingSketch, AbstractVertex networkVertex, String edgeType)
    {
        sketch = workingSketch;
        vertex = networkVertex;
        type = edgeType;
    }

    @Override
    public void run()
    {
        String storageId = getStorageId(vertex);
        if (type.equalsIgnoreCase("Used"))
        {
            logger.log(Level.INFO, "concreteSketch - Updating matrixfilter for USED edge for storageId: {0}", storageId);
            String remoteHost = vertex.getAnnotation("destination host");
            BloomFilter newAncestors = Kernel.remoteSketches.get(remoteHost).matrixFilter.get(vertex);
            GetLineage getLineage = new GetLineage();
            Map<String, List<String>> lineageParams = new HashMap<>();
            lineageParams.put(PRIMARY_KEY, Arrays.asList(OPERATORS.EQUALS, storageId));
            lineageParams.put("direction", Collections.singletonList("descendants"));
            lineageParams.put("maxDepth", Collections.singletonList("20"));
            Graph descendants = getLineage.execute(lineageParams, 100);
            for (AbstractVertex currentVertex : descendants.vertexSet())
            {
                if (currentVertex.getAnnotation("network").equalsIgnoreCase("true"))
                {
                    sketch.matrixFilter.updateAncestors(currentVertex, newAncestors);
                }
            }
            logger.log(Level.INFO, "concreteSketch - Updated Bloom filters for USED edge - storageId: {0}", storageId);
        }
        else if (type.equalsIgnoreCase("WasGeneratedBy"))
        {
            logger.log(Level.INFO, "concreteSketch - Updating matrix filter for WGB edge for storageId: {0}", storageId);
            GetLineage getLineage = new GetLineage();
            Map<String, List<String>> lineageParams = new HashMap<>();
            lineageParams.put(PRIMARY_KEY, Arrays.asList(OPERATORS.EQUALS, storageId));
            lineageParams.put("direction", Collections.singletonList("ancestors"));
            lineageParams.put("maxDepth", Collections.singletonList("20"));
            Graph ancestors = getLineage.execute(lineageParams, 100);
            for (AbstractVertex currentVertex : ancestors.vertexSet())
            {
                if (currentVertex.getAnnotation("network").equalsIgnoreCase("true"))
                {
                    sketch.matrixFilter.add(vertex, currentVertex);
                }
            }
            logger.log(Level.INFO, "concreteSketch - Updated Bloom filters for WGB edge - storageId: {0}", storageId);
        }
    }

    private String getStorageId(AbstractVertex networkVertex)
    {
        try
        {
            GetVertex getVertex = new GetVertex();
            logger.log(Level.INFO, "concreteSketch - Getting storageId of networkVertex");
            Map<String, List<String>> vertexParams = new HashMap<>();
            vertexParams.put(SOURCE_HOST, Arrays.asList(OPERATORS.EQUALS, networkVertex.getAnnotation(SOURCE_HOST)));
            vertexParams.put(SOURCE_PORT, Arrays.asList(OPERATORS.EQUALS, networkVertex.getAnnotation(SOURCE_PORT)));
            vertexParams.put(DESTINATION_HOST, Arrays.asList(OPERATORS.EQUALS, networkVertex.getAnnotation(DESTINATION_HOST)));
            vertexParams.put(DESTINATION_PORT, Arrays.asList(OPERATORS.EQUALS, networkVertex.getAnnotation(DESTINATION_PORT)));
            Set<AbstractVertex> vertexSet = getVertex.execute(vertexParams, 100);
            AbstractVertex resultVertex = vertexSet.iterator().next();
            logger.log(Level.INFO, "concreteSketch - Returning storageId: {0}", resultVertex.getAnnotation(ID_STRING));
            return resultVertex.getAnnotation(ID_STRING);
        }
        catch (Exception exception)
        {
            Logger.getLogger(Sketch.class.getName()).log(Level.SEVERE, null, exception);
            return null;
        }
    }
}
