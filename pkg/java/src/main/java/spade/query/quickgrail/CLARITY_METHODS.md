# CLARION Clarity Methods — Design Notes

`getContainerBoundary` and `getContainerInit` are QuickGrail graph methods
that implement the two "essential container semantic patterns" from CLARION
(Chen et al., *Sound and Clear Provenance Tracking for Microservice
Deployments*, USENIX Security 2021), §4.2. They turn capabilities that
CLARION previously expressed only as data-model labels or ingestion-time
filters into first-class, on-demand query operations against any storage
that already exposes the standard QuickGrail executor primitives.

This document captures the design decisions behind the implementation. It
does not duplicate the public-facing reference (see `README.md` for
signatures and examples) or the inline Javadoc (see
`instruction/GetContainerBoundary.java` and `instruction/GetContainerInit.java`).

## 1. Paper anchoring

| Paper section | What it claims | What the method does |
|---|---|---|
| §4.2.1 *Boundary of Containers* | A container at runtime is the set of processes sharing one PID namespace; an artifact relates to that container iff a process inside accessed it; CLARION leverages the per-vertex `pid namespace` label to "certify the boundary of each container." | Selects vertices by `pid namespace`, joins their adjacent artifacts, returns the spanned subgraph. |
| §4.2.2 *Initialization of Containers* | The init pattern starts with `unshare`/`clone` carrying a new-namespace flag and ends with the `execve` that launches the in-container app (whose process has `ns pid == 1`). | Finds the boundary-crossing edges, anchors a bounded path search to `ns pid == 1` vertices, and surfaces the spanning subgraph. |
| §5.2 *Cross-container Evaluation* | Figures 14–16 show per-container init subgraphs; the prose states CLARION "successfully summarizes the container boundary for all three container engines"; Tables 7–9 measure aggregate vertex/edge/component counts across multi-container (`MC-4`) traces. | Boundary supports both forms — one container by id (per-container figures) and union over all (`MC-4`-style aggregate). |

The paper does not specify a query API for §4.2.1 (it was a labeling
property) or §4.2.2 (it was a SPADE ingestion filter). The shapes here are
derived from the evaluation usage in §5 but the implementation is purely
query-side; reporter-side behavior is unchanged.

## 2. Architecture choice: composite over primitives

A new QuickGrail graph method generally needs:

1. A method name → handler dispatch in `QuickGrailQueryResolver.resolveGraphMethod()`.
2. A private `resolveXxx(...)` helper that validates arguments and emits an `Instruction`.
3. An `Instruction` subclass in `instruction/`.
4. Execution logic — either a new abstract method on `QueryInstructionExecutor` (then concrete impls in every backend: PostgreSQL, Neo4j, Quickstep, …) **or** a composite `exec()` that calls *existing* executor primitives.

We chose the composite route for both methods. The abstract
`QueryInstructionExecutor` already uses this pattern for `getPath` and
`getPathLengths` (see `QueryInstructionExecutor.java` lines 437 and 514).
Benefits:

- One implementation works for every storage backend that already supports
  the underlying primitives.
- No `getMatch`-style "experimental" caveats per backend.
- Reverts and edits localized to the `instruction/` package and the
  resolver.

The cost: we lean on `exportEdges` / `exportVertices` in `GetContainerInit`
to perform a cross-endpoint filter that no single primitive expresses (see
§4 below). For typical microservice traces this is fine; for very large
graphs the materialization may become a bottleneck, in which case the
escape hatch is to add a storage-side primitive later without changing the
public method.

## 3. `getContainerBoundary` design

### Signatures

```
$r = $base.getContainerBoundary()                  # union across all containers
$r = $base.getContainerBoundary('<pid_ns_id>')     # one specific container
```

Argument is a string only. The `pid namespace` annotation is stored as a
decimal-formatted string (e.g. `'4026532270'`), so accepting only a string
literal matches QuickGrail convention for annotation-value arguments.

### Algorithm (see `GetContainerBoundary.exec`)

```
procs    = getWhereAnnotationsExist(subjectGraph, ["pid namespace"])           # no-arg form
         | getVertex(subjectGraph, "pid namespace" == pidNamespaceId)          # one-container form
adjacent = getAdjacentVertex(subjectGraph, procs, Direction.kBoth)
skeleton = procs ∪ adjacent
target   = getSubgraph(subjectGraph, skeleton)
```

