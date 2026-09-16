/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International

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
package spade.query.quickgrail.instruction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import spade.core.AbstractStorage;
import spade.query.quickgrail.core.AbstractQueryEnvironment;
import spade.query.quickgrail.core.GraphDescription;
import spade.query.quickgrail.core.GraphStatistic;
import spade.query.quickgrail.core.QueriedEdge;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.QuickGrailQueryResolver.PredicateOperator;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.entities.GraphMetadata;
import spade.query.quickgrail.instruction.DescribeGraph;
import spade.query.quickgrail.instruction.DescribeGraph.ElementType;
import spade.query.quickgrail.instruction.GetEdgeEndpoint.Component;
import spade.query.quickgrail.instruction.GetLineage;
import spade.query.quickgrail.instruction.SetGraphMetadata;
import spade.query.quickgrail.utility.ResultTable;

/**
 * In-memory query harness for exercising QuickGrail graph-method
 * instructions without a real storage backend.
 *
 * Layout:
 *   - {@link Env} extends {@link AbstractQueryEnvironment} with no-op
 *     persistence (every save/delete callback is a no-op since there
 *     is nothing to persist to).
 *   - {@link Executor} extends {@link QueryInstructionExecutor} and
 *     implements every primitive that {@link GetContainerBoundary}
 *     and {@link GetContainerInit} call. All other abstract methods
 *     throw {@link UnsupportedOperationException} so that any test
 *     reaching them is loudly broken rather than silently wrong.
 *
 * Data model:
 *   - {@link Executor#verticesByHash} and {@link Executor#edgesByHash}
 *     hold the entire test fixture.
 *   - {@link Executor#graphsByName} maps each allocated graph (the
 *     base graph plus everything {@code createEmptyGraph} produces)
 *     to the subset of vertex and edge hashes belonging to it.
 *   - The base graph is populated up-front by tests via
 *     {@link Executor#putVertex} / {@link Executor#putEdge}; every
 *     other graph is derived by an instruction at runtime.
 */
public final class InMemoryQueryHarness{

	public final Env env;
	public final Executor executor;
	public final Graph baseGraph;

	public InMemoryQueryHarness(){
		this.env = new Env("base");
		this.executor = new Executor(env);
		this.baseGraph = env.getBaseGraph();
		// Allocate the base graph so it shows up in graphsByName.
		this.executor.createEmptyGraph(baseGraph);
	}

	// =========================================================================
	// Test-facing fixture builders
	// =========================================================================

	public String putVertex(final String hash, final String... annotationPairs){
		return executor.putVertex(hash, annotationPairs);
	}

	public String putEdge(final String hash, final String childHash, final String parentHash,
			final String operation, final String... extraAnnotationPairs){
		return executor.putEdge(hash, childHash, parentHash, operation, extraAnnotationPairs);
	}

	// =========================================================================
	// Env: no-op subclass of AbstractQueryEnvironment.
	// =========================================================================

	public static final class Env extends AbstractQueryEnvironment{

		public Env(final String baseGraphName){
			super(baseGraphName);
		}

		@Override public void doGarbageCollection(){ /* in-memory; nothing to GC */ }
		@Override public void createSymbolStorageIfNotPresent(){ }
		@Override public void deleteSymbolStorageIfPresent(){ }
		@Override public int readIdCount(){ return 0; }
		@Override public Map<String, Graph> readGraphSymbols(){ return Collections.emptyMap(); }
		@Override public void readRemoteSymbols(final Graph graph){ }
		@Override public Map<String, String> readMetadataSymbols(){ return Collections.emptyMap(); }
		@Override public Map<String, String> readPredicateSymbols(){ return Collections.emptyMap(); }
		@Override public void saveIdCounter(final int idCounter){ }
		@Override public void saveGraphSymbol(final String symbol, final String graphName, final boolean wasPresent){ }
		@Override public void saveRemoteSymbol(final Graph graph, final Graph.Remote remote){ }
		@Override public void saveMetadataSymbol(final String symbol, final String name, final boolean wasPresent){ }
		@Override public void savePredicateSymbol(final String symbol, final String pred, final boolean wasPresent){ }
		@Override public void deleteGraphSymbol(final String symbol){ }
		@Override public void deleteRemoteSymbol(final Graph graph, final Graph.Remote remote){ }
		@Override public void deleteRemoteSymbols(final Graph graph){ }
		@Override public void deleteMetadataSymbol(final String symbol){ }
		@Override public void deletePredicateSymbol(final String symbol){ }
	}

