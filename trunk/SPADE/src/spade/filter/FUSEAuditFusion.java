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
package spade.filter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;

public class FUSEAuditFusion extends AbstractFilter {

    private HashSet<AbstractVertex> FUSESet;
    private HashSet<AbstractVertex> AuditSet;
    private HashSet<AbstractEdge> EdgeSet;
    private HashMap<Integer, AbstractVertex> fusedVertices;

    private HashMap<AbstractVertex, Long> vertexDelays;
    private HashMap<AbstractEdge, Long> edgeDelays;
    
    private final int THREAD_DELAY_TIME = 5000;

    public FUSEAuditFusion() {
        FUSESet = new HashSet<AbstractVertex>();
        AuditSet = new HashSet<AbstractVertex>();
        EdgeSet = new HashSet<AbstractEdge>();
        fusedVertices = new HashMap<Integer, AbstractVertex>();
        
        vertexDelays = new HashMap<AbstractVertex, Long>();
        edgeDelays = new HashMap<AbstractEdge, Long>();
        
        Runnable checkDelays = new Runnable() {

            public void run() {
                try {
                    while (true) {
                        
                        Thread.sleep(THREAD_DELAY_TIME);
                    }
                } catch (Exception exception) {
                    Logger.getLogger(FUSEAuditFusion.class.getName()).log(Level.SEVERE, null, exception);
                }
            }
        };
        new Thread(checkDelays, "checkDelays").start();        
    }

    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        if (fusedVertices.containsKey(incomingVertex.hashCode())) {
            putInNextFilter(fusedVertices.get(incomingVertex.hashCode()));
            return;
        }
        if (incomingVertex.getAnnotation("source_reporter").equalsIgnoreCase("spade.reporter.LinuxFUSE")) {
            
        } else if (incomingVertex.getAnnotation("source_reporter").equalsIgnoreCase("spade.reporter.LinuxAudit")) {

        }
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
        if (fusedVertices.containsKey(incomingEdge.getSourceVertex().hashCode())) {
            incomingEdge.setSourceVertex(fusedVertices.get(incomingEdge.getSourceVertex().hashCode()));
        }
        if (fusedVertices.containsKey(incomingEdge.getDestinationVertex().hashCode())) {
            incomingEdge.setDestinationVertex(fusedVertices.get(incomingEdge.getDestinationVertex().hashCode()));
        }
        
        putInNextFilter(incomingEdge);
    }    
}
