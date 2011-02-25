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

import java.util.Map;
import java.util.Iterator;

public abstract class AbstractEdge {

    protected String edgeType;
    protected Map<String, String> annotations;

    public Map<String, String> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(Map<String, String> annotations) {
        this.annotations = annotations;
    }

    public boolean addAnnotation(String key, String value) {
        if (!annotations.containsKey(key)) {
            annotations.put(key, value);
            return true;
        } else {
            annotations.put(key, value);
            return false;
        }
    }

    public boolean removeAnnotation(String key) {
        if (annotations.containsKey(key)) {
            annotations.remove(key);
            return true;
        } else {
            return false;
        }
    }

    public String getAnnotation(String key) {
        return annotations.get(key);
    }

    public String getEdgeType() {
        return edgeType;
    }

    public void setEdgeType(String edgeType) {
        this.edgeType = edgeType;
    }

    public AbstractVertex getSrcVertex() {
        return null;
    }

    public AbstractVertex getDstVertex() {
        return null;
    }

    @Override
    public String toString() {
        String annotationString = "";
        for (Iterator iterator = annotations.keySet().iterator(); iterator.hasNext();) {
            String key = (String) iterator.next();
            String value = (String) annotations.get(key);
            if (key.equals("type")) {
                continue;
            }
            annotationString = annotationString + key + ":" + value + ", ";
        }
        if (annotationString.length() > 3) {
            annotationString = "(" + annotationString.substring(0, annotationString.length() - 2) + ")";
        }
        return annotationString;
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
                && this.getSrcVertex().equals(thatEdge.getSrcVertex())
                && this.getDstVertex().equals(thatEdge.getDstVertex()));
    }

    @Override
    public int hashCode() {
        final int hashInt = 53;
        int hashCode = 7;
        hashCode = hashInt * hashCode + (this.getSrcVertex() != null ? this.getSrcVertex().hashCode() : 0);
        hashCode = hashInt * hashCode + (this.getDstVertex() != null ? this.getDstVertex().hashCode() : 0);
        hashCode = hashInt * hashCode + (this.edgeType != null ? this.edgeType.hashCode() : 0);
        hashCode = hashInt * hashCode + (this.annotations != null ? this.annotations.hashCode() : 0);
        return hashCode;
    }
}
