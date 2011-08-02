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
package spade.core;

import java.io.Serializable;
import java.util.Map;

public abstract class AbstractEdge implements Serializable  {

    // The AbstractEdge class is from which other edge classes (e.g., OPM edges)
    // are derived.
    protected Map<String, String> annotations;
    private AbstractVertex sourceVertex;
    private AbstractVertex destinationVertex;
    public Graph resultGraph;

    public final Map<String, String> getAnnotations() {
        return annotations;
    }

    public final void setAnnotations(Map<String, String> annotations) {
        this.annotations = annotations;
    }

    public final void addAnnotation(String key, String value) {
        annotations.put(key, value);
    }

    public final String removeAnnotation(String key) {
        return annotations.remove(key);
    }

    public final String getAnnotation(String key) {
        return annotations.get(key);
    }

    public final String type() {
        return annotations.get("type");
    }

    // The following functions that get and set source and destination vertices
    // are left empty in this abstract class - they are overridden and implemented
    // in derived classes since the source and destination vertex types may be
    // specific to those classes.
    public final AbstractVertex getSourceVertex() {
        return sourceVertex;
    }

    public final AbstractVertex getDestinationVertex() {
        return destinationVertex;
    }

    public final void setSourceVertex(AbstractVertex sourceVertex) {
        this.sourceVertex = sourceVertex;
    }

    public final void setDestinationVertex(AbstractVertex destinationVertex) {
        this.destinationVertex = destinationVertex;
    }

    @Override
    public boolean equals(Object thatObject) {
        if (this == thatObject) {
            return true;
        }
        if (!(thatObject instanceof AbstractEdge)) {
            return false;
        }
        AbstractEdge thatEdge = (AbstractEdge) thatObject;
        return (this.annotations.equals(thatEdge.annotations)
                && this.getSourceVertex().equals(thatEdge.getSourceVertex())
                && this.getDestinationVertex().equals(thatEdge.getDestinationVertex()));
    }

    @Override
    public int hashCode() {
        final int seed1 = 53;
        final int seed2 = 7;
        int hashCode = seed2;
        hashCode = seed1 * hashCode + (this.getSourceVertex() != null ? this.getSourceVertex().hashCode() : 0);
        hashCode = seed1 * hashCode + (this.getDestinationVertex() != null ? this.getDestinationVertex().hashCode() : 0);
        hashCode = seed1 * hashCode + (this.annotations != null ? this.annotations.hashCode() : 0);
        return hashCode;
    }

    @Override
    public String toString() {
        String result = "";
        for (Map.Entry<String, String> currentEntry : annotations.entrySet()) {
            result += currentEntry.getKey() + "|" + currentEntry.getValue();
        }
        result = result.substring(0, result.length() - 1);
        return result;
    }
}
