package spade.filter;

import java.util.Set;
import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractSketch;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;

public class FinalCommitFilter extends AbstractFilter {

    // Reference to the set of storages maintained by the Kernel.
    public Set<AbstractStorage> storages;
    public Set<AbstractSketch> sketches;

    // This filter is the last filter in the list so any vertices or edges
    // received by it need to be passed to the storages. On receiving any
    // provenance elements, it is passed to all storages.
    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        for (AbstractStorage storage : storages) {
            if (storage.putVertex(incomingVertex)) {
                storage.vertexCount++;
            }
        }
        for (AbstractSketch sketch : sketches) {
            sketch.putVertex(incomingVertex);
        }
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
        for (AbstractStorage storage : storages) {
            if (storage.putEdge(incomingEdge)) {
                storage.edgeCount++;
            }
        }
        for (AbstractSketch sketch : sketches) {
            sketch.putEdge(incomingEdge);
        }
    }
}