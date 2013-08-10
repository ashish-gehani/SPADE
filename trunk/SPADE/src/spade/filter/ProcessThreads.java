package spade.filter;

import java.beans.DesignMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.edge.opm.Used;
import spade.edge.opm.WasGeneratedBy;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

/**
 * This filter groups together threads of same processes in one node It might
 * not scale well so ideally it should be used as post-processing i.e. re-run a
 * dump through it
 *
 * Thread and Process nodes are held occasionally till they are deemed to be
 * flushable Edges are always flushed at the end of processing during which they
 * are remapped to new nodes
 *
 * @author Sharjeel Ahmed Qureshi
 */
public class ProcessThreads extends AbstractFilter {

    static final Logger logger = Logger.getLogger(ProcessThreads.class.getName());

    private class ShelvedProcess {

        private AbstractVertex mainProcess = null;
        private Map<String, Set<String>> multiAttributes = new HashMap<String, Set<String>>();

        public ShelvedProcess(AbstractVertex vertex) {
            mainProcess = vertex;
            addAnotherVertexAnnotations(vertex);
        }

        public ShelvedProcess() {
            mainProcess = null;
        }

        public void addAnotherVertexAnnotations(AbstractVertex vertex) {
            assert vertex.type().equalsIgnoreCase("Process");
            Map<String, String> annotations = vertex.getAnnotations();
            for (String k : annotations.keySet()) {
                // logger.log(Level.INFO, "Adding annotation k=" + k + " Value=" + annotations.get(k));
                addAttribute(k, annotations.get(k));
            }
        }

        public boolean isMainVertexSet() {
            return mainProcess != null;
        }

        public void setMainProcess(AbstractVertex vertex) {
            assert mainProcess == null;
            mainProcess = vertex;
            addAnotherVertexAnnotations(vertex);
        }

        public void addAttribute(String key, String value) {
            if (!multiAttributes.containsKey(key)) {
                multiAttributes.put(key, new HashSet<String>());
            }
            multiAttributes.get(key).add(value);
        }

        public AbstractVertex getVertex() {
            // Converts all multi-attributes into one comma separated string, 
            // adds it back in the original vertex and returns it

            if (mainProcess == null) {
                StringBuilder builder = new StringBuilder();
                for (String k : multiAttributes.keySet()) {
                    builder.append(k + "=");
                    Set<String> attrs = multiAttributes.get(k);
                    for (String a : attrs) {
                        builder.append(a + ",");
                    }
                    builder.append(" ");
                }
                logger.log(Level.WARNING, "Shelved vertex is null. All attributes: " + builder.toString());
                logger.log(Level.INFO, mainProcess.getAnnotation("pid"));  // Forcefully throw exception
                return null;
            }


            for (String s : multiAttributes.keySet()) {
                StringBuilder builder = new StringBuilder();
                Set<String> attrsSet = multiAttributes.get(s);

                if (s.equals("pid")) {
                    attrsSet.remove(mainProcess.getAnnotation("pid"));
                }

                ArrayList<String> attrs = new ArrayList<String>();
                attrs.addAll(attrsSet);

                if (!attrs.isEmpty()) {
                    builder.append(attrs.remove(0));

                    for (String attr : attrs) {
                        builder.append(", ");
                        builder.append(attr);
                    }
                }


                String strAttrs = builder.toString();

                // logger.log(Level.INFO, "Setting attributes" + strAttrs);

                if (s.equals("pid")) {
                    // PID should be same as of original Process
                    mainProcess.addAnnotation("tpids", strAttrs);
                } else {
                    mainProcess.addAnnotation(s, strAttrs);
                }
            }

            return mainProcess;
        }
    }
    // Curernt active process node for merging threads, mapped by PID (String)
    private Map<String, ShelvedProcess> currentMainProcessNode = new HashMap<String, ShelvedProcess>();
    // Reference to flushed out processes
    private Map<String, AbstractVertex> flushedOutVertices = new HashMap<String, AbstractVertex>();
    private ArrayList<AbstractEdge> shelvedEdges = new ArrayList<AbstractEdge>();
    private Set<AbstractVertex> shelvedThreads = new HashSet<AbstractVertex>();

