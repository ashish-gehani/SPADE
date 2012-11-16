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
import org.gephi.data.attributes.api.AttributeColumn;
import org.gephi.data.attributes.api.AttributeController;
import org.gephi.data.attributes.api.AttributeModel;
import org.gephi.data.attributes.api.AttributeOrigin;
import org.gephi.data.attributes.api.AttributeType;
import org.gephi.data.attributes.type.TimeInterval;
import org.gephi.dynamic.api.DynamicController;
import org.gephi.dynamic.api.DynamicGraph;
import org.gephi.dynamic.api.DynamicModel;
import org.gephi.graph.api.Attributes;
//import org.apache.http.client.HttpClient;
//import org.apache.http.client.methods.HttpPost;
//import org.apache.http.entity.StringEntity;
//import org.apache.http.impl.client.DefaultHttpClient;
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
import org.gephi.timeline.api.TimelineController;
import org.openide.util.Lookup;
import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;

/**
 *
 * @author Dawood Tariq
 */
public class Gephi extends AbstractStorage {

    private GraphModel graphModel;
    private DirectedGraph graph;
    private Map<AbstractVertex, Node> vertexMap;
    private AttributeColumn nodeTimeColumn;
    private AttributeColumn edgeTimeColumn;
    private final long COMMIT_DELAY = 120;
    private long startTime = 0;
    static final Logger logger = Logger.getLogger(Gephi.class.getName());

    @Override
    public boolean initialize(String arguments) {
        try {
            vertexMap = new HashMap<AbstractVertex, Node>();

            ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
            pc.newProject();
            Workspace workspace = pc.getCurrentWorkspace();
            AttributeController attributeController = Lookup.getDefault().lookup(AttributeController.class);
            AttributeModel attributeModel = attributeController.getModel();
            nodeTimeColumn = attributeModel.getNodeTable().addColumn(DynamicModel.TIMEINTERVAL_COLUMN, AttributeType.TIME_INTERVAL, AttributeOrigin.PROPERTY);
            edgeTimeColumn = attributeModel.getEdgeTable().addColumn(DynamicModel.TIMEINTERVAL_COLUMN, AttributeType.TIME_INTERVAL, AttributeOrigin.PROPERTY);
            GraphController graphController = Lookup.getDefault().lookup(GraphController.class);
            graphModel = graphController.getModel();
            graph = graphModel.getDirectedGraph();

            DynamicModel dynamicModel = Lookup.getDefault().lookup(DynamicController.class).getModel();
            DynamicGraph dynamicGraph = dynamicModel.createDynamicGraph(graph);

            TimelineController timelineController = Lookup.getDefault().lookup(TimelineController.class);
            timelineController.setEnabled(true);

            StreamingServer server = Lookup.getDefault().lookup(StreamingServer.class);
            ServerControllerFactory controllerFactory = Lookup.getDefault().lookup(ServerControllerFactory.class);
            ServerController serverController = controllerFactory.createServerController(graph);
            String context = "/spade";
            server.register(serverController, context);
            startTime = System.currentTimeMillis();

            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
//            ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
//            pc.newProject();
//            Workspace workspace = pc.getCurrentWorkspace();
//
//            GraphController graphController = Lookup.getDefault().lookup(GraphController.class);
//            graphModel = graphController.getModel();
//            graph = graphModel.getDirectedGraph();
//
//            StreamingServer server = Lookup.getDefault().lookup(StreamingServer.class);
//            ServerControllerFactory controllerFactory = Lookup.getDefault().lookup(ServerControllerFactory.class);
//            ServerController serverController = controllerFactory.createServerController(graph);
//            String context = "/spade";
//            server.register(serverController, context);

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

//        return true;
//        } catch (Exception exception) {
//            logger.log(Level.SEVERE, null, exception);
//            return false;
//        }
    }

    @Override
    public boolean shutdown() {
        return true;
    }

    @Override
    public boolean putVertex(AbstractVertex incomingVertex) {
//        try {
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
//            return true;
//        } catch (Exception exception) {
//            logger.log(Level.SEVERE, null, exception);
//            return false;
//        }

        try {
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
            for (String key : incomingVertex.getAnnotations().keySet()) {
                setAttribute(newNode.getAttributes(), key, incomingVertex.getAnnotation(key));
            }
            double vertexStart = System.currentTimeMillis() - startTime;
            TimeInterval nodeTime = new TimeInterval(vertexStart, Double.POSITIVE_INFINITY);
            newNode.getNodeData().getAttributes().setValue(nodeTimeColumn.getIndex(), nodeTime);

            vertexMap.put(incomingVertex, newNode);
            graph.addNode(newNode);

            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
    }

    @Override
    public boolean putEdge(AbstractEdge incomingEdge) {
//        try {
//            HttpClient httpClient = new DefaultHttpClient();
//            HttpPost request = new HttpPost("http://localhost:8080/workspace0?operation=updateGraph");
//            String edgeString = String.format(
//                    "{\"ae\":{\"%s\":{\"source\":\"%s\",\"target\":\"%s\",\"directed\":true}}}",
//                    incomingEdge.hashCode(),
//                    incomingEdge.getSourceVertex().hashCode(),
//                    incomingEdge.getDestinationVertex().hashCode());
//            StringEntity params = new StringEntity(edgeString);
//            request.setEntity(params);
//            httpClient.execute(request);
//            httpClient.getConnectionManager().shutdown();
//            return true;
//        } catch (Exception exception) {
//            logger.log(Level.SEVERE, null, exception);
//            return false;
//        }

        try {
            Thread.sleep(COMMIT_DELAY);
            Node src = vertexMap.get(incomingEdge.getSourceVertex());
            Node dst = vertexMap.get(incomingEdge.getDestinationVertex());
            Edge newEdge = graphModel.factory().newEdge(src, dst, 1.0f, true);
            newEdge.getEdgeData().setLabel(incomingEdge.toString());
            newEdge.getEdgeData().setColor(0.0f, 0.0f, 0.0f);
            for (String key : incomingEdge.getAnnotations().keySet()) {
                setAttribute(newEdge.getAttributes(), key, incomingEdge.getAnnotation(key));
            }
            double edgeStart = System.currentTimeMillis() - startTime;
            TimeInterval edgeTime = new TimeInterval(edgeStart, Double.POSITIVE_INFINITY);
            newEdge.getEdgeData().getAttributes().setValue(edgeTimeColumn.getIndex(), edgeTime);

            graph.addEdge(newEdge);

            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
    }

    private void setAttribute(Attributes attributes, String key, String value) {
        try {
            Long longValue = Long.parseLong(value);
            attributes.setValue(key, longValue);
        } catch (NumberFormatException parseLongException) {
            try {
                Double doubleValue = Double.parseDouble(value);
                attributes.setValue(key, doubleValue);
            } catch (NumberFormatException parseDoubleException) {
                attributes.setValue(key, value);
            }
        }
    }
}
