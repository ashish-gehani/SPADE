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

import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.InetAddress;
import java.util.Map;
import spade.core.AbstractEdge;
import spade.core.AbstractSketch;
import spade.core.AbstractVertex;
import spade.core.Kernel;
import spade.core.MatrixFilter;
import spade.core.BloomFilter;
import spade.core.Graph;

public class ConcreteSketch extends AbstractSketch {

    private static final double falsePositiveProbability = 0.1;
    private static final int expectedSize = 100;

    public ConcreteSketch() {
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
                    && incomingEdge.getDestinationVertex().type().equalsIgnoreCase("Network")) {
                // Connection was created to this host
                AbstractVertex networkVertex = incomingEdge.getDestinationVertex();
                String remoteHost = networkVertex.getAnnotation("source host");
                String storageId = networkVertex.removeAnnotation("stroageId");
                if (!Kernel.remoteSketches.containsKey(remoteHost)) {
                    // If sketch for remote doesn't exist, fetch it and add it to the local cache
                    Socket remoteSocket = new Socket(remoteHost, Kernel.REMOTE_SKETCH_PORT);
                    String expression = "giveSketch";
                    PrintWriter remoteSocketOut = new PrintWriter(remoteSocket.getOutputStream(), true);
                    ObjectInputStream graphInputStream = new ObjectInputStream(remoteSocket.getInputStream());
                    remoteSocketOut.println(expression);
                    AbstractSketch tmpSketch = (AbstractSketch) graphInputStream.readObject();
                    Map<String, AbstractSketch> receivedSketches = (Map<String, AbstractSketch>) graphInputStream.readObject();
                    Kernel.remoteSketches.put(remoteHost, tmpSketch);
                    receivedSketches.remove(InetAddress.getLocalHost().getHostAddress());
                    Kernel.remoteSketches.putAll(receivedSketches);
                    remoteSocketOut.close();
                    graphInputStream.close();
                    remoteSocket.close();
                }
                // Update sketch bloom filters
                BloomFilter newAncestors = Kernel.remoteSketches.get(remoteHost).matrixFilter.get(networkVertex);
                Graph descendants = Kernel.query("query Neo4j lineage " + storageId + "d type:* 100", false);
                for (AbstractVertex currentVertex : descendants.vertexSet()) {
                    if (currentVertex.type().equalsIgnoreCase("Network")) {
                        matrixFilter.updateAncestors(currentVertex, newAncestors);
                    }
                }
            /*
            } else if (incomingEdge.type().equalsIgnoreCase("WasGeneratedBy")
                    && incomingEdge.getSourceVertex().type().equalsIgnoreCase("Network")) {
                // Connection was established by this host
                AbstractVertex networkVertex = incomingEdge.getDestinationVertex();
                String remoteHost = networkVertex.getAnnotation("destination host");
                Socket remoteSocket = new Socket(remoteHost, Kernel.SKETCH_QUERY_PORT);
                PrintWriter remoteSocketOut = new PrintWriter(remoteSocket.getOutputStream(), true);
                ObjectOutputStream remoteSocketObjectOutputStream = new ObjectOutputStream(remoteSocket.getOutputStream());
                remoteSocketOut.println("receiveSketches");
                remoteSocketObjectOutputStream.writeObject(Kernel.remoteSketches);
                remoteSocketObjectOutputStream.close();
                remoteSocketOut.close();
                remoteSocket.close();
             * 
             */
            }
        } catch (Exception exception) {
            Logger.getLogger(ConcreteSketch.class.getName()).log(Level.SEVERE, null, exception);
        }
    }
}
