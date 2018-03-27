/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2016 SRI International

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
 package spade.reporter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import spade.core.AbstractEdge;
import spade.core.AbstractReporter;
import spade.core.AbstractVertex;
import spade.edge.prov.ActedOnBehalfOf;
import spade.edge.prov.Used;
import spade.edge.prov.WasAssociatedWith;
import spade.edge.prov.WasAttributedTo;
import spade.edge.prov.WasDerivedFrom;
import spade.edge.prov.WasGeneratedBy;
import spade.edge.prov.WasInformedBy;
import spade.vertex.prov.Activity;
import spade.vertex.prov.Agent;
import spade.vertex.prov.Entity;

/**
 * JSON reporter for SPADE
 *
 * @author Hasanat Kazmi
 */
public class JSON extends AbstractReporter {

    private boolean shutdown = false;
    private boolean PRINT_DEBUG = true;
    private HashMap<String, AbstractVertex> vertices;

    @Override
    public boolean launch(final String arguments) {
        /*
        * argument is path to json file
        */
        vertices = new HashMap<String, AbstractVertex>();

        Runnable eventThread = new Runnable() {
            public void run() {

                String file_path = arguments.trim();
                String line;
                StringBuffer jsonString = new StringBuffer();
                BufferedReader br;
                JSONArray jsonArray;
                try {
                  debugLog("Starting to read json file");
                  br = new BufferedReader(new InputStreamReader(new FileInputStream(file_path)));
                  while ((line = br.readLine()) != null) {
                      jsonString.append(line);
                  }
                  br.close();
                  debugLog("Sucessfully read json file");

                } catch (IOException e) {
                    JSON.log(Level.SEVERE, "Can't open and read json file.", e);
                }

                debugLog("Starting to objectify JSON content");
                try {
                  jsonArray = new JSONArray(jsonString.toString());
                } catch (JSONException e){
                  JSON.log(Level.SEVERE, "Failed to create JSON Array object", e);
                  return;
                }
                debugLog("Sucessfully objectified JSON content");

                processJsonArray(jsonArray);
              }
        };
        new Thread(eventThread, "JsonReporter-Thread").start();
        return true;
    }

    @Override
    public boolean shutdown() {
        shutdown=true;
        return true;
    }

    private void processJsonArray(JSONArray jsonArray) {
      debugLog("Size of JSON Array: " + jsonArray.length());
      for (int i=0; i<jsonArray.length();i++) {
        JSONObject jsonObject;
        try {
          jsonObject = jsonArray.getJSONObject(i);
        } catch (JSONException e) {
          JSON.log(Level.SEVERE, "Can not read object in JSON Array", e);
          continue;
        }

        String objectType;
        try {
          objectType = jsonObject.getString("type");
        } catch (JSONException e) {
          JSON.log(Level.SEVERE, "Missing type in object, can not access if its node or edge, ignoring object", null);
          continue;
        }

        if (objectType.equalsIgnoreCase("Activity") ||
          objectType.equalsIgnoreCase("Agent") ||
          objectType.equalsIgnoreCase("Entity")
        ) {
          processVertex(jsonObject);
        } else if (objectType.equalsIgnoreCase("ActedOnBehalfOf") ||
          objectType.equalsIgnoreCase("Used") ||
          objectType.equalsIgnoreCase("WasAssociatedWith") ||
          objectType.equalsIgnoreCase("WasAttributedTo") ||
          objectType.equalsIgnoreCase("WasDerivedFrom") ||
          objectType.equalsIgnoreCase("WasGeneratedBy") ||
          objectType.equalsIgnoreCase("WasInformedBy")
        ){
          processEdge(jsonObject);
        } else {
          JSON.log(Level.SEVERE, "Unknown object type: '" + objectType + "', ignoring object", null);
        }
      }
      debugLog("All provenance reported through JSON file has been retrived. Wait for buffers to clear....");

      try {
        while (this.getBuffer().size()!=0) {
          Thread.sleep(1000);
          debugLog("Size of buffer: " + this.getBuffer().size());
        }
      } catch (Exception e){}

      debugLog("All buffers cleared. You may remove JSON reporter");

    }

