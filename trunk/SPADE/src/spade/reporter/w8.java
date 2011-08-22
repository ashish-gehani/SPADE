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
public class w8 extends AbstractReporter {

    public static int hostNumber = 8;
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
        Process p2 = new Process();
        p2.addAnnotation("pidname", "process");
        putVertex(p2);

        Network n1 = new Network();
        n1.addAnnotation("source host", hosts[hostNumber - 1]);
        n1.addAnnotation("source port", "22");
        n1.addAnnotation("destination host", hosts[hostNumber - 2]);
        n1.addAnnotation("destination port", "22");
        n1.addAnnotation("time", (new Date()).toString());
        putVertex(n1);

        Used used = new Used(p2, n1);
        putEdge(used);

        Network n2 = new Network();
        n2.addAnnotation("source host", hosts[hostNumber - 1]);
        n2.addAnnotation("source port", "22");
        n2.addAnnotation("destination host", hosts[hostNumber]);
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
