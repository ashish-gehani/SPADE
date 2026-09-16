# CLARION Clarity Methods — Test Results

Snapshot of the four test classes that exercise `getContainerBoundary`
and `getContainerInit`. This file is a review artifact, not a CI
report: regenerate it (manually) after any change that touches the
methods, the harness, or the test fixtures.

Cross-references (paths relative to `pkg/java/src/`):
- Algorithm and detection-criterion rationale: `main/java/spade/query/quickgrail/CLARITY_METHODS.md`.
- Public-facing reference: the wiki's [QuickGrail Reference](https://github.com/ashish-gehani/SPADE/wiki/QuickGrail-Reference) page.
- Sources under test:
  - `main/java/spade/query/quickgrail/instruction/GetContainerBoundary.java`
  - `main/java/spade/query/quickgrail/instruction/GetContainerInit.java`
  - `main/java/spade/query/quickgrail/core/QuickGrailQueryResolver.java` (dispatch and arg parsing)

## Environment

| | |
|---|---|
| Date | 2026-09-16 |
| Branch | `clarity-of-container`, rebased onto upstream `master` at `1ff51190` |
| Tested revision | the commit that last changed this file |
| OS | Windows 11 Pro 10.0.26200 |
| JDK | OpenJDK 21.0.2 (2024-01-16) from https://jdk.java.net/archive/ |
| Build | Apache Maven 3.9.16, `maven-surefire-plugin` 3.5.5 |
| Test framework | JUnit Jupiter 6.0.3 (`junit-jupiter-api`, per `pkg/java/pom.xml`) |

## Summary

```
GetContainerBoundaryTest .............. 5/5 PASS  (0.008 s)
GetContainerInitTest .................. 5/5 PASS  (0.009 s)
GetContainerBoundaryIntegrationTest ... 6/6 PASS  (0.361 s)
GetContainerInitIntegrationTest ....... 6/6 PASS  (0.025 s)
-----------------------------------------------------------
Total                                  22/22 PASS
```

Times are Surefire's per-class elapsed times. The first integration class
to run also pays for one-time JVM class loading (including `Settings`), so
its time is inflated relative to the others; expect drift on other hardware.

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

These tests do not require a real database. They do require JDK 21,
Maven, and the SPADE keystores.

```bash
# 1. JDK 21 and Maven on PATH (JAVA_HOME pointing at the JDK).

# 2. Keystores. The top-level `make` generates them before building
#    pkg/java; if you are running Maven directly, generate them once from
#    the repository root. Settings.<clinit> aborts the JVM if they are
#    missing, and Settings is loaded transitively from
#    QueryInstructionExecutor's constructor (via DiscrepancyDetector).
bin/keys/generatekeys.sh

# 3. Run the four classes from the Java package. The first run needs
#    network access to resolve Maven Central dependencies and the JUnit
#    platform engine/launcher; afterwards `-o` (offline) works.
cd pkg/java
mvn test -Dtest='GetContainer*Test'
```

A successful run reports `Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`
and `BUILD SUCCESS`.

## Notes worth surfacing

- **Keystore dependency is incidental, not load-bearing.** The methods
  themselves do not touch SSL. The dependency comes from
  `QueryInstructionExecutor`'s constructor instantiating a
  `DiscrepancyDetector`, which triggers `Settings.<clinit>` and a
  fatal `System.exit(-1)` when the keystores are missing. This is a
  global SPADE setup quirk that any in-process test against the
  executor inherits.
- **`pkg/java/cfg` is a symlink.** Surefire runs tests with
  `pkg/java` as the working directory, and `Settings` resolves
  `cfg/...` relative to it, so the symlink to the root `cfg/` must
  resolve. On Linux and macOS it does. On a Windows checkout with
  `core.symlinks=false`, Git writes a small placeholder file instead,
  and the integration tests abort at `Settings.<clinit>`. The run
  above temporarily replaced the placeholder with a directory junction
  to the root `cfg/` and restored the tracked file afterwards.
- **In-memory harness, not a mocking framework.** The harness
  (`test/java/spade/query/quickgrail/instruction/InMemoryQueryHarness.java`)
  is a real subclass of `QueryInstructionExecutor` and
  `AbstractQueryEnvironment` with the primitives written out by hand.
  Any primitive the methods do not call throws
  `UnsupportedOperationException`, which is how the harness keeps the
  blast radius of "we accidentally added a primitive call" visible.
