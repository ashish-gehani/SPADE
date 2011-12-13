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

 Based on the Bloom filter implementation by Magnus Skjegstad:
 http://code.google.com/p/java-bloomfilter/
 
 Author: Magnus Skjegstad <magnus@skjegstad.com>

--------------------------------------------------------------------------------
 */
package spade.core;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;

/*
 * @param <E> Object type that is to be inserted into the Bloom filter, e.g. String or Integer.
 * @author Magnus Skjegstad <magnus@skjegstad.com>
 */
public class MatrixFilter implements Serializable {

    private List<BloomFilter> filterSet;
    private int filterSetSize;
    private double filtersPerElement;
    private int expectedNumberOfElements; // expected (maximum) number of elements to be added
    private int numberOfAddedElements; // number of elements actually added to the Bloom filter
    private int k; // number of hash functions
    static final Charset charset = Charset.forName("UTF-8"); // encoding used for storing hash values as strings
    static final String hashName = "MD5"; // MD5 gives good enough accuracy in most circumstances. Change to SHA1 if it's needed
    static final MessageDigest digestFunction;

    static { // The digest method is reused between instances
        MessageDigest tmp;
        try {
            tmp = java.security.MessageDigest.getInstance(hashName);
        } catch (NoSuchAlgorithmException e) {
            tmp = null;
        }
        digestFunction = tmp;
    }

    /**
     * Constructs an empty Bloom filter. The total length of the Bloom filter will be
     * c*n.
     *
     * @param c is the number of bits used per element.
     * @param n is the expected number of elements the filter will contain.
     * @param k is the number of hash functions used.
     */
    public MatrixFilter(double c, int n, int k) {
        this.expectedNumberOfElements = n;
        this.k = k;
        this.filtersPerElement = c;
        this.filterSetSize = (int) Math.ceil(c * n);
        numberOfAddedElements = 0;
        this.filterSet = new LinkedList<BloomFilter>();
        for (int i = 0; i < this.filterSetSize; i++) {
            this.filterSet.add(new BloomFilter(c, n, k));
        }
    }

    /**
     * Constructs an empty Bloom filter. The optimal number of hash functions (k) is estimated from the total size of the Bloom
     * and the number of expected elements.
     *
     * @param bitSetSize defines how many bits should be used in total for the filter.
     * @param expectedNumberOElements defines the maximum number of elements the filter is expected to contain.
     */
    public MatrixFilter(int bitSetSize, int expectedNumberOElements) {
        this(bitSetSize / (double) expectedNumberOElements,
                expectedNumberOElements,
                (int) Math.round((bitSetSize / (double) expectedNumberOElements) * Math.log(2.0)));
    }

    /**
     * Constructs an empty Bloom filter with a given false positive probability. The number of bits per
     * element and the number of hash functions is estimated
     * to match the false positive probability.
     *
     * @param falsePositiveProbability is the desired false positive probability.
     * @param expectedNumberOfElements is the expected number of elements in the Bloom filter.
     */
    public MatrixFilter(double falsePositiveProbability, int expectedNumberOfElements) {
        this(Math.ceil(-(Math.log(falsePositiveProbability) / Math.log(2))) / Math.log(2), // c = k / ln(2)
                expectedNumberOfElements,
                (int) Math.ceil(-(Math.log(falsePositiveProbability) / Math.log(2)))); // k = ceil(-log_2(false prob.))
    }

    public BloomFilter getAllBloomFilters() {
        BloomFilter result = new BloomFilter(filtersPerElement, expectedNumberOfElements, k);
        result.getBitSet().set(0, result.getBitSet().size() - 1, false);
        for (int i = 0; i < filterSet.size(); i++) {
            BloomFilter currentFilter = filterSet.get(i);
            result.getBitSet().or(currentFilter.getBitSet());
        }
        return result;
    }

    /**
     * Generates a digest based on the contents of a String.
     *
     * @param val specifies the input data.
     * @param charset specifies the encoding of the input data.
     * @return digest as long.
     */
    public static long createHash(String val, Charset charset) {
        return createHash(val.getBytes(charset));
    }

    /**
     * Generates a digest based on the contents of a String.
     *
     * @param val specifies the input data. The encoding is expected to be UTF-8.
     * @return digest as long.
     */
    public static long createHash(String val) {
        return createHash(val, charset);
    }

    /**
     * Generates a digest based on the contents of an array of bytes.
     *
     * @param data specifies input data.
     * @return digest as long.
     */
    public static long createHash(byte[] data) {
        long h = 0;
        byte[] res;

        synchronized (digestFunction) {
            res = digestFunction.digest(data);
        }

        for (int i = 0; i < 4; i++) {
            h <<= 8;
            h |= ((int) res[i]) & 0xFF;
        }
        return h;
    }

