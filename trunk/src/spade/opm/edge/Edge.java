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

package spade.opm.edge;

import spade.opm.vertex.Vertex;
import java.util.Map;
import java.util.Iterator;

public abstract class Edge {

    protected String edgeType;
    protected Map<String, String> annotations;

    public Map<String, String> getAnnotations() {
        return annotations;
    }

    public boolean addAnnotation(String inputKey, String inputValue) {
        if (!annotations.containsKey(inputKey)) {
            annotations.put(inputKey, inputValue);
            return true;
        } else {
            annotations.put(inputKey, inputValue);
            return false;
        }
    }

    public boolean removeAnnotation(String inputKey) {
        if (annotations.containsKey(inputKey)) {
            annotations.remove(inputKey);
            return true;
        } else {
            return false;
        }
    }

    public String getAnnotationValue(String inputKey) {
        return annotations.get(inputKey);
    }

    public String getEdgeType() {
        return edgeType;
    }

    public Vertex getSrcVertex() {
        return null;
    }

    public Vertex getDstVertex() {
        return null;
    }

    @Override
    public String toString() {
        String annotationstring = "";
        for (Iterator it = annotations.keySet().iterator(); it.hasNext();) {
            String name = (String) it.next();
            String value = (String) annotations.get(name);
            if (name.equals("type")) {
                continue;
            }
            annotationstring = annotationstring + name + ":" + value + ", ";
        }
        if (annotationstring.length() > 3) {
            annotationstring = getEdgeType() + " (" + annotationstring.substring(0, annotationstring.length() - 2) + ")";
        } else {
            annotationstring = getEdgeType();
        }
        return annotationstring;
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (!(that instanceof Edge)) {
            return false;
        }
        Edge thatE = (Edge) that;
        return (this.annotations.equals(thatE.annotations)
                && this.getSrcVertex().equals(thatE.getSrcVertex())
                && this.getDstVertex().equals(thatE.getDstVertex()));
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + (this.getSrcVertex() != null ? this.getSrcVertex().hashCode() : 0);
        hash = 53 * hash + (this.getDstVertex() != null ? this.getDstVertex().hashCode() : 0);
        hash = 53 * hash + (this.edgeType != null ? this.edgeType.hashCode() : 0);
        hash = 53 * hash + (this.annotations != null ? this.annotations.hashCode() : 0);
        return hash;
    }
}
