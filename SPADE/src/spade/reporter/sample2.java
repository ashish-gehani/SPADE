/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package spade.reporter;

import java.util.Date;
import spade.core.AbstractReporter;
import spade.opm.edge.Used;
import spade.opm.edge.WasGeneratedBy;
import spade.opm.vertex.Network;
import spade.opm.vertex.Process;

/**
 *
 * @author dawood
 */
public class sample2 extends AbstractReporter {

    @Override
    public boolean launch(String arguments) {
        Network n1 = new Network();
        n1.addAnnotation("source host", "planetlab-03.cs.princeton.edu");
        n1.addAnnotation("source port", "22");
        n1.addAnnotation("destination host", "planetlab-02.cs.princeton.edu");
        n1.addAnnotation("destination port", "22");
        n1.addAnnotation("time", (new Date()).toString());
        putVertex(n1);
        
        Process p2 = new Process();
        p2.addAnnotation("pidname", "process2");
        putVertex(p2);
        
        Used used = new Used(p2, n1);
        putEdge(used);
        
        Network n2 = new Network();
        n2.addAnnotation("source host", "planetlab-03.cs.princeton.edu");
        n2.addAnnotation("source port", "22");
        n2.addAnnotation("destination host", "planetlab-04.cs.princeton.edu");
        n2.addAnnotation("destination port", "22");
        n2.addAnnotation("time", (new Date()).toString());
        putVertex(n2);
        
        WasGeneratedBy wgb = new WasGeneratedBy(n2, p2);
        putEdge(wgb);
        
        return true;
    }

    @Override
    public boolean shutdown() {
        return true;
    }
    
}
