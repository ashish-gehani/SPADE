# CLARION Clarity Methods — Test Results

Snapshot of the four test classes that exercise `getContainerBoundary`
and `getContainerInit`. This file is a review artifact, not a CI
report: regenerate it (manually) after any change that touches the
methods, the harness, or the test fixtures.

Cross-references:
- Algorithm and detection-criterion rationale: `src/spade/query/quickgrail/CLARITY_METHODS.md`.
- Public-facing reference: `src/spade/query/quickgrail/README.md`.
- Sources under test:
  - `src/spade/query/quickgrail/instruction/GetContainerBoundary.java`
  - `src/spade/query/quickgrail/instruction/GetContainerInit.java`
  - `src/spade/query/quickgrail/core/QuickGrailQueryResolver.java` (dispatch and arg parsing)

## Environment

| | |
|---|---|
| Date | 2026-06-08 |
| Branch | `clarity-of-container` |
| HEAD commit | `391667e3` (`Add integration tests for getContainerInit`) |
| OS | Windows 11 Pro 10.0.26200, MSYS / Git-Bash shell |
| JDK | OpenJDK 11.0.2 (2019-01-15) from https://jdk.java.net/archive/ |
| JUnit | 4.12 (`lib/junit-4.12.jar`) + Hamcrest 1.3 (`lib/hamcrest-core-1.3.jar`) |

## Summary

```
GetContainerBoundaryTest .............. 5/5 PASS  (≈6 ms)
GetContainerInitTest .................. 5/5 PASS  (≈2 ms)
GetContainerBoundaryIntegrationTest ... 6/6 PASS  (≈197 ms)
GetContainerInitIntegrationTest ....... 6/6 PASS  (≈138 ms)
-----------------------------------------------------------
Total                                  22/22 PASS
```

JUnit run-times above are wall-clock from `org.junit.runner.JUnitCore`
on the JDK above; expect drift on different hardware.

## Per-class breakdown

### `GetContainerBoundaryTest` — unit-level contract checks
- `constructor_storesAllFieldsForSingleContainerForm` — fields round-trip from constructor arguments to public-final fields.
- `constructor_allowsNullPidNamespaceForAllContainersForm` — a null `pidNamespaceId` is accepted as the no-arg form's sentinel.
- `getLabel_returnsClassName` — the execution-plan printer sees `GetContainerBoundary`.
- `getFieldStringItems_listsBothGraphsAndExplicitNamespace` — the inline name/value pairs the printer consumes are correct and 1:1.
- `getFieldStringItems_serializesAllContainersFormWithSentinel` — null id renders as the literal `<all>` rather than `null`.

### `GetContainerInitTest` — unit-level contract checks
- `constructor_storesAllFields` — fields round-trip, including the int `maxDepth`.
- `constructor_acceptsZeroDepthEvenThoughResolverRejectsIt` — the Instruction itself does not enforce the maxDepth-must-be-set policy; that policy is in the resolver. This pins the layering.
- `getLabel_returnsClassName` — `GetContainerInit`.
- `getFieldStringItems_listsBothGraphsAndMaxDepth` — name/value pairs correct.
- `getFieldStringItems_serializesDepthAsDecimalNotHexOrOctal` — guard against accidental `Integer.toHexString` regressions.

### `GetContainerBoundaryIntegrationTest` — end-to-end against `InMemoryQueryHarness`
Fixture: host (containerd + a host-only artifact) plus two container-labeled subgraphs (`ns_A`, `ns_B`) that both descend from the host daemon and each read their own copy of `/etc/passwd`.
- `singleContainer_keepsOnlyChosenContainersProcessesAndAdjacentArtifacts` — container A's processes, the artifact A read, and the host daemon (adjacent via clone) all appear; B's processes/artifact and the host-only artifact do not.
- `singleContainer_unknownPidNamespaceProducesEmptyGraph` — unknown namespace id yields empty result, no exception.
- `singleContainer_disjointContainersProduceDisjointResults` — A's and B's results share only the host daemon.
- `allContainers_unionsEveryLabeledContainersBoundary` — no-arg form unions both containers' boundaries while still excluding host-only data.
- `resultEdges_alwaysHaveBothEndpointsInTheResultVertexSet` — spanning-subgraph invariant holds.
- `singleContainer_exportedAnnotationsAreFaithful` — `ns_A`, `ns pid` = `1`, `type` = `Process` survive the extraction round-trip.