	// =========================================================================
	// Executor: in-memory implementation of the primitives we exercise.
	// =========================================================================

	public static final class Executor extends QueryInstructionExecutor{

		private final Env env;

		// All vertices and edges that exist in the fixture, keyed by hash.
		final Map<String, Map<String, String>> verticesByHash = new HashMap<String, Map<String, String>>();
		final Map<String, QueriedEdge> edgesByHash = new HashMap<String, QueriedEdge>();

		// Per-graph subsets (graph.name → subset of vertex/edge hashes).
		private final Map<String, GraphData> graphsByName = new HashMap<String, GraphData>();

		Executor(final Env env){
			this.env = env;
		}

		@Override public AbstractQueryEnvironment getQueryEnvironment(){ return env; }
		@Override public AbstractStorage getStorage(){ return null; }

		// Test fixture helpers -------------------------------------------------

		public String putVertex(final String hash, final String... annotationPairs){
			final Map<String, String> annotations = new TreeMap<String, String>();
			for(int i = 0; i + 1 < annotationPairs.length; i += 2){
				annotations.put(annotationPairs[i], annotationPairs[i + 1]);
			}
			verticesByHash.put(hash, annotations);
			graphsByName.get(env.getBaseGraph().name).vertexHashes.add(hash);
			return hash;
		}

		public String putEdge(final String hash, final String childHash, final String parentHash,
				final String operation, final String... extraAnnotationPairs){
			final Map<String, String> annotations = new TreeMap<String, String>();
			annotations.put("operation", operation);
			for(int i = 0; i + 1 < extraAnnotationPairs.length; i += 2){
				annotations.put(extraAnnotationPairs[i], extraAnnotationPairs[i + 1]);
			}
			edgesByHash.put(hash, new QueriedEdge(hash, childHash, parentHash, annotations));
			graphsByName.get(env.getBaseGraph().name).edgeHashes.add(hash);
			return hash;
		}

		GraphData data(final Graph g){
			final GraphData d = graphsByName.get(g.name);
			if(d == null){
				throw new IllegalStateException("Unknown graph: " + g.name);
			}
			return d;
		}

		// Primitives the instructions actually call ---------------------------

		@Override
		public void createEmptyGraph(final Graph graph){
			graphsByName.put(graph.name, new GraphData());
		}

		@Override
		public void unionGraph(final Graph target, final Graph source){
			final GraphData t = data(target);
			final GraphData s = data(source);
			t.vertexHashes.addAll(s.vertexHashes);
			t.edgeHashes.addAll(s.edgeHashes);
		}

		@Override
		public void intersectGraph(final Graph output, final Graph lhs, final Graph rhs){
			final GraphData o = data(output);
			final GraphData l = data(lhs);
			final GraphData r = data(rhs);
			o.vertexHashes.addAll(l.vertexHashes);
			o.vertexHashes.retainAll(r.vertexHashes);
			o.edgeHashes.addAll(l.edgeHashes);
			o.edgeHashes.retainAll(r.edgeHashes);
		}

		@Override
		public void getWhereAnnotationsExist(final Graph target, final Graph subject,
				final ArrayList<String> annotationNames){
			final GraphData t = data(target);
			final GraphData s = data(subject);
			for(final String hash : s.vertexHashes){
				final Map<String, String> annotations = verticesByHash.get(hash);
				if(annotations == null) continue;
				boolean allPresent = true;
				for(final String key : annotationNames){
					if(!annotations.containsKey(key)){ allPresent = false; break; }
				}
				if(allPresent){ t.vertexHashes.add(hash); }
			}
		}

