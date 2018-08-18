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
package spade.storage;

import org.apache.commons.codec.digest.DigestUtils;

import com.sleepycat.je.utilint.Pair;

import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Graph;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;

/**
 * A storage implementation that simply outputs plain text to a file.
 * @author Armando Caro
 */
public class CompressedTextFile extends AbstractStorage {

    private static FileWriter scaffoldWriter;
    private static FileWriter annotationsWriter;
    private String filePath;
    public static Integer W;
	public static Integer L;
	static Deflater compresser;
	static Integer nextVertexID;
	static Vector<String> alreadyRenamed;
	static Map<String, Integer> hashToID;
	static Integer edgesInMemory;
	static final Integer maxEdgesInMemory = 10;
	Map<Integer, Pair<SortedSet<Integer>, SortedSet<Integer>>> scaffoldInMemory;
	private static final Logger logger = Logger.getLogger(CompressedStorage.class.getName());
	long clock;
	PrintWriter benchmarks;
	File annotationsFile;
	File scaffoldFile;
	Scanner annotationsScanner;
	Scanner scaffoldScanner;
	
    @Override
    public boolean initialize(String arguments) {
		clock = System.currentTimeMillis();
    	scaffoldInMemory = new HashMap<Integer, Pair<SortedSet<Integer>, SortedSet<Integer>>>();
		edgesInMemory = 0;
		hashToID = new HashMap<String, Integer>();
		alreadyRenamed = new Vector<String>();
		compresser = new Deflater(Deflater.BEST_COMPRESSION);
		W=10;
		L=5;
		nextVertexID = 0;
        try {
            if (arguments == null) {
                return false;
            }
            annotationsFile = new File(filePath + "/annotations.txt");
            scaffoldFile = new File(filePath + "/scaffold.txt");
            annotationsScanner = new Scanner(annotationsFile);
            scaffoldScanner = new Scanner(scaffoldFile);
			benchmarks = new PrintWriter("/Users/melanie/Documents/benchmarks/compression_time_TextFile.txt", "UTF-8");
            filePath = arguments;
            scaffoldWriter = new FileWriter(filePath + "/scaffold.txt", false);
            scaffoldWriter.write("[BEGIN]\n");
            annotationsWriter = new FileWriter(filePath + "/annotations.txt", false);
            annotationsWriter.write("[BEGIN]\n");
            
            return true;
        } catch (Exception ex) {
        	logger.log(Level.SEVERE, "Compressed Storage Initialized not successful!", ex);
        	return false;
        }
    }

