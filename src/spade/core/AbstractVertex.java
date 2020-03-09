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

import org.apache.commons.codec.digest.DigestUtils;

import spade.reporter.audit.OPMConstants;
import spade.utility.HelperFunctions;

/**
 * This is the class from which other vertex classes (e.g., OPM vertices) are
 * derived.
 *
 * @author Dawood Tariq
 */
public abstract class AbstractVertex implements Serializable
{

    /**
	 * 
	 */
	private static final long serialVersionUID = 4766085487390172973L;
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
     * An integer indicating the depth of the vertex in the graph
     */
    private int depth;

    /**
     * Create a vertex without a fixed big hash.
     */
    public AbstractVertex(){
    	this.bigHashCode = null;
    }
    
    /**
     * Create a vertex with a fixed big hash.
     * 
     * @param md5HexBigHashCode String
     */
    public AbstractVertex(String md5HexBigHashCode){
    	this.bigHashCode = md5HexBigHashCode;
    	// TODO required for future changes?
    	/*
    	if(this.bigHashCode != null){
    		if(this.bigHashCode.length() != 32){
    			throw new RuntimeException("The big hash code length must be '32' to work with all storages");
    		}
    	}
    	*/
    }
    
    /**
     * Returns true if the vertex has a fixed big hash otherwise false
     * 
     * @return true/false
     */
    public final boolean isReferenceVertex(){
    	return bigHashCode != null;
    }
    
    public int getDepth() {
		return depth;
	}

	public void setDepth(int depth) {
		this.depth = depth;
	}

	/**
     * Checks if vertex is empty
     *
     * @return Returns true if vertex contains no annotation
     */
    public final boolean isEmpty()
    {
        return annotations.size() == 0;
    }

    /**
     * Returns the map containing the annotations for this vertex.
     *
     * @return The map containing the annotations.
     */
//    public final Map<String, String> getAnnotations() {
//        return annotations;
//    }
    
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
    public final void addAnnotation(String key, String value)
    {
        if(!HelperFunctions.isNullOrEmpty(key))
        {
            if(value == null)
            {
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
    public final void addAnnotations(Map<String, String> newAnnotations)
    {
        for (Map.Entry<String, String> currentEntry : newAnnotations.entrySet())
        {
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
        return annotations.get(OPMConstants.TYPE);
    }

    /**
     * Computes MD5 hash of annotations in the vertex.
     *
     @return A 128-bit hash digest.
     */
	public final String bigHashCode(){
		if(bigHashCode == null){
			return DigestUtils.md5Hex(annotations.toString()); // calculated at runtime
		}else{
			return bigHashCode;
		}
	}

    /**
     * Computes MD5 hash of annotations in the vertex
     * @return 16 element byte array of the digest.
     */
	public final byte[] bigHashCodeBytes(){
    	if(bigHashCode == null){
    		return DigestUtils.md5(annotations.toString()); // calculated at runtime
    	}else{
    		return bigHashCode.getBytes();
    	}
    }

	/**
     * Computes a function of the annotations in the vertex.
     *
     * This takes less time to compute than bigHashCode() but is less collision-resistant.
     *
     * @return An integer-valued hash code.
     */
    @Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + bigHashCode().hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj){
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		AbstractVertex other = (AbstractVertex) obj;
		return bigHashCode().equals(other.bigHashCode());
	}
	
    @Override
    public final String toString()
    {
        return "AbstractVertex{" +
        		"hash=" + bigHashCode() + "," +
                "annotations=" + annotations +
                '}';
    }
    
    /*
     * @Author Raza
     */
    public final String prettyPrint()
	{
		return "\t\tVertex:{\n" +
				"\t\t\thash:" + bigHashCode() + ",\n" +
				"\t\t\tannotations:" + annotations + "'\n" +
				"\t\t}";
	}

	public final AbstractVertex copyAsVertex(){
		AbstractVertex copy = new Vertex(this.bigHashCode);
		copy.annotations.putAll(this.annotations);
		copy.depth = this.depth;
		return copy;
	}
}
