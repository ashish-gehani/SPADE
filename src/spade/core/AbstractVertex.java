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
import java.util.TreeMap;

import spade.utility.HelperFunctions;

/**
 * This is the class from which other vertex classes (e.g., OPM vertices) are
 * derived.
 *
 * @author Dawood Tariq
 */
public abstract class AbstractVertex implements Serializable{

	private static final long serialVersionUID = 2395150635994083355L;

	public static final String hashKey = "hash";
	public static final String annotationsKey = "annotations";
	public static final String typeKey = "type";
	public static final String idKey = "id";

	/**
     * A map containing the annotations for this vertex.
     */
    private final Map<String, String> annotations = new TreeMap<>();

    /**
     * String big hash to be returned by bigHashCode function only if not null.
     * If null then big hash computed using the annotations map.
     */
    private final String bigHashCode;

    /**
     * Create a vertex without a fixed big hash.
     */
    public AbstractVertex(){
    	this.bigHashCode = null;
    }
    
    /**
     * Create a vertex with a fixed big hash.
     * 
     * @param hexHashString String
     */
    public AbstractVertex(final String hexHashString){
    	if(!HashHelper.defaultInstance.isValidHashHexString(hexHashString)){
    		setId(hexHashString);
    		this.bigHashCode = HashHelper.defaultInstance.hashToHexString(hexHashString);
    	}else{
    		this.bigHashCode = hexHashString;
    	}
    }
    
    public final AbstractVertex copyAsVertex(){
    	final AbstractVertex copy;
    	if(isReferenceVertex()){
    		copy = new Vertex(this.bigHashCode);
    	}else{
    		copy = new Vertex();
    	}
		copy.annotations.putAll(this.annotations);
		return copy;
	}
    
    /**
     * Returns true if the vertex has a fixed big hash otherwise false
     * 
     * @return true/false
     */
    public final boolean isReferenceVertex(){
    	return bigHashCode != null;
    }
    
    /**
     * Returns the copy of the map containing the annotations for this vertex.
     *
     * Updated because if copy needs to be made then it should be made using the explicit function below
     *
     * @return The map containing the annotations.
     */
	public final Map<String, String> getCopyOfAnnotations(){
		return new TreeMap<String, String>(annotations);
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
    public final String removeAnnotation(String key) {
        return annotations.remove(key);
    }

    /**
     * Gets an annotation.
     *
     * @param key The annotation key.
     * @return The value of the annotation corresponding to the key.
     */
    public String getAnnotation(String key)
    {
        return annotations.get(key);
    }

    /**
     * Sets the type of this vertex
     * 
     * @param value Must be a non-null string otherwise converted to empty string
     */
    protected final void setType(final String value){
    	addAnnotation(typeKey, value);
    }

    /**
     * Gets the type of this vertex.
     *
     * @return A string indicating the type of this vertex.
     */
    public final String type(){
        return annotations.get(typeKey);
    }

    /**
     * Gets the id of this vertex.
     *
     * @return A string indicating the id of this vertex (if any).
     */
    public final String id(){
        return annotations.get(idKey);
    }
    
    /**
     * Sets the id of this vertex
     * 
     * @param value Must be a non-null string otherwise converted to empty string
     */
    public final void setId(final String value){
    	addAnnotation(idKey, value);
    }
    
    public final String getIdentifierForExport(){
    	if(id() == null){
    		return bigHashCode();
    	}else{
    		return id();
    	}
    }

    /**
     * Computes hash of annotations in the vertex according to the default set in spade.core.HashHelper.
     * If the hash was fixed then that is used.
     */
	public final String bigHashCode(){
		if(bigHashCode == null){
			final String data = annotations.toString();
			return HashHelper.defaultInstance.hashToHexString(data);
		}else{
			return bigHashCode;
		}
	}

	public final byte[] bigHashCodeBytes(){
		return HashHelper.defaultInstance.convertHashHexStringToHashByteArray(bigHashCode());
    }

    @Override
	public final int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + bigHashCode().hashCode();
		return result;
	}

	@Override
	public final boolean equals(Object obj){
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		AbstractVertex other = (AbstractVertex) obj;
		return bigHashCode().equals(other.bigHashCode());
	}

	@Override
	public final String toString(){
		return "AbstractVertex{" + hashKey + "=" + bigHashCode() + "," + annotationsKey + "=" + annotations + '}';
	}
	
	public static final boolean isVertexType(String type){
		if(HelperFunctions.isNullOrEmpty(type)){
			return false;
		}
		type = type.trim();
		if(	// generic vertex
			type.equalsIgnoreCase(spade.core.Vertex.typeValue)
			// prov
			|| type.equalsIgnoreCase(spade.vertex.prov.Activity.typeValue)
			|| type.equalsIgnoreCase(spade.vertex.prov.Agent.typeValue)
			|| type.equalsIgnoreCase(spade.vertex.prov.Entity.typeValue)
			// opm
			|| type.equalsIgnoreCase(spade.vertex.opm.Agent.typeValue)
			|| type.equalsIgnoreCase(spade.vertex.opm.Artifact.typeValue)
			|| type.equalsIgnoreCase(spade.vertex.opm.Process.typeValue)
			// cdm
			|| type.equalsIgnoreCase(spade.vertex.cdm.Event.typeValue)
			|| type.equalsIgnoreCase(spade.vertex.cdm.Object.typeValue)
			|| type.equalsIgnoreCase(spade.vertex.cdm.Principal.typeValue)
			|| type.equalsIgnoreCase(spade.vertex.cdm.Subject.typeValue)
			){
			return true;
		}else{
			return false;
		}
	}
}