		@Override
		public void getVertex(final Graph target, final Graph subject, final String key,
				final PredicateOperator op, final String value, final boolean hasArguments){
			final GraphData t = data(target);
			final GraphData s = data(subject);
			for(final String hash : s.vertexHashes){
				final Map<String, String> ann = verticesByHash.get(hash);
				if(ann == null) continue;
				if(matches(ann.get(key), op, value)){
					t.vertexHashes.add(hash);
				}
			}
		}

		@Override
		public void getEdge(final Graph target, final Graph subject, final String key,
				final PredicateOperator op, final String value, final boolean hasArguments){
			final GraphData t = data(target);
			final GraphData s = data(subject);
			for(final String hash : s.edgeHashes){
				final QueriedEdge edge = edgesByHash.get(hash);
				if(edge == null) continue;
				if(matches(edge.getCopyOfAnnotations().get(key), op, value)){
					t.edgeHashes.add(hash);
				}
			}
		}

		@Override
		public void getEdgeEndpoint(final Graph target, final Graph subject, final Component component){
			final GraphData t = data(target);
			final GraphData s = data(subject);
			for(final String edgeHash : s.edgeHashes){
				final QueriedEdge e = edgesByHash.get(edgeHash);
				if(e == null) continue;
				switch(component){
					case kSource:      t.vertexHashes.add(e.childHash); break;
					case kDestination: t.vertexHashes.add(e.parentHash); break;
					case kBoth:        t.vertexHashes.add(e.childHash);
					                   t.vertexHashes.add(e.parentHash); break;
				}
			}
		}

		@Override
		public void getAdjacentVertex(final Graph target, final Graph subject, final Graph source,
				final GetLineage.Direction direction){
			final GraphData t = data(target);
			final GraphData srcData = data(source);
			final GraphData subjData = data(subject);
			for(final String edgeHash : subjData.edgeHashes){
				final QueriedEdge e = edgesByHash.get(edgeHash);
				if(e == null) continue;
				final boolean childInSrc  = srcData.vertexHashes.contains(e.childHash);
				final boolean parentInSrc = srcData.vertexHashes.contains(e.parentHash);
				switch(direction){
					case kAncestor:
						if(childInSrc) t.vertexHashes.add(e.parentHash);
						break;
					case kDescendant:
						if(parentInSrc) t.vertexHashes.add(e.childHash);
						break;
					case kBoth:
						if(childInSrc) t.vertexHashes.add(e.parentHash);
						if(parentInSrc) t.vertexHashes.add(e.childHash);
						break;
				}
			}
		}

		@Override
		public void getSubgraph(final Graph target, final Graph subject, final Graph skeleton){
			final GraphData t = data(target);
			final GraphData subj = data(subject);
			final GraphData skel = data(skeleton);

			// Vertex set = (skeleton.vertices ∪ endpoints of skeleton.edges) ∩ subject.vertices
			final Set<String> candidateVertices = new LinkedHashSet<String>(skel.vertexHashes);
			for(final String edgeHash : skel.edgeHashes){
				final QueriedEdge e = edgesByHash.get(edgeHash);
				if(e == null) continue;
				candidateVertices.add(e.childHash);
				candidateVertices.add(e.parentHash);
			}
			candidateVertices.retainAll(subj.vertexHashes);
			t.vertexHashes.addAll(candidateVertices);

			// Edge set = edges in subject whose both endpoints landed in t.vertexHashes
			for(final String edgeHash : subj.edgeHashes){
				final QueriedEdge e = edgesByHash.get(edgeHash);
				if(e == null) continue;
				if(t.vertexHashes.contains(e.childHash) && t.vertexHashes.contains(e.parentHash)){
					t.edgeHashes.add(edgeHash);
				}
			}
		}

