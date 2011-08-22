/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package spade.reporter;

import java.util.Date;
import spade.core.AbstractReporter;
import spade.opm.edge.WasGeneratedBy;
import spade.opm.vertex.Network;
import spade.opm.vertex.Process;

/**
 *
 * @author dawood
 */
public class w1 extends AbstractReporter {

    public static int hostNumber = 1;
    public static String hosts[] = {
        "planetlab01.cs.washington.edu",
        "pl-node-0.csl.sri.com",
        "planetlab02.cs.washington.edu",
        "planetlab-01.cs.princeton.edu",
        "planetlab03.cs.washington.edu",
        "planetlab-02.cs.princeton.edu",
        "planetlab04.cs.washington.edu",
        "planetlab-04.cs.princeton.edu",
        "planetlab06.cs.washington.edu"
    };

    @Override
    public boolean launch(String arguments) {
        Process p1 = new Process();
        p1.addAnnotation("pidname", "process");
        putVertex(p1);
        
        Network n1 = new Network();
        n1.addAnnotation("source host", hosts[hostNumber - 1]);
        n1.addAnnotation("source port", "22");
        n1.addAnnotation("destination host", hosts[hostNumber]);
        n1.addAnnotation("destination port", "22");
        n1.addAnnotation("time", (new Date()).toString());
        putVertex(n1);
        
        WasGeneratedBy wgb = new WasGeneratedBy(n1, p1);
        putEdge(wgb);
        
        return true;
    }

    @Override
    public boolean shutdown() {
        return true;
    }
    
}
