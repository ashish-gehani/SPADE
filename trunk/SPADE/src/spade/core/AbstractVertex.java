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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This is the class from which other vertex classes (e.g., OPM vertices) are
 * derived.
 *
 * @author Dawood
 */
public abstract class AbstractVertex implements Serializable {

    /**
     * A map containing the annotations for this vertex.
     */
    protected Map<String, String> annotations = new LinkedHashMap<String, String>();
    /**
     * A pointer to the Graph object that this vertex belongs to.
     */
    public Graph resultGraph;

    /**
     * Returns the map containing the annotations for this vertex.
     *
     * @return The map containing the annotations.
     */
    public final Map<String, String> getAnnotations() {
        return annotations;
    }

    /**
     * Adds an annotation.
     *
     * @param key The annotation key.
     * @param value The annotation value.
     */
    public final void addAnnotation(String key, String value) {
        if (key == null || value == null) {
            return;
        }
        annotations.put(key, value);
    }

    /**
     * Adds a map of annotation.
     *
     * @param newAnnotations New annotations to be added.
     */
    public final void addAnnotations(Map<String, String> newAnnotations) {
        for (Map.Entry<String, String> currentEntry : newAnnotations.entrySet()) {
            String key = currentEntry.getKey();
            String value = currentEntry.getValue();
            addAnnotation(key, value);
        }
    }

    /**
     * Removes an annotation.
     *
     * @param key The annotation key to be removed.
     * @return The annotation that is removed, or null of no such annotation key
     * existed.
     */
    public final String removeAnnotation(String key) {
        return annotations.remove(key);
    }

    /**
     * Gets an annotation.
     *
     * @param key The annotation key.
     * @return The value of the annotation corresponding to the key.
     */
    public final String getAnnotation(String key) {
        return annotations.get(key);
    }

    /**
     * Gets the type of this vertex.
     *
     * @return A string indicating the type of this vertex.
     */
    public final String type() {
        return annotations.get("type");
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

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> currentEntry : annotations.entrySet()) {
            result.append(currentEntry.getKey());
            result.append(":");
            result.append(currentEntry.getValue());
            result.append("|");
        }
        return result.substring(0, result.length() - 1);
    }
}
