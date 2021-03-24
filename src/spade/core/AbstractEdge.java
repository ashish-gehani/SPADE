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
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import spade.utility.HelperFunctions;

/**
 * This is the class from which other edge classes (e.g., OPM edges) are
 * derived.
 *
 * @author Dawood Tariq
 */
public abstract class AbstractEdge implements Serializable{

	private static final long serialVersionUID = -2920945968273904093L;

	public static final String hashKey = AbstractVertex.hashKey;
	public static final String annotationsKey = AbstractVertex.annotationsKey;
	public static final String typeKey = AbstractVertex.typeKey;
	public static final String idKey = AbstractVertex.idKey;
	public static final String childVertexKey = "childVertex";
	public static final String parentVertexKey = "parentVertex";
	public static final String fromIdKey = "from"; // child
	public static final String toIdKey = "to"; // parent

	/**
     * A map containing the annotations for this edge.
     */
    private final Map<String, String> annotations = new TreeMap<>();
    private AbstractVertex childVertex;
    private AbstractVertex parentVertex;

    /**
     * String big hash to be returned by bigHashCode function only if not null.
     * If null then big hash computed using the annotations map.
     */
    private final String bigHashCode;
    
    /**
     * Create a vertex without a fixed big hash.
     */
    public AbstractEdge(){
    	this.bigHashCode = null;
    }
    
    /**
     * Create a vertex with a fixed big hash.
     * 
     * @param hexHashString String
     */
    public AbstractEdge(String hexHashString){
    	if(!HashHelper.defaultInstance.isValidHashHexString(hexHashString)){
    		throw new RuntimeException("Invalid Edge hash. "
    				+ "Mismatch in hex hash string '"+hexHashString+"' and hash algorithm " + HashHelper.defaultInstance);
    	}
    	this.bigHashCode = hexHashString;
    }
    
    /**
     * Returns true if the vertex has a fixed big hash otherwise false
     * 
     * @return true/false
     */
    public final boolean isReferenceEdge(){
    	return bigHashCode != null;
    }

    /**
     * Returns the map containing the annotations for this edge.
     *
     * @return The map containing the annotations.
     */
    public final Map<String, String> getCopyOfAnnotations() {
        return new HashMap<String, String>(annotations);
    }
    
    /**
     * Adds an annotation.
     *
     * @param key The annotation key.
     * @param value The annotation value.
     */
	public void addAnnotation(String key, String value){
		if(!HelperFunctions.isNullOrEmpty(key)){
			if(value == null){
				value = "";
			}
			annotations.put(key, value);
		}
	}

