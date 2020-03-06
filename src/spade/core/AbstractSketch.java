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
package spade.core;

import java.io.Serializable;
import java.util.Map;

/**
 * This is the base class for sketches.
 *
 * @author Dawood Tariq
 */
public abstract class AbstractSketch implements Serializable {

	private static final long serialVersionUID = -3747042008910150947L;
	
	/**
     * The matrix filter belonging to this sketch.
     */
    private MatrixFilter matrixFilter;
    /**
     * A generic map used to store objects for various purposes (querying, etc.)
     */
    private Map<String, Object> objects;
    
    public AbstractSketch(MatrixFilter matrixFilter, Map<String, Object> objects)
    {
        this.matrixFilter = matrixFilter;
        this.objects = objects;
    }
    
    public MatrixFilter getMatrixFilter(){
		return matrixFilter;
	}

	public Map<String, Object> getObjects(){
		return objects;
	}

	/**
     * This method is triggered when the sketch receives a vertex.
     *
     * @param incomingVertex The vertex received by this sketch.
     */
    public abstract void putVertex(AbstractVertex incomingVertex);

    /**
     * This method is triggered when the sketch receives an edge.
     *
     * @param incomingEdge The edge received by this sketch.
     */
    public abstract void putEdge(AbstractEdge incomingEdge);
}