All four steps are existing executor primitives. The result includes:
process vertices in the chosen PID namespace(s), every artifact (file,
socket, IPC object, memory) those processes touched in either direction,
and the edges between them.

### Why support both forms

§5.2 of the paper exercises both shapes — per-container forensic
walkthroughs (single PID namespace) and `MC-4` aggregate measurements
(union over many). Restricting to one form would have reduced parity with
the evaluation.

## 4. `getContainerInit` design

### Signature

```
$r = $base.getContainerInit()      # depth implicitly bounded by env var maxDepth
```

No arguments. The bound comes from the `maxDepth` environment variable
(same convention as `getLineage` and `getShortestPath` when called without
an explicit depth).

### Algorithm (see `GetContainerInit.exec`)

```
ends                 = getVertex(subjectGraph, "ns pid" == "1")
if |ends| == 0:        return empty target               # no containers — not an error
unshareEdges         = getEdge(subjectGraph, "operation" == "unshare")
allCloneEdges        = getEdge(subjectGraph, "operation" == "clone")
pid1Hashes           = exportVertices(ends).keySet()
cloneCrossingEdges   = { e ∈ exportEdges(allCloneEdges) : e.childHash ∈ pid1Hashes }
boundaryEdges        = unshareEdges ∪ insertLiteralEdge(cloneCrossingEdges)
if |boundaryEdges| == 0: throw RuntimeException("found PID-1 vertices but no boundary edges …")
startVertices        = getEdgeEndpoint(boundaryEdges, kDestination)
target               = getSimplePath(subjectGraph, ends, startVertices, maxDepth)
startsInResult       = startVertices ∩ target
if |startsInResult|  < |startVertices|:
    throw RuntimeException("N starts detected, M did not reach 'ns pid'=='1' within maxDepth …")
```

### Detection criterion — why "unshare OR clone-crossing-PID-namespace"

Three options were considered:

| Option | Definition | Trade-off |
|---|---|---|
| 1 | `operation == "unshare"` only | Simplest. Misses engines that rely solely on `clone(CLONE_NEWPID)` without `unshare`. |
| 2 | `unshare` OR (`clone` AND child PID namespace ≠ parent PID namespace) | Covers Docker (runC path), rkt, LXC. Requires a cross-endpoint check no single primitive expresses. |
| 3 | `ns pid == '1'` | Engine-agnostic but coarser; matches the in-container init process rather than the boundary-crossing event itself. |