		@Override
		public void getSimplePath(final Graph target, final Graph subject, final Graph srcGraph,
				final Graph dstGraph, final int maxDepth){
			final GraphData t = data(target);
			final GraphData subj = data(subject);
			final GraphData src = data(srcGraph);
			final GraphData dst = data(dstGraph);

			// Build forward adjacency restricted to subject's edges.
			// SPADE OPM convention: edge child → parent; "follow the edge" goes child → parent.
			final Map<String, List<String>> adjEdges = new HashMap<String, List<String>>();
			for(final String edgeHash : subj.edgeHashes){
				final QueriedEdge e = edgesByHash.get(edgeHash);
				if(e == null) continue;
				adjEdges.computeIfAbsent(e.childHash, k -> new ArrayList<String>()).add(edgeHash);
			}

			// BFS from each source vertex, recording the path-of-edges so far.
			for(final String startHash : src.vertexHashes){
				final Deque<List<String>> queue = new ArrayDeque<List<String>>();
				queue.add(new ArrayList<String>(Collections.singletonList(startHash))); // path of vertex hashes
				int depth = 0;
				final Set<String> visited = new HashSet<String>();
				visited.add(startHash);

				while(!queue.isEmpty() && depth < maxDepth){
					final int frontierSize = queue.size();
					for(int i = 0; i < frontierSize; i++){
						final List<String> path = queue.poll();
						final String tail = path.get(path.size() - 1);
						final List<String> outgoing = adjEdges.get(tail);
						if(outgoing == null) continue;
						for(final String edgeHash : outgoing){
							final QueriedEdge e = edgesByHash.get(edgeHash);
							final String next = e.parentHash;
							if(!subj.vertexHashes.contains(next)) continue;
							if(visited.contains(next)) continue;
							final List<String> newPath = new ArrayList<String>(path);
							newPath.add(next);
							if(dst.vertexHashes.contains(next)){
								// Path complete — fold every vertex and edge along it into target.
								absorbPath(t, newPath, subj);
							}
							visited.add(next);
							queue.add(newPath);
						}
					}
					depth++;
				}
			}
		}

		private void absorbPath(final GraphData t, final List<String> vertexPath, final GraphData subj){
			for(int i = 0; i < vertexPath.size(); i++){
				t.vertexHashes.add(vertexPath.get(i));
				if(i == 0) continue;
				final String prev = vertexPath.get(i - 1);
				final String curr = vertexPath.get(i);
				// Find the edge that connects (prev, curr) child→parent direction.
				for(final String edgeHash : subj.edgeHashes){
					final QueriedEdge e = edgesByHash.get(edgeHash);
					if(e == null) continue;
					if(prev.equals(e.childHash) && curr.equals(e.parentHash)){
						t.edgeHashes.add(edgeHash);
						break;
					}
				}
			}
		}

		@Override
		public GraphStatistic.Count getGraphCount(final Graph graph){
			final GraphData d = data(graph);
			return new GraphStatistic.Count(d.vertexHashes.size(), d.edgeHashes.size());
		}

		@Override
		public void insertLiteralEdge(final Graph target, final ArrayList<String> edges){
			final GraphData t = data(target);
			t.edgeHashes.addAll(edges);
		}

		@Override
		public Map<String, Map<String, String>> exportVertices(final Graph graph){
			final GraphData d = data(graph);
			final Map<String, Map<String, String>> out = new HashMap<String, Map<String, String>>();
			for(final String h : d.vertexHashes){
				final Map<String, String> ann = verticesByHash.get(h);
				if(ann != null){
					out.put(h, new HashMap<String, String>(ann));
				}
			}
			return out;
		}

		@Override
		public Set<QueriedEdge> exportEdges(final Graph graph){
			final GraphData d = data(graph);
			final Set<QueriedEdge> out = new HashSet<QueriedEdge>();
			for(final String h : d.edgeHashes){
				final QueriedEdge e = edgesByHash.get(h);
				if(e != null){
					out.add(new QueriedEdge(e.edgeHash, e.childHash, e.parentHash, e.getCopyOfAnnotations()));
				}
			}
			return out;
		}

		// Helpers --------------------------------------------------------------

		private static boolean matches(final String actual, final PredicateOperator op, final String value){
			if(actual == null) return false;
			switch(op){
				case EQUAL:         return actual.equals(value);
				case NOT_EQUAL:     return !actual.equals(value);
				case LIKE:          return actual.contains(value.replace("%", ""));
				case GREATER:       return compareNum(actual, value) >  0;
				case GREATER_EQUAL: return compareNum(actual, value) >= 0;
				case LESSER:        return compareNum(actual, value) <  0;
				case LESSER_EQUAL:  return compareNum(actual, value) <= 0;
				case REGEX:         return actual.matches(value);
				default:            return false;
			}
		}

