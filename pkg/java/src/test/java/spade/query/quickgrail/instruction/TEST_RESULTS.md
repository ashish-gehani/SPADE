# Container Methods: Test Results

A snapshot of the tests for `getContainerInit` and `getContainerBoundary`,
for review. This is not a CI report; regenerate it by hand after changing the
methods, the harness, or the fixtures.

The test design and the behavior being tested are described in
`pkg/java/src/main/java/spade/query/quickgrail/CLARITY_METHODS.md` (§9).

## Environment

| | |
|---|---|
| Date | 2026-09-16 |
| Branch | `clarity-of-container`, based on upstream `prov-query` at `1ff51190` |
| Tested revision | the commit that last changed this file, which also adds the two Q3 scenarios |
| OS | Windows 11 Pro 10.0.26200 |
| JDK | OpenJDK 21.0.2 from https://jdk.java.net/archive/ |
| Build | Apache Maven 3.9.16, `maven-surefire-plugin` 3.5.5 |
| Test framework | JUnit Jupiter 6.0.3 |

Command, run in `pkg/java`:

```
mvn test -Dtest='InMemoryQueryHarnessTest,GetContainer*Test,LinuxConstantsTest'
```

`pkg/java/cfg` is a git symbolic link to the root `cfg` directory, and
Windows checks it out as a plain file. For this run it was temporarily
replaced with a directory junction. The resolver tests read
`cfg/spade.reporter.Audit.config` and the Linux constants file through it.

## Summary

```
LinuxConstantsTest ................................................  2/2   PASS
InMemoryQueryHarnessTest ..........................................  5/5   PASS
GetContainerInitTest ..............................................  6/6   PASS
GetContainerBoundaryTest ..........................................  9/9   PASS
GetContainerInitIntegrationTest$PostgreSQLAndQuickstepAdjacency ... 20/20  PASS
GetContainerInitIntegrationTest$Neo4jAdjacency .................... 20/20  PASS
GetContainerBoundaryIntegrationTest$PostgreSQLAndQuickstepAdjacency 12/12  PASS
GetContainerBoundaryIntegrationTest$Neo4jAdjacency ................ 12/12  PASS
--------------------------------------------------------------------------------
Total                                                                86/86  PASS
```

Surefire reported `Tests run: 86, Failures: 0, Errors: 0, Skipped: 0` and
`BUILD SUCCESS`. Each class took under 0.3 s.

## Per-class results

### `LinuxConstantsTest`
- PASS `procPidInitIno_isReadFromShippedConstantsFile`
- PASS `procPidInitIno_isOptionalWhenLoadingButRequiredWhenRequested`

### `InMemoryQueryHarnessTest`
- PASS `adjacency_postgresSemantics_addsEverySourceVertex`
- PASS `adjacency_neo4jSemantics_addsSourcesOnlyThroughMatchingEdges`
- PASS `adjacency_neo4jSemantics_requiresBothEndpointsInSubject`
- PASS `subtract_removesPerComponent_andRejectsBaseSubtrahend`
- PASS `comparisons_areStringOrdered_andLikeUsesSqlWildcards`

### `GetContainerInitTest`
- PASS `constructor_storesAllFields`
- PASS `getLabel_returnsClassName`
- PASS `getFieldStringItems_listsGraphsAndHostPidNamespace`
- PASS `exec_rejectsMissingHostPidNamespace`
- PASS `resolver_readsHostPidNamespaceFromTheAuditConstantsFile`: resolving `$base.getContainerInit()` gives `4026531836`
- PASS `resolver_rejectsArguments`

### `GetContainerBoundaryTest`
- PASS `constructors_storeTheirForm`
- PASS `constructor_rejectsIdsThatAreNotContainers`: host ID, `-1`, empty ID, container number 0, null seed graph
- PASS `getLabel_returnsClassName`
- PASS `getFieldStringItems_listsTheFieldsOfEachForm`
- PASS `resolver_noArguments_selectsEveryContainer`
- PASS `resolver_stringArgument_selectsById`
- PASS `resolver_stringAndInteger_selectsANumberedContainer`
- PASS `resolver_graphArgument_selectsBySeedProcesses`
- PASS `resolver_rejectsInvalidArguments`: three arguments, number 0, integer ID, string number, host ID, `-1`

### `GetContainerInitIntegrationTest` (passed under both adjacency semantics)
- PASS `dockerRun_returnsTheUnshareStepAndTheInitUpToTheApplication`
- PASS `dockerExec_entersAContainerWithoutStartingOne`
- PASS `dockerExecAlone_returnsNothing`
- PASS `cloneWithNewPidNamespace_startsAContainer`
- PASS `cloneWithNewPidNamespaceButNoSigchld_isRecordedAsCloneAndStillFound`
- PASS `unshareThenExecve_theNewProgramsFirstChildIsTheInit`
- PASS `creatorJoiningAnotherNamespaceFirst_includesThoseSteps`
- PASS `nestedContainer_isReportedWithItsCreatorInsideTheOuterContainer`
- PASS `laterChildrenOfTheSameUnshare_enterTheNamespace`
- PASS `processReturningToEarlierLabels_startsTwoContainersAndThenJoinsTheFirst`
- PASS `reusedNamespaceIdAndPids_startupsStaySeparate`
- PASS `containerStartedBeforeTracing_isNotReported`
- PASS `unshareBeforeTracing_isNotSeen`
- PASS `unshareByProcessWithUnobservedNamespaces_isTakenAsJoining`
- PASS `mountNamespaceOnHost_isNotAContainer`
- PASS `emptyGraph_returnsNothing`
- PASS `initThatNeverCallsExecve_failsLoudly`
- PASS `reusedNamespaceIdAndPids_doNotHideAnInitThatNeverCallsExecve`
- PASS `bubblewrapSandbox_failsBecauseItsReaperInitNeverCallsExecve`: decision Q3 (a)
- PASS `nspawnAsPid2_failsBecauseItsStubInitNeverCallsExecve`: decision Q3 (a)