### `GetContainerInitIntegrationTest` — end-to-end against `InMemoryQueryHarness`
Per-test fixtures so the chain topology is visible right next to the assertions.
- `dockerLikeInitChain_isExtractedEndToEnd` — containerd → containerd-shim → runC → runC[Parent] → (clone-NEWPID) → runC[Child] → runC[INIT] → hello. Result spans from `hello` back to `runC[Parent]`, inclusive; everything above the boundary is excluded.
- `unshareCase_yieldsThePostUnshareToCallerEdge` — single `unshare` edge between a host caller and a PID-1 post-unshare snapshot is captured exactly.
- `noPid1Vertices_returnsEmptyGraphWithoutError` — a graph with only host processes returns empty cleanly.
- `twoIndependentContainers_bothInitChainsAreExtracted` — a clone-based container and an unshare-based container in the same input each produce their own boundary edge + endpoints in the result.
- `pid1ExistsButNoBoundaryEdges_throwsTruncationException` — a PID-1 vertex with no `unshare`/`clone` edges throws; the message contains the literal "no 'unshare' or PID-namespace-crossing 'clone'".
- `depthZero_throwsCompletenessException` — `maxDepth = 0` throws; the message contains "maxDepth" so the user knows the env-var knob.

## Reproduction

These tests do not require a real database. They do require a usable
JDK and the SPADE keystores. The full sequence from a freshly-cloned
working tree:

```bash
# 1. Make sure a JDK in the supported range (11–14) is on PATH.
export PATH=/path/to/jdk-11.0.2/bin:$PATH

# 2. Generate the SPADE keystores once. Settings.<clinit> aborts the JVM
#    if these are missing, and Settings is loaded transitively from
#    QueryInstructionExecutor's constructor (via DiscrepancyDetector).
bash bin/keys/generatekeys.sh

# 3. Compile main sources and the test sources into build/.
mkdir -p build tmp
CP=$(bash bin/classpath.sh | tr -d '\r' | sed 's|/d/SPADE/lib/neo4j-community-4.1.1/lib/\*:||')
#   On non-MSYS Linux/macOS the `tr -d '\r'` is harmless. The neo4j
#   wildcard is filtered only when `make download-neo4j` has not been
#   run; if it has, leave it in.
CP_FINAL="$CP"   # On Windows/MSYS, additionally: $(cygpath -wp "$CP")

find src/spade/query -name '*.java' > tmp/query.classes
javac -Xlint:none -proc:none -cp "$CP_FINAL" -sourcepath src -d build @tmp/query.classes

find test/spade/query/quickgrail -name '*.java' > tmp/tests.classes
javac -Xlint:none -proc:none -cp "$CP_FINAL" -sourcepath src -d build @tmp/tests.classes

# 4. Run each test class.
TEST_CP="build${CP_FINAL:+:${CP_FINAL}}"   # Use ; instead of : on Windows.
for c in \
  GetContainerBoundaryTest \
  GetContainerInitTest \
  GetContainerBoundaryIntegrationTest \
  GetContainerInitIntegrationTest; do
  java -cp "$TEST_CP" org.junit.runner.JUnitCore "spade.query.quickgrail.instruction.$c"
done
```

A successful run prints `OK (<N> tests)` for each class and exits 0.

## Notes worth surfacing

- **Keystore dependency is incidental, not load-bearing.** The methods
  themselves do not touch SSL. The dependency comes from
  `QueryInstructionExecutor`'s constructor instantiating a
  `DiscrepancyDetector`, which triggers `Settings.<clinit>` and a
  fatal `System.exit(-1)` when the keystores are missing. This is a
  global SPADE setup quirk that any in-process test against the
  executor inherits.
- **CRLF in `cfg/java.classpath`.** On a fresh Windows checkout the
  file may be saved with CRLF line endings, which leaks `\r`
  characters into `bin/classpath.sh`'s output and makes the resulting
  classpath unusable by `javac`. The reproduction recipe above
  filters with `tr -d '\r'`; a separate fix on the script side would
  remove the need.
- **In-memory harness, not a mocking framework.** The harness
  (`test/spade/query/quickgrail/instruction/InMemoryQueryHarness.java`)
  is a real subclass of `QueryInstructionExecutor` and
  `AbstractQueryEnvironment` with the primitives written out by hand.
  Any primitive the methods do not call throws
  `UnsupportedOperationException`, which is how the harness keeps the
  blast radius of "we accidentally added a primitive call" visible.
