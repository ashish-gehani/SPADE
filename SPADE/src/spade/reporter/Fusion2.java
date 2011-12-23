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
public class Fusion2 extends AbstractReporter {

    @Override
    public boolean launch(String arguments) {
        
        Runnable fuse2 = new Runnable() {

            public void run() {
                try {
                    Artifact previousArtifact = null, currentArtifact = null;
                    Thread.sleep(1000);
                    for (int i=0; i<20; i++) {
                        currentArtifact = new Artifact();
                        currentArtifact.addAnnotation("key", Integer.toString(i));
                        currentArtifact.addAnnotation("filename", "rightreporter_" + i);
                        putVertex(currentArtifact);
                        Thread.sleep(100);

                        if ((i%2 == 0) && (previousArtifact != null)) {
                            WasDerivedFrom wdf = new WasDerivedFrom(currentArtifact, previousArtifact);
                            wdf.addAnnotation("from", "right");
                            putEdge(wdf);
                        }
                        previousArtifact = currentArtifact;
                    }
                } catch (Exception ex) {}
            }
        };
        new Thread(fuse2, "fuse2").start();
        
        return true;
    }

    @Override
    public boolean shutdown() {
        return true;
    }
    
}