### `GetContainerBoundaryIntegrationTest` (passed under both adjacency semantics)
- PASS `everyContainer_isItsProcessesWhatTheyTouchAndTheEdgesBetween`
- PASS `pidNamespaceId_selectsThatContainerOnly`
- PASS `containerRunningBeforeTracing_isOneContainer`
- PASS `unknownPidNamespaceId_returnsNothing_butANumberedContainerMustExist`
- PASS `emptyGraph_returnsNothing`
- PASS `reusedPidNamespaceId_failsListingTheContainers`
- PASS `reusedPidNamespaceId_numberSelectsOneContainer`
- PASS `reusedPidNamespaceId_allContainersStillReturnsEveryProcess`
- PASS `container_includesContainersStartedInsideIt`
- PASS `nestedContainer_belongsOnlyToTheOuterContainerThatStartedIt`
- PASS `seedProcesses_selectTheContainersTheyBelongTo`
- PASS `seedProcessesOutsideContainers_selectNothing`

## Planted bugs

To check that the tests detect wrong behavior, each bug below was planted
into `ContainerAnalysis` or `GetContainerInit` by itself. The affected
method's integration tests were rerun (M1–M4 for `getContainerInit`, M5–M10
for `getContainerBoundary`), and the code was then restored and confirmed
identical. This was done before the two Q3 scenarios were added. Every bug
made the listed tests fail under both adjacency semantics.

Surefire merges same-named failures from the two `@Nested` classes into one
entry with "Run 1" and "Run 2", so each failing test there covers both.

| # | Planted bug | Failing tests |
|---|---|---|
| M1 | Every child after an unshare is a start | `laterChildrenOfTheSameUnshare_enterTheNamespace`, `processReturningToEarlierLabels_startsTwoContainersAndThenJoinsTheFirst` |
| M2 | Init versions continue past the first execve | `dockerRun_returnsTheUnshareStepAndTheInitUpToTheApplication` |
| M3 | A version is stopped by its own kind of namespace change | `processReturningToEarlierLabels_startsTwoContainersAndThenJoinsTheFirst` |
| M4 | Children after an unshare grouped by namespace ID only, ignoring when the namespace was created | `reusedNamespaceIdAndPids_startupsStaySeparate`, `reusedNamespaceIdAndPids_doNotHideAnInitThatNeverCallsExecve` |
| M5 | Containers have no time windows | `pidNamespaceId_selectsThatContainerOnly`, `reusedPidNamespaceId_numberSelectsOneContainer`, `container_includesContainersStartedInsideIt`, `nestedContainer_belongsOnlyToTheOuterContainerThatStartedIt`, `seedProcesses_selectTheContainersTheyBelongTo` |
| M6 | Containers started inside a container are not included | `container_includesContainersStartedInsideIt`, `nestedContainer_belongsOnlyToTheOuterContainerThatStartedIt`, `seedProcesses_selectTheContainersTheyBelongTo` |
| M7 | A nested container is attached to every container of its creator's ID | `nestedContainer_belongsOnlyToTheOuterContainerThatStartedIt`, `seedProcesses_selectTheContainersTheyBelongTo` |
| M8 | A seed process selects the earliest container of its ID | `seedProcesses_selectTheContainersTheyBelongTo` |
| M9 | No container "running when tracing started" | `containerRunningBeforeTracing_isOneContainer`, `reusedPidNamespaceId_failsListingTheContainers`, `reusedPidNamespaceId_numberSelectsOneContainer` |
| M10 | `seen time` ignored when placing processes in containers | `reusedPidNamespaceId_numberSelectsOneContainer`, `seedProcesses_selectTheContainersTheyBelongTo` |

## Whole test suite

Running every test in `pkg/java` (`-Dtest='**/*Test*'`) gave 353 tests, 5 of
them failing. None of the failures is in code this branch touches, and all
5 are caused by Windows:

| Test | Cause |
|---|---|
| `spade.utility.setting.convert.FilesTest`: `rejectsNonReadableFile`, `rejectsNonExecutableFile`, `rejectsNonWritableDirectory`, `rejectsCreatableDirectoryWhenExistingDirectoryNotWritable` | `File.setReadable(false)`, `setExecutable(false)` and `setWritable(false)` have no effect on Windows |
| `spade.utility.setting.SettingTest.parsesTextFileReference` | The test resource is checked out with CRLF line endings; the test expects `\n` |

Upstream last changed those tests in `1ff51190`, the base of this branch.

## Not covered

- Real storage backends (PostgreSQL, Neo4j, Quickstep). The harness follows
  their semantics, but the methods have not run against a database.
- Real Audit reporter traces. The fixtures follow the reporter's source code;
  validating against real `docker run` and `docker exec` traces is pending.
