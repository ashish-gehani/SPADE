/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International

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
package spade.filter;

import java.time.LocalTime;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;

import java.util.ArrayList;
import java.util.PriorityQueue;
// java.io.*;

import java.io.FileWriter;
import java.io.IOException;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Settings;
import spade.core.Vertex;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;

public class WindowEdgeSort extends AbstractFilter{

	private final Logger logger = Logger.getLogger(this.getClass().getName());


	// config keys
	private static final String
		keySortAnnot = "sortAnnot";

        // array buffer to contain edges until bufferTime elapses
        // private ArrayList<AbstractEdge> bufferedEdges = new ArrayList(); 

	// priority queue buffer to contain edges until bufferTime elapses
	private PriorityQueue<AbstractEdge> bufferedEdges;
	
        // annotation to sort the input stream on
        private String sortAnnot;

        // edge counter
        private long edgeReference;

	// output
	FileWriter fWriter;
   
    

	// arguments -> anno_key1=anno_value1 anno_key2=anno_value2 ...
	public boolean initialize(String arguments){
		Map<String, String> configMap;
		final String configFilePath = Settings.getDefaultConfigFilePath(this.getClass());
		try{
			configMap = HelperFunctions.parseKeyValuePairsFrom(arguments, new String[]{configFilePath});

			configMap = FileUtility.readConfigFileAsKeyValueMap(Settings.getDefaultConfigFilePath(this.getClass()),
					"=");
			if(configMap == null){
				throw new Exception("NULL config map read");
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to read config file", e);
			return false;
		}

		try{
			// parsing sortAnnot
           		sortAnnot = configMap.get(keySortAnnot);	
		       
			// initializing edgeReference
           		 edgeReference = 1;

			// initializing priority queue
			bufferedEdges = new PriorityQueue<AbstractEdge>(new annotationComparator(sortAnnot));


		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to load config keys", e);
			return false;
		}
		return true;

	}
	
	// flushing edges when the stream ends
	// NOTE: remove the filter from control client to flush remaining edges
	public boolean shutdown(){
		
		// flushing buffer
        while(!bufferedEdges.isEmpty())
        {
            putInNextFilter(bufferedEdges.poll());
        }


		return true;
	}


	// flushing edges until an edge comes which is out of order
    public void flushUntilOutofOrder(){
        
        // flushing buffer
        while(!bufferedEdges.isEmpty())
        {
            AbstractEdge nextEdge = bufferedEdges.peek();

            long currRelationID = Long.parseLong(nextEdge.getAnnotation("relation_id"));
	    
            /*try{
	    fWriter = new FileWriter("/home/vagrant/temporary/flush_output.txt", true);
			
	    String writeline = String.format("currentRelationID: %d ----- edgeReference: %d\n*****\n", currRelationID, edgeReference);

            fWriter.write(writeline);

	    fWriter.close();
	    
	    }catch(IOException e){
	    System.out.print(e.getMessage());
	    }*/
            if (currRelationID > edgeReference){
                break;
            }
	    
	    if (currRelationID < edgeReference){
	    	AbstractEdge dropThisEdge = bufferedEdges.poll();
	    } else{
		putInNextFilter(bufferedEdges.poll());
            	edgeReference++;    
	    
	    }
        }
    }

	@Override
	public void putVertex(AbstractVertex incomingVertex){
		if(incomingVertex != null){
			putInNextFilter(incomingVertex);
		}else{
			logger.log(Level.WARNING, "Null vertex");
		}
	}

	@Override
	public void putEdge(AbstractEdge incomingEdge){
		if(incomingEdge != null && incomingEdge.getChildVertex() != null && incomingEdge.getParentVertex() != null){

            // add edge to priorityQueue
            bufferedEdges.add(incomingEdge);
			
			// extracting relation id
            long currentRelationID = Long.parseLong(incomingEdge.getAnnotation("relation_id"));
	    

	    /*try{
	    fWriter = new FileWriter("/home/vagrant/temporary/putEdge_output.txt", true);

	    String writeline = String.format("currentRelationID: %d ----- edgeReference: %d\n*****\n", currentRelationID, edgeReference);

            fWriter.write(writeline);
 
	    fWriter.close();
	    }catch(IOException e){
	    System.out.print(e.getMessage());
	    }*/


			// if the incoming edge is in order, then
			// flush all the in-order edges in the priority queue
            if (currentRelationID == edgeReference){
               
	    	/*try{
		
		fWriter = new FileWriter("/home/vagrant/temporary/putEdge_output.txt", true);
			
		String writeline = "flushUntilOutofOrder Called!!!\n*****\n";

		fWriter.write(writeline);

		fWriter.close();

		} catch(IOException e){
		System.out.print(e.getMessage());
		
		}*/
		flushUntilOutofOrder();
	    } 

        }else{
			logger.log(Level.WARNING, "Invalid edge: {0}, source: {1}, destination: {2}",
					new Object[]{incomingEdge, incomingEdge == null ? null : incomingEdge.getChildVertex(),
							incomingEdge == null ? null : incomingEdge.getParentVertex()});
		}
	}
}

// creates the comparator for comparing specific annotation of AbstractEdge
class annotationComparator implements Comparator<AbstractEdge> {
    
    // annotation to be used for comparison 
    private String annotKey;

    // parameterized constructor
    public annotationComparator(String annot)
    {
        annotKey = annot;
    }

    // overriding compare method
    @Override
    public int compare(AbstractEdge e1, AbstractEdge e2)
    {

		// null checks
		if(e1 == null && e2 == null)
		{
			return 0;
		}
		else if(e1 == null)
		{
			return -1;
		}
		else if(e2 == null)
		{
			return 1;
		}

        String annot1 = e1.getAnnotation(annotKey);
        String annot2 = e2.getAnnotation(annotKey);
	
        // return annot1.compareTo(annot2);
	return Integer.parseInt(annot1) - Integer.parseInt(annot2);
    }
}

