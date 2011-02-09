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

import java.util.Map;
import java.util.Iterator;

public abstract class Vertex {

    protected String vertexType;
    protected Map<String, String> annotations;

    public Map<String, String> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(Map<String, String> annotations) {
        this.annotations = annotations;
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

    public String getVertexType() {
        return vertexType;
    }

    @Override
    public String toString() {
        String vertexstring = "";
        for (Iterator it = annotations.keySet().iterator(); it.hasNext();) {
            String name = (String) it.next();
            String value = (String) annotations.get(name);
            vertexstring = vertexstring + name + ":" + value + "|";
        }
        vertexstring = vertexstring.substring(0, vertexstring.length() - 1);
        return vertexstring;
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (!(that instanceof Vertex)) {
            return false;
        }
        Vertex thatV = (Vertex) that;
        return (this.annotations.equals(thatV.annotations));
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 67 * hash + (this.vertexType != null ? this.vertexType.hashCode() : 0);
        hash = 67 * hash + (this.annotations != null ? this.annotations.hashCode() : 0);
        return hash;
    }
}
