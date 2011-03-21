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

public abstract class AbstractVertex {

    protected String vertexType;
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

    public String getVertexType() {
        return vertexType;
    }

    public void setVertexType(String vertexType) {
        this.vertexType = vertexType;
    }

    @Override
    public String toString() {
        String vertexString = "";
        for (Iterator iterator = annotations.keySet().iterator(); iterator.hasNext();) {
            String key = (String) iterator.next();
            String value = (String) annotations.get(key);
            vertexString = vertexString + key + ":" + value + "|";
        }
        vertexString = vertexString.substring(0, vertexString.length() - 1);
        return vertexString;
    }

    @Override
    public boolean equals(Object thatObject) {
        if (this == thatObject) {
            return true;
        }
        if (!(thatObject instanceof AbstractVertex)) {
            return false;
        }
        AbstractVertex thatVertex = (AbstractVertex) thatObject;
        return (this.annotations.equals(thatVertex.annotations));
    }

    @Override
    public int hashCode() {
        final int seed1 = 67;
        final int seed2 = 3;
        int hashCode = seed2;
        hashCode = seed1 * hashCode + (this.vertexType != null ? this.vertexType.hashCode() : 0);
        hashCode = seed1 * hashCode + (this.annotations != null ? this.annotations.hashCode() : 0);
        return hashCode;
    }
}