    private void processVertex(JSONObject vertexObject) {
      // Activity, Agent, Entity
      String id = null;
      
      try {
    	Object idValue = vertexObject.get("id");
    	if(idValue == null){
    		throw new JSONException("");
    	}
        id = String.valueOf(idValue);
      } catch (JSONException e) {
        JSON.log(Level.SEVERE, "Missing id in vertex, ignoring vertex : " + vertexObject.toString() , null);
        return;
      }

      String vertexType;
      try {
        vertexType = vertexObject.getString("type");
      } catch (JSONException e) {
        // this is already been checked
        return;
      }

      AbstractVertex vertex = null;
      if (vertexType.equalsIgnoreCase("Activity")) {
        vertex = new Activity();
      } else if (vertexType.equalsIgnoreCase("Agent")) {
        vertex = new Agent();
      } else if (vertexType.equalsIgnoreCase("Entity")) {
        vertex = new Entity();
      }

      JSONObject annotationsObject;
      try {
        annotationsObject = vertexObject.getJSONObject("annotations");
        if (annotationsObject.length()!=0) {
          for(Iterator iterator = annotationsObject.keys(); iterator.hasNext();) {
            String key = (String) iterator.next();
            String value = annotationsObject.getString(key);
            vertex.addAnnotation(key, value);
          }
        }
      } catch (JSONException e) {
        // no annotations
      }

      vertices.put(id, vertex);
      putVertex(vertex);
    }

    private void processEdge(JSONObject edgeObject) {
      String from;
      try {
        Object fromValue = edgeObject.get("from");
        if(fromValue == null){
        	throw new JSONException("");
        }
        from = String.valueOf(fromValue);
      } catch (JSONException e) {
        JSON.log(Level.SEVERE, "Missing 'from' in edge, ignoring edge : " + edgeObject.toString() , null);
        return;
      }

      String to;
      try {
        Object toValue = edgeObject.get("to");
        if(toValue == null){
        	throw new JSONException("");
        }
        to = String.valueOf(toValue);
      } catch (JSONException e) {
        JSON.log(Level.SEVERE, "Missing 'to' in edge, ignoring edge : " + edgeObject.toString() , null);
        return;
      }

      AbstractVertex fromVertex = vertices.get(from);
      AbstractVertex toVertex = vertices.get(to);

      if (fromVertex == null || toVertex == null) {
        JSON.log(Level.SEVERE, "Starting and/or ending vertex of edge hasn't been seen before, ignoring edge : " + edgeObject.toString() , null);
        return;
      }

      String edgeType;
      try {
        edgeType = edgeObject.getString("type");
      } catch (JSONException e) {
        // this is already been checked
        return;
      }

      AbstractEdge edge = null;
      if (edgeType.equalsIgnoreCase("ActedOnBehalfOf")) {
        edge = new ActedOnBehalfOf((Agent) fromVertex, (Agent) toVertex);
      } else if (edgeType.equalsIgnoreCase("WasAttributedTo")) {
        edge = new WasAttributedTo((Entity) fromVertex, (Agent) toVertex);
      } else if (edgeType.equalsIgnoreCase("WasDerivedFrom")) {
        edge = new WasDerivedFrom((Entity) fromVertex, (Entity) toVertex);
      } else if (edgeType.equalsIgnoreCase("WasGeneratedBy")) {
        edge = new WasGeneratedBy((Entity) fromVertex, (Activity) toVertex);
      } else if (edgeType.equalsIgnoreCase("WasInformedBy")) {
        edge = new WasInformedBy((Activity) fromVertex, (Activity) toVertex);
      } else if (edgeType.equalsIgnoreCase("Used")) {
        edge = new Used((Activity) fromVertex, (Entity) toVertex);
      } else if (edgeType.equalsIgnoreCase("WasAssociatedWith")) {
        edge = new WasAssociatedWith((Activity) fromVertex, (Agent) toVertex);
      }

      JSONObject annotationsObject;
      try {
        annotationsObject = edgeObject.getJSONObject("annotations");
        if (annotationsObject.length()!=0) {
          for(Iterator iterator = annotationsObject.keys(); iterator.hasNext();) {
            String key = (String) iterator.next();
            String value = annotationsObject.getString(key);
            edge.addAnnotation(key, value);
          }
        }

      } catch (JSONException e) {
        // no annotations
      }

      putEdge(edge);
    }

    public void debugLog(String msg) {
      if (PRINT_DEBUG == true) {
        JSON.log(Level.INFO, msg, null);
      }
    }

    public static void log(Level level, String msg, Throwable thrown) {
        if (level == level.FINE) {
        } else {
            Logger.getLogger(JSON.class.getName()).log(level, msg, thrown);
        }
    }
    
}
