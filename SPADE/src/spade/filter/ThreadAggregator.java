package spade.filter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.edge.opm.Used;
import spade.edge.opm.WasGeneratedBy;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

public class ThreadAggregator extends AbstractFilter {

	Set<AbstractVertex> processes = new HashSet<AbstractVertex>();
	Map<String, AbstractVertex> aggregates = new HashMap<String, AbstractVertex>();

	@Override
	public void putVertex(AbstractVertex incomingVertex) {
		putInNextFilter(incomingVertex);
		if (incomingVertex.type().equalsIgnoreCase("Process")) {
			String tgid = incomingVertex.getAnnotation("tgid");
			if (!processes.contains(incomingVertex)) {
				if (!aggregates.containsKey(tgid)) {
					Artifact aggregate = new Artifact();
					aggregate.addAnnotation("location", "threadgroup:[" + tgid + "]");
					putInNextFilter(aggregate);
					aggregates.put(tgid, aggregate);
				}
				Artifact aggregate = (Artifact) aggregates.get(tgid);
				AbstractEdge r = new Used((Process) incomingVertex, aggregate);
				AbstractEdge e = new WasGeneratedBy(aggregate, (Process) incomingVertex);
				putInNextFilter(r);
				putInNextFilter(e);
				processes.add(incomingVertex);
			}
		}
	}

	@Override
	public void putEdge(AbstractEdge incomingEdge) {
		putInNextFilter(incomingEdge);
	}

}
