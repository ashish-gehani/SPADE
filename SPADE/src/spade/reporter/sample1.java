/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package spade.reporter;

import java.util.Date;
import spade.core.AbstractReporter;
import spade.opm.edge.WasGeneratedBy;
import spade.opm.vertex.Artifact;
import spade.opm.vertex.Network;
import spade.opm.vertex.Process;

/**
 *
 * @author dawood
 */
public class sample1 extends AbstractReporter {

    @Override
    public boolean launch(String arguments) {
        Process p1 = new Process();
        p1.addAnnotation("pidname", "process1");
        putVertex(p1);
        
        Network n1 = new Network();
        n1.addAnnotation("source host", "planetlab-02.cs.princeton.edu");
        n1.addAnnotation("source port", "22");
        n1.addAnnotation("destination host", "planetlab02.cs.washington.edu");
        n1.addAnnotation("destination port", "22");
        n1.addAnnotation("time", (new Date()).toString());
        putVertex(n1);
        
        WasGeneratedBy wgb = new WasGeneratedBy(n1, p1);
        putEdge(wgb);
        
        Artifact a1 = new Artifact();
        a1.addAnnotation("filename", "temp file 1");
        putVertex(a1);
        
        WasGeneratedBy wgb2 = new WasGeneratedBy(a1, p1);
        putEdge(wgb2);        
        
        return true;
    }

    @Override
    public boolean shutdown() {
        return true;
    }
    
}
