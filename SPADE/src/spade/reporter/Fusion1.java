/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package spade.reporter;

import spade.core.AbstractReporter;
import spade.opm.edge.WasDerivedFrom;
import spade.opm.vertex.Artifact;

/**
 *
 * @author dawood
 */
public class Fusion1 extends AbstractReporter {

    @Override
    public boolean launch(String arguments) {
        
        Runnable fuse1 = new Runnable() {

            public void run() {
                try {
                    Artifact previousArtifact = null, currentArtifact = null;
                    Thread.sleep(1000);
                    for (int i=0; i<20; i++) {
                        currentArtifact = new Artifact();
                        currentArtifact.addAnnotation("pidname", "leftreporter_" + i);
                        currentArtifact.addAnnotation("key", Integer.toString(i));
                        putVertex(currentArtifact);
                        Thread.sleep(100);
                        
                        if ((i%2 == 1) && (previousArtifact != null)) {
                            WasDerivedFrom wdf = new WasDerivedFrom(currentArtifact, previousArtifact);
                            wdf.addAnnotation("from", "left");
                            putEdge(wdf);
                        }
                        previousArtifact = currentArtifact;
                    }
                } catch (Exception ex) {}
            }
        };
        new Thread(fuse1, "fuse1").start();
        
        return true;
    }

    @Override
    public boolean shutdown() {
        return true;
    }
    
}
