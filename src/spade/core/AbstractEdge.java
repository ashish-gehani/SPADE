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

import org.apache.commons.codec.digest.DigestUtils;

import spade.reporter.audit.OPMConstants;
import spade.utility.HelperFunctions;

/**
 * This is the class from which other edge classes (e.g., OPM edges) are
 * derived.
 *
 * @author Dawood Tariq
 */
public abstract class AbstractEdge implements Serializable
{

    /**
	 * 
	 */
	private static final long serialVersionUID = 5777793863959971982L;
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
     * @param md5HexBigHashCode String
     */
    public AbstractEdge(String md5HexBigHashCode){
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
    public final boolean isReferenceEdge(){
    	return bigHashCode != null;
    }
    
    /**
     * Checks if edge is empty
     *
     * @return Returns true if edge contains no annotation,
     * and both end points are empty
     */
    public final boolean isEmpty()
    {
        return annotations.size() == 0 && childVertex != null && parentVertex != null;
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
     * Gets the type of this edge.
     *
     * @return A string indicating the type of this edge.
     */
    public final String type() {
        return annotations.get(OPMConstants.TYPE);
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
     * Computes MD5 hash of annotations in the vertex.
     *
     @return A 128-bit hash digest.
     */
	public final String bigHashCode(){
		if(bigHashCode == null){
			return DigestUtils.md5Hex(
					(childVertex == null ? "(null)" : childVertex.bigHashCode()) + "," +
					annotations.toString() + "," +
					(parentVertex == null ? "(null)" : parentVertex.bigHashCode())
					); // calculated at runtime
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
    		return DigestUtils.md5(
					(childVertex == null ? "(null)" : childVertex.bigHashCode()) + "," +
					annotations.toString() + "," + 
					(parentVertex == null ? "(null)" : parentVertex.bigHashCode())
					); // calculated at runtime
    	}else{
    		return bigHashCode.getBytes();
    	}
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

    /**
     * Computes a function of the annotations in the edge and the vertices it is incident upon.
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
    public final String toString()
    {
        return "AbstractEdge{" +
                "annotations=" + annotations +
                ", childVertex=" + childVertex +
                ", parentVertex=" + parentVertex +
                '}';
    }
    
    /*
     * @Author Raza
     */
    public String prettyPrint()
	{
		return "\t\tEdge:{\n" +
				"\t\t\thash:" + bigHashCode() + ",\n" +
				"\t\t\tchildHash:" + (childVertex == null ? "<NULL>" : childVertex.bigHashCode()) + ",\n" +
				"\t\t\tparentHash:" + (parentVertex == null ? "<NULL>" : parentVertex.bigHashCode()) + ",\n" +
				"\t\t\tannotations:" + annotations + "\n" +
				"\t\t}";
	}
}
