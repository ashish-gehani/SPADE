/*
--------------------------------------------------------------------------------
SPADE - Support for Provenance Auditing in Distributed Environments.
Copyright (C) 2011 SRI International

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

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import spade.core.*;


public class ConcreteSketchUncached extends AbstractSketch {

    private static final double falsePositiveProbability = 0.1;
    private static final int expectedSize = 20;

    public ConcreteSketchUncached() {
        matrixFilter = new MatrixFilter(falsePositiveProbability, expectedSize);
        objects = new HashMap<String, Object>();
    }

    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        return;
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
        try {
            if (incomingEdge.type().equalsIgnoreCase("Used")
                    //&& incomingEdge.getDestinationVertex().type().equalsIgnoreCase("Network")) {
                    && incomingEdge.getDestinationVertex().getAnnotation("network").equalsIgnoreCase("true")) {
                // Connection was created to this host
                AbstractVertex networkVertex = incomingEdge.getDestinationVertex();
                String remoteHost = networkVertex.getAnnotation("destination host");
                String localHost = networkVertex.getAnnotation("source host");
                //if (!Kernel.remoteSketches.containsKey(remoteHost)) {
                    // If sketch for remote doesn't exist, fetch it and add it to the local cache

                    ////////////////////////////////////////////////////////////
                    System.out.println("concreteSketch - Attempting to receive sketches from " + remoteHost);
                    ////////////////////////////////////////////////////////////

                    SocketAddress sockaddr = new InetSocketAddress(remoteHost, Kernel.REMOTE_SKETCH_PORT);
                    Socket remoteSocket = new Socket();
                    remoteSocket.connect(sockaddr, Kernel.CONNECTION_TIMEOUT);
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

                    ////////////////////////////////////////////////////////////
                    System.out.println("concreteSketch - Received sketches from " + remoteHost);
                    ////////////////////////////////////////////////////////////

                    clientObjectOutputStream.writeObject("close");
                    clientObjectOutputStream.flush();
                    clientObjectOutputStream.close();
                    clientObjectInputStream.close();
                    outStream.close();
                    inStream.close();
                    remoteSocket.close();
                //}
                // Update sketch bloom filters
                BloomFilter newAncestors = Kernel.remoteSketches.get(remoteHost).matrixFilter.get(networkVertex);
                if (newAncestors != null) {
                    ////////////////////////////////////////////////////////////
                    System.out.println("concreteSketch - Found bloomfilter for networkVertex");
                    ////////////////////////////////////////////////////////////
                    Runnable update = new updateMatrixThreadUncached(this, networkVertex, incomingEdge.type());
                    new Thread(update).start();
                }
            } else if (incomingEdge.type().equalsIgnoreCase("WasGeneratedBy")
                    //&& incomingEdge.getSourceVertex().type().equalsIgnoreCase("Network")) {
                    && incomingEdge.getSourceVertex().getAnnotation("network").equalsIgnoreCase("true")) {
                AbstractVertex networkVertex = incomingEdge.getSourceVertex();
                Runnable update = new updateMatrixThreadUncached(this, networkVertex, incomingEdge.type());
                new Thread(update).start();
            }
        } catch (Exception exception) {
            Logger.getLogger(ConcreteSketchUncached.class.getName()).log(Level.SEVERE, null, exception);
        }
    }
}
class updateMatrixThreadUncached implements Runnable {
    
    private AbstractSketch sketch;
    private AbstractVertex vertex;
    private String type;

    public updateMatrixThreadUncached(AbstractSketch workingSketch, AbstractVertex networkVertex, String edgeType) {
        sketch = workingSketch;
        vertex = networkVertex;
        type = edgeType;
    }

    public void run() {
        String storageId = getStorageId(vertex);
        if (type.equalsIgnoreCase("Used")) {
            ////////////////////////////////////////////////////////////
            System.out.println("concreteSketch - Updating matrixfilter for USED edge for storageId: " + storageId);
            ////////////////////////////////////////////////////////////
            String remoteHost = vertex.getAnnotation("destination host");
            BloomFilter newAncestors = Kernel.remoteSketches.get(remoteHost).matrixFilter.get(vertex);
            Graph descendants = Query.executeQuery("query Neo4j lineage " + storageId + " 20 d null tmp.dot", false);
            for (AbstractVertex currentVertex : descendants.vertexSet()) {
                //if (currentVertex.type().equalsIgnoreCase("Network")) {
                if (currentVertex.getAnnotation("network").equalsIgnoreCase("true")) {
                    sketch.matrixFilter.updateAncestors(currentVertex, newAncestors);
                }
            }
            ////////////////////////////////////////////////////////////
            System.out.println("concreteSketch - Updated bloomfilters for USED edge - storageId: " + storageId);
            ////////////////////////////////////////////////////////////
        } else if (type.equalsIgnoreCase("WasGeneratedBy")) {
            ////////////////////////////////////////////////////////////
            System.out.println("concreteSketch - Updating matrixfilter for WGB edge for storageId: " + storageId);
            ////////////////////////////////////////////////////////////
            Graph ancestors = Query.executeQuery("query Neo4j lineage " + storageId + " 20 a null tmp.dot", false);
            for (AbstractVertex currentVertex : ancestors.vertexSet()) {
                //if (currentVertex.type().equalsIgnoreCase("Network")) {
                if (currentVertex.getAnnotation("network").equalsIgnoreCase("true")) {
                    sketch.matrixFilter.add(vertex, currentVertex);
                }
            }            
            ////////////////////////////////////////////////////////////
            System.out.println("concreteSketch - Updated bloomfilters for WGB edge - storageId: " + storageId);
            ////////////////////////////////////////////////////////////
        }
    }

    private String getStorageId(AbstractVertex networkVertex) {
        try {
            ////////////////////////////////////////////////////////////
            System.out.println("concreteSketch - Getting storageId of networkVertex");
            ////////////////////////////////////////////////////////////
            String vertexQueryExpression = "query Neo4j vertices";
            vertexQueryExpression += " source\\ host:" + networkVertex.getAnnotation("source host");
            vertexQueryExpression += " AND source\\ port:" + networkVertex.getAnnotation("source port");
            vertexQueryExpression += " AND destination\\ host:" + networkVertex.getAnnotation("destination host");
            vertexQueryExpression += " AND destination\\ port:" + networkVertex.getAnnotation("destination port");
            Graph result = Query.executeQuery(vertexQueryExpression, false);
            AbstractVertex resultVertex = result.vertexSet().iterator().next();
            ////////////////////////////////////////////////////////////
            System.out.println("concreteSketch - Returning storageId: " + resultVertex.getAnnotation("storageId"));
            ////////////////////////////////////////////////////////////
            return resultVertex.getAnnotation("storageId");
        } catch (Exception exception) {
            Logger.getLogger(ConcreteSketchUncached.class.getName()).log(Level.SEVERE, null, exception);
            return null;
        }
    }
}
