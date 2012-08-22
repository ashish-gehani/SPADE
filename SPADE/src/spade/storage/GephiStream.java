/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2012 SRI International

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
package spade.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.gephi.graph.api.DirectedGraph;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.project.api.Project;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.gephi.streaming.server.ServerController;
import org.gephi.streaming.server.ServerControllerFactory;
import org.gephi.streaming.server.StreamingServer;
import org.openide.util.Lookup;
import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;

/**
 *
 * @author Dawood Tariq
 */
public class GephiStream extends AbstractStorage {

    private GraphModel graphModel;
    private DirectedGraph graph;
    private Map<AbstractVertex, Node> vertexMap;
    private final long COMMIT_DELAY = 120;
    static final Logger logger = Logger.getLogger(GephiStream.class.getName());

    @Override
    public boolean initialize(String arguments) {
        vertexMap = new HashMap<AbstractVertex, Node>();
        try {
            ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
            pc.newProject();
            Workspace workspace = pc.getCurrentWorkspace();

            GraphController graphController = Lookup.getDefault().lookup(GraphController.class);
            graphModel = graphController.getModel();
            graph = graphModel.getDirectedGraph();

            StreamingServer server = Lookup.getDefault().lookup(StreamingServer.class);
            ServerControllerFactory controllerFactory = Lookup.getDefault().lookup(ServerControllerFactory.class);
            ServerController serverController = controllerFactory.createServerController(graph);
            String context = "/spade";
            server.register(serverController, context);

//////////////////////////////////////////////////////////////////////////////////////////////////////////////

//            ProjectController projectController = Lookup.getDefault().lookup(ProjectController.class);
//            Project project = projectController.getCurrentProject();
//            Workspace workspace = projectController.getCurrentWorkspace();
//            // Get the graph instance
//            GraphController graphController = Lookup.getDefault().lookup(GraphController.class);
//            graphModel = graphController.getModel();
//            graph = graphModel.getDirectedGraph();
//
//            // Connect to stream using the Streaming API
//            StreamingController controller = Lookup.getDefault().lookup(StreamingController.class);
//            StreamingEndpoint endpoint = new StreamingEndpoint();
//            endpoint.setUrl(new URL("http://localhost:8080/workspace0"));
//            endpoint.setStreamType(controller.getStreamType("JSON"));
//            StreamingConnection connection = controller.connect(endpoint, graph);
//            connection.asynchProcess();

            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
    }

    @Override
    public boolean shutdown() {
        return true;
    }

    @Override
    public boolean putVertex(AbstractVertex incomingVertex) {
        try {
//            HttpClient httpClient = new DefaultHttpClient();
//            HttpPost request = new HttpPost("http://localhost:8080/workspace0?operation=updateGraph");
//            String vertexString = String.format(
//                    "{\"an\":{\"%s\":{\"label\":\"%s\"}}}",
//                    incomingVertex.hashCode(),
//                    incomingVertex.toString());
//            StringEntity params = new StringEntity(vertexString);
//            request.setEntity(params);
//            httpClient.execute(request);
//            httpClient.getConnectionManager().shutdown();

            Node newNode = graphModel.factory().newNode();
            newNode.getNodeData().setLabel(incomingVertex.toString());
            newNode.getNodeData().setSize(10.0f);
            if (incomingVertex.type().equals("Process")) {
                newNode.getNodeData().setColor(0.0f, 0.0f, 1.0f);
            } else if (incomingVertex.type().equals("Artifact")) {
                newNode.getNodeData().setColor(0.9f, 0.8f, 0.4f);
            } else if (incomingVertex.type().equals("Agent")) {
                newNode.getNodeData().setColor(1.0f, 0.4f, 0.4f);
            }
            vertexMap.put(incomingVertex, newNode);
            graph.addNode(newNode);
            Thread.sleep(COMMIT_DELAY);

            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
    }

    @Override
    public boolean putEdge(AbstractEdge incomingEdge) {
        try {
//            HttpClient httpClient = new DefaultHttpClient();
//            HttpPost request = new HttpPost("http://localhost:8080/workspace0?operation=updateGraph");
//            String edgeString = String.format(
//                    "{\"ae\":{\"%s\":{\"source\":\"%s\",\"target\":\"%s\",\"directed\":false}}}",
//                    incomingEdge.hashCode(),
//                    incomingEdge.getSourceVertex().hashCode(),
//                    incomingEdge.getDestinationVertex().hashCode());
//            StringEntity params = new StringEntity(edgeString);
//            request.setEntity(params);
//            httpClient.execute(request);
//            httpClient.getConnectionManager().shutdown();

            Node src = vertexMap.get(incomingEdge.getSourceVertex());
            Node dst = vertexMap.get(incomingEdge.getDestinationVertex());
            Edge edge = graphModel.factory().newEdge(src, dst, 1.0f, true);
            edge.getEdgeData().setLabel(incomingEdge.toString());
            edge.getEdgeData().setColor(0.0f, 0.0f, 0.0f);
            graph.addEdge(edge);
            Thread.sleep(COMMIT_DELAY);

            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
    }
}
