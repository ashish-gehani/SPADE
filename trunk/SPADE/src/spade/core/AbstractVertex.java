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

public abstract class AbstractVertex {

    // The AbstractVertex class is from which other vertex classes (e.g., OPM
    // vertices) are derived.
    protected Map<String, String> annotations;

    public final Map<String, String> getAnnotations() {
        return annotations;
    }

    public final void setAnnotations(Map<String, String> annotations) {
        this.annotations = annotations;
    }

    public final void addAnnotation(String key, String value) {
        annotations.put(key, value);
    }

    public final void removeAnnotation(String key) {
        annotations.remove(key);
    }

    public final String getAnnotation(String key) {
        return annotations.get(key);
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
        hashCode = seed1 * hashCode + (this.annotations != null ? this.annotations.hashCode() : 0);
        return hashCode;
    }
}