    /**
     * Compares the contents of two instances to see if they are equal.
     *
     * @param obj is the object to compare to.
     * @return True if the contents of the objects are equal.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MatrixFilter other = (MatrixFilter) obj;
        if (this.expectedNumberOfElements != other.expectedNumberOfElements) {
            return false;
        }
        if (this.k != other.k) {
            return false;
        }
        if (this.filterSetSize != other.filterSetSize) {
            return false;
        }
        if (this.filterSet != other.filterSet && (this.filterSet == null || !this.filterSet.equals(other.filterSet))) {
            return false;
        }
        return true;
    }

    /**
     * Calculates a hash code for this class.
     * @return hash code representing the contents of an instance of this class.
     */
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 61 * hash + (this.filterSet != null ? this.filterSet.hashCode() : 0);
        hash = 61 * hash + this.expectedNumberOfElements;
        hash = 61 * hash + this.filterSetSize;
        hash = 61 * hash + this.k;
        return hash;
    }

    /**
     * Calculates the expected probability of false positives based on
     * the number of expected filter elements and the size of the Bloom filter.
     * <br /><br />
     * The value returned by this method is the <i>expected</i> rate of false
     * positives, assuming the number of inserted elements equals the number of
     * expected elements. If the number of elements in the Bloom filter is less
     * than the expected value, the true probability of false positives will be lower.
     *
     * @return expected probability of false positives.
     */
    public double expectedFalsePositiveProbability() {
        return getFalsePositiveProbability(expectedNumberOfElements);
    }

    /**
     * Calculate the probability of a false positive given the specified
     * number of inserted elements.
     *
     * @param numberOfElements number of inserted elements.
     * @return probability of a false positive.
     */
    public double getFalsePositiveProbability(double numberOfElements) {
        // (1 - e^(-k * n / m)) ^ k
        return Math.pow((1 - Math.exp(-k * (double) numberOfElements
                / (double) filterSetSize)), k);

    }

    /**
     * Get the current probability of a false positive. The probability is calculated from
     * the size of the Bloom filter and the current number of elements added to it.
     *
     * @return probability of false positives.
     */
    public double getFalsePositiveProbability() {
        return getFalsePositiveProbability(numberOfAddedElements);
    }

    /**
     * Returns the value chosen for K.<br />
     * <br />
     * K is the optimal number of hash functions based on the size
     * of the Bloom filter and the expected number of inserted elements.
     *
     * @return optimal k.
     */
    public int getK() {
        return k;
    }

    /**
     * Sets all bits to false in the Bloom filter.
     */
    public void clear() {
        filterSet.clear();
        numberOfAddedElements = 0;
    }

    /**
     * Adds an object to the Bloom filter. The output from the object's
     * toString() method is used as input to the hash functions.
     *
     * @param destinationVertex is an element to register in the Bloom filter.
     */
    public void add(AbstractVertex destinationVertex, AbstractVertex sourceVertex) {
        long hash;
        String valString = sketchString(destinationVertex);
        for (int x = 0; x < k; x++) {
            hash = createHash(valString + Integer.toString(x));
            hash = hash % (long) filterSetSize;
            filterSet.get(Math.abs((int) hash)).add(sourceVertex);
        }
        numberOfAddedElements++;
    }

    public void updateAncestors(AbstractVertex vertex, BloomFilter ancestorsToAdd) {
        long hash;
        String valString = sketchString(vertex);
        for (int x = 0; x < k; x++) {
            hash = createHash(valString + Integer.toString(x));
            hash = hash % (long) filterSetSize;
            filterSet.get(Math.abs((int) hash)).getBitSet().or(ancestorsToAdd.getBitSet());
        }
        numberOfAddedElements++;
    }

    public BloomFilter get(AbstractVertex vertex) {
        BloomFilter result = new BloomFilter(filtersPerElement, expectedNumberOfElements, k);
        result.getBitSet().set(0, result.getBitSet().size() - 1, true);
        long hash;
        String valString = sketchString(vertex);
        for (int x = 0; x < k; x++) {
            hash = createHash(valString + Integer.toString(x));
            hash = hash % (long) filterSetSize;
            BloomFilter tempBloomFilter = filterSet.get(Math.abs((int) hash));
            result.getBitSet().and(tempBloomFilter.getBitSet());
        }
        return result;
    }

    /**
     * Returns true if the element could have been inserted into the Bloom filter.
     * Use getFalsePositiveProbability() to calculate the probability of this
     * being correct.
     *
     * @param element element to check.
     * @return true if the element could have been inserted into the Bloom filter.
     */
    public boolean contains(AbstractVertex vertex) {
        long hash;
        String valString = sketchString(vertex);
        for (int x = 0; x < k; x++) {
            hash = createHash(valString + Integer.toString(x));
            hash = hash % (long) filterSetSize;
            if (!filterSet.get(Math.abs((int) hash)).contains(vertex)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the number of bits in the Bloom filter. Use count() to retrieve
     * the number of inserted elements.
     *
     * @return the size of the bitset used by the Bloom filter.
     */
    public int size() {
        return this.filterSetSize;
    }

    /**
     * Returns the number of elements added to the Bloom filter after it
     * was constructed or after clear() was called.
     *
     * @return number of elements added to the Bloom filter.
     */
    public int count() {
        return this.numberOfAddedElements;
    }

    /**
     * Returns the expected number of elements to be inserted into the filter.
     * This value is the same value as the one passed to the constructor.
     *
     * @return expected number of elements.
     */
    public int getExpectedNumberOfElements() {
        return expectedNumberOfElements;
    }

    /**
     * Get expected number of bits per element when the Bloom filter is full. This value is set by the constructor
     * when the Bloom filter is created. See also getBitsPerElement().
     *
     * @return expected number of bits per element.
     */
    public double getExpectedBitsPerElement() {
        return this.filtersPerElement;
    }

    /**
     * Get actual number of bits per element based on the number of elements that have currently been inserted and the length
     * of the Bloom filter. See also getExpectedBitsPerElement().
     *
     * @return number of bits per element.
     */
    public double getBitsPerElement() {
        return this.filterSetSize / (double) numberOfAddedElements;
    }

    public String sketchString(AbstractVertex vertex) {
        String result = "";
        if ((vertex.getAnnotation("source host")).compareTo(vertex.getAnnotation("destination host")) < 0) {
            result += vertex.getAnnotation("source host") + vertex.getAnnotation("source port");
            result += vertex.getAnnotation("destination host") + vertex.getAnnotation("destination port");
        } else {
            result += vertex.getAnnotation("destination host") + vertex.getAnnotation("destination port");
            result += vertex.getAnnotation("source host") + vertex.getAnnotation("source port");
        }
        return result;
    }
}