    @Override
    public void putVertex(AbstractVertex incomingVertex) {

        if (incomingVertex.type().equalsIgnoreCase("EOS")) {
            EOE();
            return;
        }

        String dtgid = incomingVertex.getAnnotation("tgid");
        if (dtgid != null && dtgid.equals("492"))
        	logger.log(Level.INFO, "Processing vertex " + dtgid);
        
        if (incomingVertex.type().equalsIgnoreCase("Process")) {

            String pid = incomingVertex.getAnnotation("pid");
            String tgid = incomingVertex.getAnnotation("tgid");
            
            // logger.log(Level.INFO, "Processing PID:" + pid + " TGID:" + tgid);

            // Its a process
            if (pid.equals(tgid)) {

                // Remove the previous entry, if any
                ShelvedProcess prevShelvedProcess = currentMainProcessNode.get(pid);
                if (prevShelvedProcess == null || (prevShelvedProcess != null && prevShelvedProcess.isMainVertexSet())) {
                    flushVertex(pid);
                    // Shelf this vertex
                    currentMainProcessNode.put(pid, new ShelvedProcess(incomingVertex));
                    // logger.log(Level.INFO, "Process Shelved PID: " + pid);
                } else {
                    // We've previously seen the thread but not the process. Now we're seeing the process so just register it.
                    assert !prevShelvedProcess.isMainVertexSet();
                    prevShelvedProcess.setMainProcess(incomingVertex);
                }



            } else { // If its a thread

                ShelvedProcess shelvedProcess = currentMainProcessNode.get(tgid);

                if (shelvedProcess == null) {

                    // hoping that we'll see the process sometime, so lets shelve this thread
                    shelvedProcess = new ShelvedProcess();
                    currentMainProcessNode.put(tgid, shelvedProcess);
                }

                shelvedProcess.addAnotherVertexAnnotations(incomingVertex);
                // logger.log(Level.INFO, "Thread Shelved PID: " + pid);
            }

        } else {
            putInNextFilter(incomingVertex);
        }
    }


    @Override
    public void putEdge(AbstractEdge incomingEdge) {

        // logger.info("Shelving edge " + incomingEdge.toString());
        shelvedEdges.add(incomingEdge);
        
    }

    /*
     * End-Of-Events : Finish processing and flush everything pendning
     */
    public void EOE() {

        logger.log(Level.INFO, "EOE received. Ending stream ");
        
        for (String pid : currentMainProcessNode.keySet()) {

            logger.log(Level.INFO, "Flushing vertex: " + pid);

            if (pid.equals("492")) {
                logger.log(Level.INFO, "Processing 492 now");
            }

            if (!flushVertex(pid)) {

                ShelvedProcess shelvedProcess = currentMainProcessNode.get(pid);

                if (!shelvedProcess.isMainVertexSet()) {
                    // The main process for a thread or set of threads was not received
                    // so just add a new dummy node for it 
                    // so that shelved edges will correctly map to a node when flushing out
                	
                	logger.log(Level.WARNING, "Main Process of Thread Group " + pid + " was not set. Adding artificial vertex.");
                	Process p = new Process();
                    p.addAnnotation("pid", pid);
                    currentMainProcessNode.get(pid).setMainProcess(p);
                    
                    if(!flushVertex(pid)) {
                    	logger.log(Level.WARNING, "Something went wrong flushing Thread Group ID " + pid + ". Continuing with rest.");
                    }
                } 
               
            }
        }


        logger.log(Level.INFO, "Flushing edges " + Integer.toString(shelvedEdges.size()));

        for (AbstractEdge incomingEdge : shelvedEdges) {

            AbstractVertex sourceVertex = incomingEdge.getSourceVertex();
            AbstractVertex destinationVertex = incomingEdge.getDestinationVertex();

            if (sourceVertex.type().equalsIgnoreCase("Process")) {
                String pid = sourceVertex.getAnnotation("pid");
                String tgid = sourceVertex.getAnnotation("tgid");

                
                String mappedPid = pid.equals(tgid) ? pid : tgid;
                
                if (currentMainProcessNode.containsKey(mappedPid)) {
                    incomingEdge.setSourceVertex(currentMainProcessNode.get(mappedPid).getVertex());
                } else {
                    incomingEdge.setSourceVertex(flushedOutVertices.get(mappedPid));
                }
                
            }
            
            if (destinationVertex.type().equalsIgnoreCase("Process")) {
                String pid = destinationVertex.getAnnotation("pid");
                String tgid = destinationVertex.getAnnotation("tgid");
                
                String mappedPid = pid.equals(tgid) ? pid : tgid;
                
                if (currentMainProcessNode.containsKey(mappedPid)) {
                    incomingEdge.setDestinationVertex(currentMainProcessNode.get(mappedPid).getVertex());
                } else {
                    incomingEdge.setDestinationVertex(flushedOutVertices.get(mappedPid));
                }
            
            }

            putInNextFilter(incomingEdge);
        }
        
    }

    private boolean flushVertex(String pid) {

        // Flush previously shelved vertices, if any

        ShelvedProcess previousProcess = currentMainProcessNode.get(pid);
        if (previousProcess != null && previousProcess.isMainVertexSet()) {
            putInNextFilter(previousProcess.getVertex());
            return true;
        }
        return false;

    }
}