		private static int compareNum(final String a, final String b){
			try{ return Long.compare(Long.parseLong(a), Long.parseLong(b)); }
			catch(NumberFormatException e){ return a.compareTo(b); }
		}

		// Everything else: explicit "this test path does not need me" -----------

		private static <T> T no(final String name){
			throw new UnsupportedOperationException(
					"InMemoryQueryHarness.Executor." + name + " is not implemented; "
					+ "if a test reaches it, extend the harness rather than silently passing.");
		}

		@Override public void collapseEdge(final Graph t, final Graph s, final ArrayList<String> f){ no("collapseEdge"); }
		@Override public void createEmptyGraphMetadata(final GraphMetadata m){ no("createEmptyGraphMetadata"); }
		@Override public GraphDescription describeGraph(final DescribeGraph i){ return no("describeGraph"); }
		@Override public void distinctifyGraph(final Graph t, final Graph s){
			// distinctify is added by the resolver after every assignment; for in-memory tests
			// the underlying sets are already de-duplicated, so a copy is sufficient.
			final GraphData td = data(t);
			final GraphData sd = data(s);
			td.vertexHashes.addAll(sd.vertexHashes);
			td.edgeHashes.addAll(sd.edgeHashes);
		}
		@Override public ResultTable evaluateQuery(final String n){ return no("evaluateQuery"); }
		@Override public GraphStatistic.Distribution getGraphDistribution(final Graph g, final ElementType t, final String k, final Integer b){ return no("getGraphDistribution"); }
		@Override public GraphStatistic.Histogram getGraphHistogram(final Graph g, final ElementType t, final String k){ return no("getGraphHistogram"); }
		@Override public GraphStatistic.Mean getGraphMean(final Graph g, final ElementType t, final String k){ return no("getGraphMean"); }
		@Override public GraphStatistic.StandardDeviation getGraphStandardDeviation(final Graph g, final ElementType t, final String k){ return no("getGraphStandardDeviation"); }
		@Override public long getGraphStatisticSize(final Graph g, final ElementType t, final String k){ no("getGraphStatisticSize"); return 0L; }
		@Override public void getLineage(final Graph t, final Graph s, final Graph st, final int d, final GetLineage.Direction dir){ no("getLineage"); }
		@Override public void getLink(final Graph t, final Graph s, final Graph srcG, final Graph dstG, final int d){ no("getLink"); }
		@Override public void getMatch(final Graph t, final Graph g1, final Graph g2, final ArrayList<String> a){ no("getMatch"); }
		@Override public void getShortestPath(final Graph t, final Graph s, final Graph srcG, final Graph dstG, final int d){ no("getShortestPath"); }
		@Override public void insertLiteralVertex(final Graph t, final ArrayList<String> v){ no("insertLiteralVertex"); }
		@Override public void limitGraph(final Graph t, final Graph s, final int l){ no("limitGraph"); }
		@Override public void overwriteGraphMetadata(final GraphMetadata t, final GraphMetadata l, final GraphMetadata r){ no("overwriteGraphMetadata"); }
		@Override public void setGraphMetadata(final GraphMetadata t, final SetGraphMetadata.Component c, final Graph s, final String n, final String v){ no("setGraphMetadata"); }
		@Override public void subtractGraph(final Graph o, final Graph m, final Graph s, final Graph.Component c){ no("subtractGraph"); }
		@Override public void getSubsetVertex(final Graph t, final Graph s, final long f, final long to){ no("getSubsetVertex"); }
		@Override public void getSubsetEdge(final Graph t, final Graph s, final long f, final long to){ no("getSubsetEdge"); }
	}

	// =========================================================================
	// Per-graph subset bookkeeping.
	// =========================================================================

	static final class GraphData{
		final Set<String> vertexHashes = new LinkedHashSet<String>();
		final Set<String> edgeHashes   = new LinkedHashSet<String>();
	}
}
