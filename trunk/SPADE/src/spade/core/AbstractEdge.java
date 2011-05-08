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

    // The AbstractEdge class is from which other edge classes (e.g., OPM edges)
    // are derived.

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

    // The following functions that get and set source and destination vertices
    // are left empty in this abstract class - they are overridden and implemented
    // in derived classes since the source and destination vertex types may be
    // specific to those classes.

    public AbstractVertex getSrcVertex() {
        return null;
    }

    public AbstractVertex getDstVertex() {
        return null;
    }

    public void setSrcVertex(AbstractVertex sourceVertex) {
        return;
    }

    public void setDstVertex(AbstractVertex destinationVertex) {
        return;
    }

    // The toString method is used to generate a single string of all the annotations
    // separated by commas. This is used in some storages (i.e., currently by Graphviz)
    // and may also be used by visualizations (work in progress).

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
        final int seed1 = 53;
        final int seed2 = 7;
        int hashCode = seed2;
        hashCode = seed1 * hashCode + (this.getSrcVertex() != null ? this.getSrcVertex().hashCode() : 0);
        hashCode = seed1 * hashCode + (this.getDstVertex() != null ? this.getDstVertex().hashCode() : 0);
        hashCode = seed1 * hashCode + (this.annotations != null ? this.annotations.hashCode() : 0);
        return hashCode;
    }
}