We took **Option 2**. The cross-endpoint check is implemented by exporting
the candidate clone edges into Java, filtering those whose `childHash`
(source endpoint — the child in OPM's `WasTriggeredBy`) appears in the set
of `ns pid == '1'` vertices, and re-importing via `insertLiteralEdge`.
This treats a PID-1 child as the operational signal that a clone crossed
into a new PID namespace, which matches the kernel semantics: the first
process in a fresh PID namespace always has virtual PID 1.

### Anchoring and traversal direction

The result is computed as a `getSimplePath` from `ends` (PID-1 vertices in
the container) **to** `startVertices` (destinations of boundary edges,
i.e. the host-side callers of `unshare`/`clone`). In SPADE's OPM model
edges go from child to parent (`WasTriggeredBy`), so this path follows
edge direction — equivalent to the paper's "backward traversal from the
starting point" once you align the language with the data model
(`ends` are the in-container vertices the user perceives as later events;
edges point from them upward to their callers).

### Completeness check + throw-on-incomplete

The QuickGrail primitives `getSimplePath` / `getLineage` / `getShortestPath`
all take an explicit depth, so we have to bound the search even though the
paper's pattern (`unshare`/`clone-NEWPID` ⇒ `execve`) is conceptually
self-terminating. To compensate for that gap we verify, after the path
search, that **every** detected boundary-crossing start landed in the
result subgraph. If some did not, the trace either truncates before the
init chain completes or the user-set `maxDepth` is too small to span it.

We surface this as a `RuntimeException`. The reasoning:

- The result of a partial-but-silent `getContainerInit` is misleading —
  the user sees a subgraph but cannot tell that some containers were
  missed.
- `QuickGrailExecutor.execute` already wraps any `Exception` from an
  instruction into `query.queryFailed(...)` (see lines 108–119 of
  `QuickGrailExecutor.java`), so a `RuntimeException` produces a clear
  client-side failure without crashing the SPADE CLI. The next query the
  user types still runs.
- The exception text names the gap and the remediation
  (`env set maxDepth <N>`). The trade-off — losing access to a potentially
  useful partial result — is acceptable because the partial result has no
  reliable interpretation.

We deliberately do **not** throw when `|ends| == 0` (no PID-1 vertices in
the input). That case is a legitimate "no containers here" answer, not a
failure mode; the method returns an empty target.

## 5. Annotations used

These methods rely on annotation keys produced by the upstream Linux
Audit reporter (when the namespace-tracking kernel module is loaded).
All keys are referenced via `spade.reporter.audit.OPMConstants` rather
than hard-coded literals.

| Key constant | String value | Role |
|---|---|---|
| `PROCESS_PID_NAMESPACE` | `"pid namespace"` | Boundary selector for §4.2.1 |
| `PROCESS_NS_PID` | `"ns pid"` | In-container PID 1 anchor for §4.2.2 |
| `EDGE_OPERATION` | `"operation"` | Filter for `unshare` and `clone` edges |
| `OPERATION_UNSHARE` | `"unshare"` | Boundary-crossing syscall |
| `OPERATION_CLONE` | `"clone"` | Candidate boundary-crossing syscall |

If the underlying audit/KM pipeline is not producing these annotations
(for example because the kernel module is disabled), both methods will
return empty graphs cleanly — they do not require the namespace metadata
to exist, they just have nothing to extract without it.

## 6. Limitations and future work

- **Aggregation across containers**: `getContainerBoundary` with no
  argument returns one merged graph; it does not partition the result by
  PID namespace. Per-container partitioning would need either a
  multi-result instruction (not currently supported by QuickGrail) or a
  follow-up `stat` query.
- **`exportEdges` materialization in init**: scales with the count of
  `clone` edges in the input, not with the size of init chains. Fine for
  microservice traces; large monolithic traces could see noticeable
  memory pressure during `getContainerInit`. A storage-side
  "edges-with-source-in-graph-X" primitive would eliminate this.
- **Engine-specific init shapes**: the rkt three-stage pattern, Docker's
  containerd-shim chain, and LXC's `lxd`/`lxc-start` flow all manifest
  with somewhat different vertex counts and edge labels. The algorithm
  remains correct (it does not enumerate engines) but the resulting
  subgraph sizes vary; that is expected behaviour, not a bug.
- **Other namespace flavors**: the boundary definition is PID-namespace
  centric, mirroring the paper. Mount-, network-, and IPC-namespace
  boundaries are reachable today only via the existing `getVertex` /
  `getEdge` primitives against the corresponding annotation keys
  (`mount namespace`, `net namespace`, `ipc namespace`).

## 7. Test design

Tests live under `test/spade/query/quickgrail/instruction/`. There are
two layers, each commit-separated per the project's history convention.

### 7.1 Layering

- **Unit tests** (`GetContainerBoundaryTest`, `GetContainerInitTest`)
  cover the small, contract-only surface that production callers
  depend on without ever touching a storage: constructor field
  assignment, the `getLabel()` string used by the execution-plan
  printer, and the inline (name, value) pairs `getFieldStringItems`
  feeds back to it. These tests need no executor, no storage, and no
  fixture data — they are essentially type-level guards against
  refactor regressions on the {target, subject, parameters} contract
  between resolver and instruction.
- **Integration tests** (`GetContainerBoundaryIntegrationTest`,
  `GetContainerInitIntegrationTest`) exercise the composite
  `exec()` algorithm itself: they synthesize a realistic provenance
  fixture, drive each instruction through a real
  `QueryInstructionExecutor`, and check the resulting graph's vertex
  and edge sets against the expected boundary or init subgraph.

Splitting the layers keeps unit tests trivially fast (cents of
milliseconds, no I/O) and keeps the integration tests' set-up costs
isolated to the cases that genuinely need a fixture.

### 7.2 In-memory harness

`InMemoryQueryHarness` provides the executor and environment the
integration tests run against. Two reasons for hand-writing it rather
than running the tests against a real storage backend (H2, PostgreSQL,
Neo4j):

1. **Reproducibility.** No external service to install, no
   database/lock state to clean up between runs. The fixture data is
   built in Java, lives only as long as the test, and goes away when
   the JVM exits.
2. **Sharp failure surfaces.** Every executor primitive that neither
   `GetContainerBoundary` nor `GetContainerInit` calls is implemented
   as `throw new UnsupportedOperationException(...)`. If a future
   refactor of the composite `exec()` ever reaches into an unintended
   primitive, the integration tests fail loudly with the offending
   method name rather than appearing to pass for the wrong reason.

The implemented primitives are: `createEmptyGraph`, `unionGraph`,
`intersectGraph`, `getWhereAnnotationsExist`, `getVertex`, `getEdge`,
`getEdgeEndpoint`, `getAdjacentVertex`, `getSubgraph`, `getSimplePath`
(BFS with per-source visited sets), `getGraphCount`,
`insertLiteralEdge`, `exportVertices`, `exportEdges`, plus
`distinctifyGraph` (the resolver appends one of these after every
assignment). The implementations mirror the production storage
executors' semantics — in particular, `getSubgraph` enforces the
spanning-subgraph invariant and `getAdjacentVertex` honours direction.

### 7.3 Per-method coverage

For `getContainerBoundary` the fixture is one mixed host plus two
container-labeled subgraphs sharing a single `containerd` parent. The
scenarios are:

| Scenario | Verifies |
|---|---|
| Single-container, known PID namespace | Processes and adjacent artifacts of the chosen container land in the result; the other container, the host-only artifact, and unrelated host reads are excluded. The host parent that cloned the in-container init is correctly pulled in by adjacency. |
| Single-container, unknown PID namespace | Returns an empty graph with neither vertices nor edges and no exception. |
| Two disjoint containers, two calls | The two result graphs share only the legitimately-shared host daemon, with all in-container vertices remaining in their own container's result. |
| All-containers (no-arg) form | Both containers' boundaries appear in the union; the host-only artifact is excluded, which is what distinguishes "union of boundaries" from "the whole graph". |
| Spanning-subgraph invariant | Every edge in the result is incident to two vertices that are also in the result — a property the composite must preserve regardless of fixture shape. |
| Annotation faithfulness | Vertex annotations on the result come back identical to what was seeded, catching any export regression. |

For `getContainerInit` the fixture varies per scenario so that the
chain topology is visible adjacent to the assertions that depend on
it. Scenarios:

| Scenario | Verifies |
|---|---|
| Docker-style clone+execve chain | From in-container hello back to the boundary-crossing runC[Parent], inclusive; the engine chain above the boundary (runC, containerd-shim, containerd) is excluded per §4.2.2's prose definition. |
| Unshare variant | The boundary edge is recognised even when no clone is present, and both endpoints plus the unshare edge itself land in the result. |
| No PID-1 vertices anywhere | The method returns an empty graph rather than failing — a graph without containers is a legitimate "nothing to do" answer. |
| Two independent containers, one clone one unshare | Both boundary-crossing edges are detected and both chains land in the result. |
| PID-1 present, no boundary edges | The method throws a `RuntimeException` whose message names the missing boundary edges, so the user can tell that the trace was truncated rather than that the algorithm gave up. |
| `maxDepth = 0` | The completeness check fires and the thrown message mentions `maxDepth`, surfacing the remediation knob (`env set maxDepth <N>`). |

### 7.4 Out of scope for these tests

- **Real storage backends.** The composite is written in terms of
  abstract primitives, so behaviour against a particular SQL or
  graph-database executor is a separate concern (one that the
  per-storage executor's own test surface should cover).
- **Concurrency.** Both methods are single-threaded query-side
  operations; concurrency lives at the storage executor.
- **Performance.** Fixture sizes are tiny by design so that the test
  remains a behaviour check rather than a benchmark. Scaling
  characteristics — particularly the `exportEdges` materialization in
  `getContainerInit` discussed in §6 — would need a dedicated
  benchmark on realistic traces.
- **End-to-end via the parser.** The resolver case-statement that
  dispatches `getContainerBoundary` / `getContainerInit` is exercised
  indirectly by any future QuickGrail-text integration test against a
  storage; the present integration tests skip the parser to keep each
  test's intent obvious.