    /**
     * Adds a map of annotation.
     *
     * @param newAnnotations New annotations to be added.
     */
	public void addAnnotations(Map<String, String> newAnnotations){
		for(Map.Entry<String, String> currentEntry : newAnnotations.entrySet()){
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
    public final String removeAnnotation(String key){
        return annotations.remove(key);
    }

    /**
     * Gets an annotation.
     *
     * @param key The annotation key.
     * @return The value of the annotation corresponding to the key.
     */
    public final String getAnnotation(String key){
        return annotations.get(key);
    }

    /**
     * Gets the type of this edge.
     *
     * @return A string indicating the type of this edge.
     */
    public final String type(){
        return annotations.get(typeKey);
    }
    
    /**
     * Sets the type of this edge
     * 
     * @param typeValue Must be a non-null string otherwise converted to empty string
     */
    protected final void setType(final String typeValue){
    	addAnnotation(typeKey, typeValue);
    }
    
    /**
     * Gets the id of this edge.
     *
     * @return A string indicating the id of this edge (if any).
     */
    public final String id(){
        return annotations.get(idKey);
    }

    // The following functions that get and set source and destination vertices
    // are left empty in this abstract class - they are overridden and implemented
    // in derived classes since the source and destination vertex types may be
    // specific to those classes.
    /**
     * Gets the source vertex.
     *
     * @return The source vertex attached to this edge.
     */
    public final AbstractVertex getChildVertex() {
        return childVertex;
    }

    /**
     * Gets the destination vertex.
     *
     * @return The destination vertex attached to this edge.
     */
    public final AbstractVertex getParentVertex() {
        return parentVertex;
    }

    /**
     * Sets the source vertex.
     *
     * @param childVertex The vertex that is to be set as the source for this
     * edge.
     */
    public final void setChildVertex(AbstractVertex childVertex) {
        this.childVertex = childVertex;
    }

    /**
     * Sets the destination vertex.
     *
     * @param parentVertex The vertex that is to be set as the destination
     * for this edge.
     */
    public final void setParentVertex(AbstractVertex parentVertex) {
        this.parentVertex = parentVertex;
    }

    /**
     * Computes hash of annotations in the edge and hashes of the endpoints according to the default set in spade.core.HashHelper.
     * If the hash was fixed then that is used.
     */
	public final String bigHashCode(){
		if(bigHashCode == null){
			final String data = 
					((childVertex == null) ? "(null)" : childVertex.bigHashCode()) + ","
					+ annotations.toString() + ","
					+ ((parentVertex == null) ? "(null)" : parentVertex.bigHashCode());
			return HashHelper.defaultInstance.hashToHexString(data);
		}else{
			return bigHashCode;
		}
	}

	public final byte[] bigHashCodeBytes(){
		return HashHelper.defaultInstance.convertHashHexStringToHashByteArray(bigHashCode());
    }

    @Override
	public boolean equals(Object obj){
    	if(this == obj)
			return true;
		if(obj == null)
			return false;
		AbstractEdge other = (AbstractEdge) obj;
		return bigHashCode().equals(other.bigHashCode());
	}

    @Override
	public int hashCode(){
    	final int prime = 31;
		int result = 1;
		result = prime * result + bigHashCode().hashCode();
		return result;
	}

	@Override
	public final String toString(){
		return "AbstractEdge{" 
				+ hashKey + "=" + bigHashCode() + ", " 
				+ childVertexKey + "=" + childVertex + ", "
				+ parentVertexKey + "=" + parentVertex + ", "
				+ annotationsKey + "=" + annotations
				+ "}";
	}
	
	public static final boolean isEdgeType(String type){
		if(HelperFunctions.isNullOrEmpty(type)){
			return false;
		}
		type = type.trim();
		if(	// generic vertex
			type.equalsIgnoreCase(spade.core.Edge.typeValue)
			// prov
			|| type.equalsIgnoreCase(spade.edge.prov.ActedOnBehalfOf.typeValue)
			|| type.equalsIgnoreCase(spade.edge.prov.Used.typeValue)
			|| type.equalsIgnoreCase(spade.edge.prov.WasAssociatedWith.typeValue)
			|| type.equalsIgnoreCase(spade.edge.prov.WasAttributedTo.typeValue)
			|| type.equalsIgnoreCase(spade.edge.prov.WasDerivedFrom.typeValue)
			|| type.equalsIgnoreCase(spade.edge.prov.WasGeneratedBy.typeValue)
			|| type.equalsIgnoreCase(spade.edge.prov.WasInformedBy.typeValue)
			// opm
			|| type.equalsIgnoreCase(spade.edge.opm.Used.typeValue)
			|| type.equalsIgnoreCase(spade.edge.opm.WasControlledBy.typeValue)
			|| type.equalsIgnoreCase(spade.edge.opm.WasDerivedFrom.typeValue)
			|| type.equalsIgnoreCase(spade.edge.opm.WasGeneratedBy.typeValue)
			|| type.equalsIgnoreCase(spade.edge.opm.WasTriggeredBy.typeValue)
			// cdm
			|| type.equalsIgnoreCase(spade.edge.cdm.SimpleEdge.typeValue)
			){
			return true;
		}else{
			return false;
		}
	}
}
