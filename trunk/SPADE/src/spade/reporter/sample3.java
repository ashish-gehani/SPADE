/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package spade.reporter;

import java.util.Date;
import spade.core.AbstractReporter;
import spade.opm.edge.Used;
import spade.opm.vertex.Artifact;
import spade.opm.vertex.Network;
import spade.opm.vertex.Process;

/**
 *
 * @author dawood
 */
public class sample3 extends AbstractReporter {

    @Override
    public boolean launch(String arguments) {
        Network n1 = new Network();
        n1.addAnnotation("source host", "planetlab-04.cs.princeton.edu");
        n1.addAnnotation("source port", "22");
        n1.addAnnotation("destination host", "planetlab-03.cs.princeton.edu");
        n1.addAnnotation("destination port", "22");
        n1.addAnnotation("time", (new Date()).toString());
        putVertex(n1);
        
        Process p3 = new Process();
        p3.addAnnotation("pidname", "process3");
        putVertex(p3);
        
        Used used = new Used(p3, n1);
        putEdge(used);

        Artifact a1 = new Artifact();
        a1.addAnnotation("filename", "temp file 2");
        putVertex(a1);
        
        Used used2 = new Used(p3, a1);
        putEdge(used2);

        return true;
    }

    @Override
    public boolean shutdown() {
        return true;
    }
    
}