    @Override
    public boolean putVertex(AbstractVertex incomingVertex) {
        try {
            String vertexHash = DigestUtils.sha256Hex(incomingVertex.toString());
            Integer vertexId = nextVertexID;
            nextVertexID ++;
            hashToID.put(vertexHash, vertexId);
            StringBuilder annotationString = new StringBuilder();
            //annotationString.append("VERTEX (" + vertexId + "): {");
            for (Map.Entry<String, String> currentEntry : incomingVertex.getAnnotations().entrySet()) {
                String key = currentEntry.getKey();
                String value = currentEntry.getValue();
                if (key == null || value == null) {
                    continue;
                }
                annotationString.append(key);
                annotationString.append(":");
                annotationString.append(value);
                annotationString.append(",");
            }
            //annotationString.append("}\n");
            String vertexString = annotationString.toString();
            //outputFile.write(vertexString);
            byte [] input = vertexString.getBytes("UTF-8");
        	byte [] output = new byte[input.length + 100];
        	compresser.setInput(input);
        	compresser.finish();
        	int compressedDataLength = compresser.deflate(output);	
        	put(annotationsWriter, vertexId, output);	
        	compresser.reset();
            return true;
        } catch (Exception exception) {
            Logger.getLogger(TextFile.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }
    }

    private void put(FileWriter writer, String key, byte[] output) {
		try {
			writer.write(key + " {" + new String(output) + "}" + '\n');
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
    
    private void put(FileWriter writer, Integer vertexId, byte[] output) {
 		put(writer, vertexId.toString(), output);
 	}

 	// generated by raza to fix things
 	private static void put(FileWriter writer, Integer key, String output) {

		try {
			writer.write(key + " {" + new String(output) + "}" + '\n');
		} catch (IOException e) {

			e.printStackTrace();
		}
	}

	@Override
    public Object executeQuery(String query)
    {
        return null;
    }

    @Override
    public boolean putEdge(AbstractEdge incomingEdge) {
        try {
            String srcHash = DigestUtils.sha256Hex(incomingEdge.getChildVertex().toString());
            String dstHash = DigestUtils.sha256Hex(incomingEdge.getParentVertex().toString());
            Integer srcID = hashToID.get(srcHash);
            Integer dstID = hashToID.get(dstHash);
            StringBuilder annotationString = new StringBuilder();
            //annotationString.append("EDGE (" + srcID + " -> " + dstID + "): {");
            for (Map.Entry<String, String> currentEntry : incomingEdge.getAnnotations().entrySet()) {
                String key = currentEntry.getKey();
                String value = currentEntry.getValue();
                if (key == null || value == null) {
                    continue;
                }
                annotationString.append(key);
                annotationString.append(":");
                annotationString.append(value);
                annotationString.append(",");
            }
            //annotationString.append("}\n");
            //String edgeString = annotationString.toString();
            //annotationsWriter.write(edgeString);
            //annotationString.append("}\n");
            String edgeString = annotationString.toString();
            //outputFile.write(edgeString);
            byte [] input = edgeString.getBytes("UTF-8");
        	byte [] output = new byte[input.length + 100];
        	compresser.setInput(input);
        	compresser.finish();
        	int compressedDataLength = compresser.deflate(output);	
        	String key = srcID + "->" + dstID;
        	put(annotationsWriter, key, output);	
        	compresser.reset();
        	// scaffold storage
        	//update scaffoldInMemory
        	Pair<SortedSet<Integer>, SortedSet<Integer>> srcLists = scaffoldInMemory.get(srcID);
        	if (srcLists == null) {
        		srcLists = new Pair<SortedSet<Integer>, SortedSet<Integer>>(new TreeSet<Integer>(), new TreeSet<Integer>());
        	}
        	srcLists.second().add(dstID);
        	scaffoldInMemory.put(srcID, srcLists);
        	Pair<SortedSet<Integer>, SortedSet<Integer>> dstLists = scaffoldInMemory.get(dstID);
        	if (dstLists == null) {
        		dstLists = new Pair<SortedSet<Integer>, SortedSet<Integer>>(new TreeSet<Integer>(), new TreeSet<Integer>());
        	}
        	dstLists.first().add(dstID);
        	scaffoldInMemory.put(dstID, dstLists);
        	edgesInMemory ++;
        	if(edgesInMemory == maxEdgesInMemory) {
        		updateAncestorsSuccessors(scaffoldInMemory);
        		scaffoldInMemory.clear();
        		edgesInMemory = 0;
        		long aux = System.currentTimeMillis();
        		benchmarks.println(aux-clock);
        		clock = aux;
        	}
        	
            return true;
        } catch (Exception exception) {
            Logger.getLogger(TextFile.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }
    }


	@Override
    public boolean shutdown() {
        try {
            scaffoldWriter.write("[END]\n");
            scaffoldWriter.close();
            annotationsWriter.write("[END]\n");
            annotationsWriter.close();
            compresser.end();
			benchmarks.close();
            return true;
        } catch (Exception ex) {
			logger.log(Level.SEVERE, "Compressed TextFile Shutdown not successful!", ex);
            return false;
        }
    }

    /**
     * This function queries the underlying storage and retrieves the edge
     * matching the given criteria.
     *
     * @param childVertexHash  hash of the source vertex.
     * @param parentVertexHash hash of the destination vertex.
     * @return returns edge object matching the given vertices OR NULL.
     */
    @Override
    public AbstractEdge getEdge(String childVertexHash, String parentVertexHash)
    {
        return null;
    }

    /**
     * This function queries the underlying storage and retrieves the vertex
     * matching the given criteria.
     *
     * @param vertexHash hash of the vertex to find.
     * @return returns vertex object matching the given hash OR NULL.
     */
    @Override
    public AbstractVertex getVertex(String vertexHash)
    {
        return null;
    }

    /**
     * This function finds the children of a given vertex.
     * A child is defined as a vertex which is the source of a
     * direct edge between itself and the given vertex.
     *
     * @param parentHash hash of the given vertex
     * @return returns graph object containing children of the given vertex OR NULL.
     */
    @Override
    public Graph getChildren(String parentHash)
    {
        return null;
    }

    /**
     * This function finds the parents of a given vertex.
     * A parent is defined as a vertex which is the destination of a
     * direct edge between itself and the given vertex.
     *
     * @param childVertexHash hash of the given vertex
     * @return returns graph object containing parents of the given vertex OR NULL.
     */
    @Override
    public Graph getParents(String childVertexHash)
    {
        return null;
    }
    
    private static void updateAncestorsSuccessors(Map<Integer, Pair<SortedSet<Integer>, SortedSet<Integer>>> asl) {
    	//for each line to update 
    	Set<Map.Entry<Integer, Pair<SortedSet<Integer>, SortedSet<Integer>>>> entries = asl.entrySet();
    	SortedSet<Integer> ancestors;
    	SortedSet<Integer> successors;
    	boolean updateAncestors;
    	boolean updateSuccessors;
    	HashMap<Integer, Pair<SortedSet<Integer>, SortedSet<Integer>>> toUpdate = new HashMap<Integer, Pair<SortedSet<Integer>, SortedSet<Integer>>>();
    	//HashMap<Integer, Pair<SortedSet<Integer>, SortedSet<Integer>>> unchangedToUpdate = new HashMap<Integer, Pair<SortedSet<Integer>, SortedSet<Integer>>>();
    	for (HashMap.Entry<Integer, Pair<SortedSet<Integer>, SortedSet<Integer>>> nodeToUpdate : entries){
    		Integer id = nodeToUpdate.getKey();
    		Pair<Pair<Integer, SortedSet<Integer>>, Pair<Integer, SortedSet<Integer>>> lists = uncompressAncestorsSuccessorsWithLayer(id, true, true);
    		ancestors = lists.first().second();
    		ancestors.addAll(nodeToUpdate.getValue().first());
    		successors = lists.second().second();
    		successors.addAll(nodeToUpdate.getValue().second());
    		toUpdate.put(id, new Pair(ancestors, successors));


    		//update other nodes but do not update the reference
    		for (Integer nodeID = id + 1; nodeID < id + W + 1; nodeID++) {
    			String line = get(scaffoldWriter, nodeID);
    			if (line != null && line.contains("/")) {
    				// get reference and see if it is id.
    				String ancestorList = line.substring(line.indexOf(' ')+1, line.indexOf("/") - 1);
    				boolean isReferenceAncestor;
    				if (ancestorList.contains("_")) {
    					isReferenceAncestor = false;
    				} else {
    					Integer referenceAncestor = Integer.parseInt(ancestorList.substring(0, ancestorList.indexOf(' ')));
    					isReferenceAncestor = id.equals(nodeID + referenceAncestor);
    				}
    				String successorList = line.substring(line.indexOf("/") + 2);
    				successorList = successorList.substring(successorList.indexOf(' ') + 1);
    				boolean isReferenceSuccessor;
    				if (successorList.contains("_")) {
    					isReferenceSuccessor = false;
    				} else {
    					Integer referenceSuccessor = Integer.parseInt(successorList.substring(0, successorList.indexOf(' ')));
    					isReferenceSuccessor = id.equals(nodeID + referenceSuccessor);
    				}
    				if ( isReferenceAncestor|| isReferenceSuccessor ){
    					//update the encoding of the line
    					Pair<Pair<Integer, SortedSet<Integer>>, Pair<Integer, SortedSet<Integer>>> aux = uncompressAncestorsSuccessorsWithLayer(nodeID, true, true);
    					if (!toUpdate.containsKey(nodeID))
    						toUpdate.put(nodeID, new Pair(aux.first().second(), aux.second().second()));
    				}
    			}
    		}
    	}
    	Set<HashMap.Entry<Integer, Pair<SortedSet<Integer>, SortedSet<Integer>>>> entriesToUpdate = toUpdate.entrySet();
    	for(HashMap.Entry<Integer, Pair<SortedSet<Integer>, SortedSet<Integer>>> nodeToUpdate : entriesToUpdate) {
    		encodeAncestorsSuccessors(nodeToUpdate);
    	}
    }
    
    
	private static String get(FileWriter scaffoldWriter2, Integer nodeID) {
		return null;
	}

	public static Pair<Pair<Integer, SortedSet<Integer>>, Pair<Integer, SortedSet<Integer>>> uncompressAncestorsSuccessorsWithLayer(
			Integer id, boolean uncompressAncestors, boolean uncompressSuccessors) {
		//System.out.println("step a");
		SortedSet<Integer> ancestors = new TreeSet<Integer>();
		SortedSet<Integer> successors = new TreeSet<Integer>();
		Integer ancestorLayer = 1;
		Integer successorLayer = 1;
		String aux = get(scaffoldWriter, id);
		//System.out.println("step b");
		if(aux != null && aux.contains("/")) {
			// split the line in two parts : ancestor list and successor list.
			String ancestorList = aux.substring(0, aux.indexOf('/'));
			String successorList = aux.substring(aux.indexOf('/')+2);
			ancestorLayer = Integer.parseInt(ancestorList.substring(0, ancestorList.indexOf(' ')));
			successorLayer = Integer.parseInt(successorList.substring(0, successorList.indexOf(' ')));
			ancestorList = ancestorList.substring(ancestorList.indexOf(' ') + 1);
			successorList = successorList.substring(successorList.indexOf(' ') + 1);
			//System.out.println("step c");
			if (uncompressAncestors) { //uncompressAncestors
				if(ancestorList.contains("_")) { // means there is no reference
					//System.out.println("step d");
					String ancestorList2 = ancestorList.substring(ancestorList.indexOf("_") + 1);
					ancestors.addAll(uncompressRemainingNodes(id, ancestorList2));
				} else { // there is a reference that we have to uncompress
					// uncompress the remaining Nodes
					//System.out.println("step e");
					String remaining = ancestorList.substring(ancestorList.indexOf(" ")+1);
					remaining = remaining.substring(remaining.indexOf(" ")+1);
					ancestors.addAll(uncompressRemainingNodes(id, remaining));
					//uncompress the reference and its reference after that
					try {
						ancestors.addAll(uncompressReference(id, ancestorList, true));
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
					}
				}
			}
			//System.out.println("step f");
			if (uncompressSuccessors) { // uncompressSuccessors
				if(successorList.contains("_")) { // means there is no reference
					//System.out.println("step g " );
					String successorList2 = successorList.substring(successorList.indexOf("_")+ 1);
					successors.addAll(uncompressRemainingNodes(id, successorList2));
				} else { // there is a reference that we have to uncompress
					// uncompress the remaining Nodes
					//System.out.println("step h");
		/*			String remaining = successorList.substring(successorList.indexOf(" ")+1);
					remaining = remaining.substring(remaining.indexOf(" ")+1);
					System.out.println("remaining" +remaining);
					//System.out.println("step i ");
					successors.addAll(uncompressRemainingNodes(id, remaining));*/
					//uncompress the reference and its reference after that
					try {
						//System.out.println("step j ");
						successors.addAll(uncompressReference(id, successorList, false));
						//System.out.println("step k");
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
					}
				}
			}
		}
		//System.out.println("step k");
		Pair<Integer, SortedSet<Integer>> ancestorsAndLayer = new Pair<Integer, SortedSet<Integer>>(ancestorLayer, ancestors);
		Pair<Integer, SortedSet<Integer>> successorsAndLayer = new Pair<Integer, SortedSet<Integer>>(successorLayer, successors);
		Pair<Pair<Integer, SortedSet<Integer>>, Pair<Integer, SortedSet<Integer>>> ancestorsAndSuccessors = new Pair<Pair<Integer, SortedSet<Integer>>, Pair<Integer, SortedSet<Integer>>>(ancestorsAndLayer, successorsAndLayer);
		return ancestorsAndSuccessors;
	}
	
	
	public static SortedSet<Integer> uncompressRemainingNodes(Integer nodeID, String remainingNodes){
		//System.out.println("remainingNodes :" + remainingNodes);
		SortedSet<Integer> successors = new TreeSet<Integer>();
		Integer currentID = nodeID;
		String uncompressing;
		int length;
		StringTokenizer st = new StringTokenizer(remainingNodes);
		while (st.hasMoreTokens()) {
			uncompressing = st.nextToken();
			if(uncompressing.contains(":")) { //uncompress consecutive numbers
				if(uncompressing.charAt(0) != ':'){
					currentID += Integer.parseInt(uncompressing.substring(0, uncompressing.indexOf(':')));
					successors.add(currentID);
				}
				length = Integer.parseInt(uncompressing.substring(uncompressing.indexOf(':')+1));
				for (int i=0; i<length; i++) {
					currentID ++;
					successors.add(currentID);
				}
			} else { 
				currentID = currentID + Integer.parseInt(uncompressing);
				successors.add(currentID);
			}
		}
		//System.out.println("uncompressRemainingNodes : " + successors.toString());
		return successors;
	}

	
	private static SortedSet<Integer> uncompressReference(Integer id, String ancestorOrSuccessorList,
			boolean ancestorOrSuccessor) throws UnsupportedEncodingException {
		//System.out.println("step m");
		SortedSet<Integer> list = new TreeSet<Integer>();
		StringTokenizer st = new StringTokenizer(ancestorOrSuccessorList);
		//System.out.println("ancestorOrSuccessorList :" + ancestorOrSuccessorList + " /id :" + id);
		//st.nextToken();
		String token = st.nextToken();
		//System.out.println("step n");
		Integer referenceID = id + Integer.parseInt(token);
		//System.out.println("referenceID1: " + referenceID);
		LinkedList<String> previousLayers = new LinkedList<String>();
		previousLayers.addFirst(id + " " + ancestorOrSuccessorList);
		String toUncompress;
		//System.out.println("step o");
		boolean hasReference = true;
		//System.out.println(toUncompress);
		//System.out.println(ancestorOrSuccessorList);
		while (hasReference){
			//System.out.println("step p");
			String currentLine = get(scaffoldWriter, referenceID);
			if(currentLine.length() > 0) {

				if (ancestorOrSuccessor) { // we want to uncompress ancestors
					toUncompress = currentLine.substring(currentLine.indexOf(' ')+1, currentLine.indexOf("/") - 1);
				} else { // we want to uncompress successors
					toUncompress = currentLine.substring(currentLine.indexOf("/") + 2);
					toUncompress = toUncompress.substring(toUncompress.indexOf(' ') + 1);
				}
				//System.out.println("step q");
				//System.out.println("toUncompress:" + toUncompress);
				// System.out.println(toUncompress);
				toUncompress = referenceID + " " + toUncompress;
				previousLayers.addFirst(toUncompress);
				//System.out.println("step r");
				if (toUncompress.contains(" _ ")) { // this is the last layer
					hasReference = false;
				} else { // we need to go one layer further to uncompress the successors
					String aux = toUncompress.substring(toUncompress.indexOf(" ")+1);
					//System.out.println("toUncompress:" + toUncompress);
					referenceID = referenceID + Integer.parseInt(aux.substring(0, aux.indexOf(" ")));
					// System.out.println("referenceID : " + referenceID);
					//System.out.println("step s");
				}
			} else {
				System.out.println("Data missing.");
				hasReference = false;
			}//System.out.println("step t");
		}

		// System.out.println("previousLayers: " + previousLayers.toString());
		String bitListLayer;
		String remainingNodesLayer;
		Integer layerID;
		for(String layer : previousLayers) { //find the successors of the first layer and then those of the second layer and so on...
			layerID = Integer.parseInt(layer.substring(0, layer.indexOf(" ")));
			//System.out.println("step u");
			if (layer.contains("_ ")) { //this is the case for the first layer only
				remainingNodesLayer = layer.substring(layer.indexOf("_ ")+2);
				//System.out.println("step v");
			} else {
				// uncompress the bitlist
				remainingNodesLayer = layer.substring(layer.indexOf(" ") + 1);
				//System.out.println("remaining Nodes Layer 1: " + remainingNodesLayer);
				//// System.out.println("step 1 :" + remainingNodesLayer + "/");
				remainingNodesLayer = remainingNodesLayer.substring(remainingNodesLayer.indexOf(" ") + 1);
				//remainingNodesLayer = remainingNodesLayer.substring(remainingNodesLayer.indexOf(" ") + 1);
				//// System.out.println("step 2 :" + remainingNodesLayer + "/");
				//System.out.println("step w");
				//System.out.println("remaining Nodes Layer " + remainingNodesLayer);
				if (remainingNodesLayer.contains(" ")) {
					bitListLayer = remainingNodesLayer.substring(0, remainingNodesLayer.indexOf(" "));
					remainingNodesLayer = remainingNodesLayer.substring(remainingNodesLayer.indexOf(" ") + 1);
				} else {
					
					bitListLayer = remainingNodesLayer.substring(0);
					remainingNodesLayer = "";
				}
				//System.out.println("bitListLayer :" + bitListLayer + "/");
				int count = 0;
				SortedSet<Integer> list2 = new TreeSet<Integer>();
				list2.addAll(list);
			//	System.out.println("step x");
				//System.out.println(bitListLayer);
				for (Integer successor : list2) {
					//System.out.println(successor + " " + count);
					if(bitListLayer.charAt(count) == '0') {
						list.remove(successor);
						//System.out.println("step y");
					}
					count++;
				}
			}
			// uncompress remaining nodes
			list.addAll(uncompressRemainingNodes(layerID, remainingNodesLayer)); 
			//System.out.println("step z");
		} 
		//System.out.println("uncompressReference : " + list.toString() + "id : " + id);
		return list;
	}

	
	public static boolean encodeAncestorsSuccessors(HashMap.Entry<Integer, Pair<SortedSet<Integer>, SortedSet<Integer>>> nodeToCompress) {
		//find reference node
		Integer id = nodeToCompress.getKey();
		//System.out.println(id);
		SortedSet<Integer> ancestors = nodeToCompress.getValue().first();
		SortedSet<Integer> successors = nodeToCompress.getValue().second();
		//NodeLayerAncestorSuccessor currentNode = new NodeLayerAncestorSuccessor(id, ancestors, successors);
		Pair<Integer, Integer> maxNodesInCommonAncestor = new Pair<Integer, Integer>(0,0);
		Pair<Integer, Integer> maxNodesInCommonSuccessor = new Pair<Integer, Integer>(0,0); //first integer is the max of nodes in common, the second one is the number of 0 in the corresponding bit list
		//NodeLayerAncestorSuccessor referenceAncestor = currentNode;
		String bitlistAncestor = "";
		int layerAncestor = 1;
		Integer referenceAncestor = -1;
		SortedSet<Integer> referenceAncestorList = new TreeSet<Integer>();
		//NodeLayerAncestorSuccessor referenceSuccessor = currentNode;
		String bitlistSuccessor = "";
		int layerSuccessor = 1;
		Integer referenceSuccessor = -1;
		SortedSet<Integer> referenceSuccessorList = new TreeSet<Integer>();
		//Iterator<NodeLayerAncestorSuccessor> iteratorPossibleReference = lastNodesSeen.iterator(); 
		//while (iteratorPossibleReference.hasNext()){
		//System.out.println("step 1");
		for (Integer possibleReferenceID = 1; possibleReferenceID<id; possibleReferenceID ++){
			//for each node in the W last nodes seen, compute the proximity, i.e. the number of successors of the current node that also are successors of the possibleReference node.
			Pair<Pair<Integer, SortedSet<Integer>>, Pair<Integer, SortedSet<Integer>>> asl = uncompressAncestorsSuccessorsWithLayer(possibleReferenceID, true, true); 
			if(asl.first().first() < L) {
				//System.out.println("step 1.1");
				Pair<Pair<Integer, Integer>, String> nodesInCommonAncestor = commonNodes(asl.first().second(), ancestors);
				int numberOfOneAncestor = nodesInCommonAncestor.first().first();
				int numberOfZeroAncestor = nodesInCommonAncestor.first().second();
				int maxNumberOfOneAncestor = maxNodesInCommonAncestor.first();
				int maxNumberOfZeroAncestor = maxNodesInCommonAncestor.second();
				//System.out.println("step 2");
				if (numberOfOneAncestor>maxNumberOfOneAncestor || (numberOfOneAncestor==maxNumberOfOneAncestor && numberOfZeroAncestor<maxNumberOfZeroAncestor)) {
					maxNodesInCommonAncestor = nodesInCommonAncestor.first();
					bitlistAncestor = nodesInCommonAncestor.second();
					referenceAncestor = possibleReferenceID;
					layerAncestor = asl.first().first() + 1;
					referenceAncestorList = asl.first().second();
					//System.out.println("step 3");
				}
			}
			//System.out.println("step 4");
			if (asl.second().first() < L) {
				//System.out.println("step 4.1");
				Pair<Pair<Integer, Integer>, String> nodesInCommonSuccessor =  commonNodes(asl.second().second(), successors);
				int numberOfOneSuccessor = nodesInCommonSuccessor.first().first();
				int numberOfZeroSuccessor = nodesInCommonSuccessor.first().second();
				int maxNumberOfOneSuccessor = maxNodesInCommonSuccessor.first();
				int maxNumberOfZeroSuccessor = maxNodesInCommonSuccessor.second();
				if (numberOfOneSuccessor>maxNumberOfOneSuccessor || (numberOfOneSuccessor==maxNumberOfOneSuccessor && numberOfZeroSuccessor<maxNumberOfZeroSuccessor)) {
					maxNodesInCommonSuccessor = nodesInCommonSuccessor.first();
					bitlistSuccessor = nodesInCommonSuccessor.second();
					referenceSuccessor = possibleReferenceID;
					layerSuccessor = asl.second().first() + 1;
					referenceSuccessorList = asl.second().second();
				}
			}
			//System.out.println("step 5");
		}
		//System.out.println("step 6");

		//encode ancestor list
		SortedSet<Integer> remainingNodesAncestor = new TreeSet<Integer>();
		remainingNodesAncestor.addAll(ancestors);
		//encode reference
		//String encoding = id.toString() + " ";
		String encoding = layerAncestor + " ";
		if (maxNodesInCommonAncestor.first() > 0) {
			encoding = encoding + (referenceAncestor - id) + " " + bitlistAncestor + "";
			//keep only remaining nodes
			remainingNodesAncestor.removeAll(referenceAncestorList);	
		} else {
			encoding = encoding + "_";
		}

		//encode consecutive nodes and delta encoding
		Integer previousNode = id;
		int countConsecutives = 0;
		for (Integer nodeID : remainingNodesAncestor) {
			Integer delta = nodeID - previousNode; 
			if (delta == 1) {
				countConsecutives++;
			} else {

				if (countConsecutives > 0) {
					encoding = encoding + ":" + countConsecutives;
					countConsecutives = 1;
				}
				encoding = encoding + " " + delta;
				countConsecutives = 0;
			}
			previousNode = nodeID;

		}		
		// encode successor list
		SortedSet<Integer> remainingNodesSuccessor = new TreeSet<Integer>();
		remainingNodesSuccessor.addAll(successors);
		//encode reference
		encoding = encoding + " / " + layerSuccessor + " ";
		if (maxNodesInCommonSuccessor.first() > 0) {
			encoding = encoding + (referenceSuccessor - id) + " " + bitlistSuccessor + "";
			//keep only remaining nodes
			remainingNodesSuccessor.removeAll(referenceSuccessorList);	
		} else {
			encoding = encoding + "_ ";
		}

		//encode consecutive nodes and delta encoding
		previousNode = id;
		countConsecutives = 0;
		for (Integer nodeID : remainingNodesSuccessor) {
			Integer delta = nodeID - previousNode; 
			if (delta == 1) {
				countConsecutives++;
			} else {

				if (countConsecutives > 0) {

					encoding = encoding + ":" + countConsecutives;
					countConsecutives = 1;
				}
				encoding = encoding + " " + delta;
				countConsecutives = 0;
			}
			previousNode = nodeID;

		}
		put(scaffoldWriter, id, encoding);
		//System.out.println(id + " " + encoding);
		return true;
	}

	public static Pair<Pair<Integer, Integer>, String> commonNodes(SortedSet<Integer> reference,
			SortedSet<Integer> node) {
		int nodesInCommon = 0;
		int numberOfZero = 0;
		String bitlist = "";
		for (Integer successor : reference) {
			if (node.contains(successor)) {
				nodesInCommon++;
				bitlist = bitlist + "1";
			} else {
				numberOfZero++;
				bitlist = bitlist + "0";
			}
		}
		Pair<Integer, Integer> count = new Pair<Integer, Integer>(nodesInCommon, numberOfZero);
		return new Pair<Pair<Integer, Integer>, String>(count, bitlist);
	}


}
