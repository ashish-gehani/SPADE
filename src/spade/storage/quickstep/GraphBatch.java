/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2018 SRI International

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
package spade.storage.quickstep;

import java.util.ArrayList;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;

/**
 * Encapsulation of a batch of vertices/edges.
 */
public class GraphBatch {
  private long batchID = 1;
  private ArrayList<AbstractVertex> vertices = new ArrayList<AbstractVertex>();
  private ArrayList<AbstractEdge> edges = new ArrayList<AbstractEdge>();

  /**
   * Clear batch buffer and increase batch ID counter.
   */
  public void reset() {
    vertices.clear();
    edges.clear();
  }

  /**
   * Swap buffer with another batch.
   */
  public void swap(GraphBatch other) {
    ArrayList<AbstractVertex> tmpVertices = vertices;
    vertices = other.vertices;
    other.vertices = tmpVertices;
    ArrayList<AbstractEdge> tmpEdges = edges;
    edges = other.edges;
    other.edges = tmpEdges;
  }

  /**
   * Increase current batch ID by 1.
   */
  public void increaseBatchID() {
    ++batchID;
  }

  /**
   * Set current batch ID.
   */
  public void setBatchID(final long batchID) {
    this.batchID = batchID;
  }

  /**
   * @return Current batch ID.
   */
  public long getBatchID() {
    return batchID;
  }

  /**
   * @return Whether current batch is empty.
   */
  public boolean isEmpty() {
    return vertices.isEmpty() && edges.isEmpty();
  }

  /**
   * Add a vertex into current batch.
   */
  public void addVertex(AbstractVertex vertex) {
    vertices.add(vertex);
  }

  /**
   * Add an edge into current batch.
   */
  public void addEdge(AbstractEdge edge) {
    edges.add(edge);
  }

  /**
   * Get all vertices from current batch.
   */
  public ArrayList<AbstractVertex> getVertices() {
    return vertices;
  }

  /**
   * Get all edges from current batch.
   * @return
   */
  public ArrayList<AbstractEdge> getEdges() {
    return edges;
  }
}