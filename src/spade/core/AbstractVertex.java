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

import com.mysql.jdbc.StringUtils;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;

import spade.reporter.audit.OPMConstants;

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
    protected Map<String, String> annotations = new TreeMap<>();

    /**
     * An integer indicating the depth of the vertex in the graph
     */
    private int depth;

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
    public final Map<String, String> getAnnotations() {
        return annotations;
    }

    /**
     * Adds an annotation.
     *
     * @param key The annotation key.
     * @param value The annotation value.
     */
    public final void addAnnotation(String key, String value)
    {
        if(!StringUtils.isNullOrEmpty(key))
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
            if(!StringUtils.isNullOrEmpty(key))
            {
                if(value == null)
                {
                    value = "";
                }
                addAnnotation(key, value);
            }
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
    public String bigHashCode()
    {
        return DigestUtils.md5Hex(this.toString());
    }


    /**
     * Computes MD5 hash of annotations in the vertex
     * @return 16 element byte array of the digest.
     */
    public byte[] bigHashCodeBytes()
    {
        return DigestUtils.md5(this.toString());
    }

    @Override
    public boolean equals(Object obj)
    {
        if(this == obj) return true;
        if(!(obj instanceof AbstractVertex)) return false;

        AbstractVertex vertex = (AbstractVertex) obj;

        return annotations.equals(vertex.annotations);
    }

    public boolean isCompleteNetworkVertex()
    {
        String subtype = this.getAnnotation(OPMConstants.ARTIFACT_SUBTYPE);
        String source = this.getAnnotation(OPMConstants.SOURCE);
        if(subtype != null && subtype.equalsIgnoreCase(OPMConstants.SUBTYPE_NETWORK_SOCKET)
                && source.equalsIgnoreCase(OPMConstants.SOURCE_AUDIT_NETFILTER))
        {
            return true;
        }

        return false;
    }

    public boolean isNetworkVertex()
    {
        String subtype = this.getAnnotation(OPMConstants.ARTIFACT_SUBTYPE);
        if(subtype != null && subtype.equalsIgnoreCase(OPMConstants.SUBTYPE_NETWORK_SOCKET))
        {
            return true;
        }

        return false;
    }

    /**
     * Computes a function of the annotations in the vertex.
     *
     * This takes less time to compute than bigHashCode() but is less collision-resistant.
     *
     * @return An integer-valued hash code.
     */
    @Override
    public int hashCode()
    {
        final int seed1 = 67;
        int hashCode = 3;
        hashCode = seed1 * hashCode + (this.annotations != null ? this.annotations.hashCode() : 0);
        return hashCode;
    }

    @Override
    public String toString()
    {
        return "AbstractVertex{" +
                "annotations=" + annotations +
                '}';
    }
}
