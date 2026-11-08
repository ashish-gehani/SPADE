# ContainerLabels: Test Results

A snapshot of the tests for the `ContainerLabels` transformer, for review.
The test design is in
`pkg/java/src/main/java/spade/query/quickgrail/CLARITY_METHODS.md` §9; the
transformer on real traces is in `TRACE_VALIDATION.md` beside it.

## Environment

| | |
|---|---|
| Date | 2026-11-07 |
| Branch | `clarity-of-container`, on `bug-fixes` (F1 to F29) and upstream `prov-query` `6b744f7c` |
| Windows | Windows 11 Pro, OpenJDK 21.0.2, Apache Maven 3.9.16, `maven-surefire-plugin` 3.5.5, JUnit Jupiter 6 |
| Linux | Ubuntu 24.04.3, Linux 6.8.0-146, OpenJDK 21 (the trace VM; `make -C pkg/java`) |

On Windows, `pkg/java/cfg` (a git symbolic link to the root `cfg`) was
replaced by a directory junction for the run, so the tests read the real
`cfg/spade.reporter.Audit.config` and Linux constants file.

## Results

```
mvn test -Dtest='ContainerLabelsTest*'          177 tests, all pass
  ContainerLabelsTest                             5   the transformer's contract
  ContainerLabelsTest$CurrentModuleAndReporter   43   scenarios
  ContainerLabelsTest$ModuleWithoutF7            43
  ContainerLabelsTest$ReporterWithoutF17         43
  ContainerLabelsTest$ModuleWithoutF7AndReporterWithoutF17  43
mvn test (every test), Windows                  491 tests, 5 fail: FilesTest (4) and
                                                SettingTest.parsesTextFileReference, which
                                                fail on Windows only (file permissions,
                                                CRLF), as without this branch
make -C pkg/java (every test), Linux            491 tests, all pass
```

## Checking the tests

Each bug below was planted in `ContainerLabels.java` alone, the tests run,
and the file restored.

| Planted bug | Failing scenarios (in every one of the four classes) |
|---|---|
| Artifacts need not have their processes' namespace | `socketInAnotherNetNamespace_isLeft`, `systemVObjectWithAnotherIpcNamespace_isLeft` |
| Artifacts accept unlabeled processes | `socketAlsoUsedByAHostProcessInTheSameNetNamespace_isLeft` |
| Members need not be in the container's PID namespace | `nestedContainers_eachProcessGetsItsInnermostContainer`, `nestedContainer_inAReusedOuterId_belongsToTheOuterThatStartedIt` |
| Every process of the namespace in the lifetime, without the edges from the start | `processFirstSeenInsideAContainer_isNotLinkedToItsStart_soNotLabeled`, `subgraphWithoutAProcesssCreation_leavesThatProcessAndItsDescendantsUnlabeled` |
| No lifetime: neither entries nor members checked against it | `reusedNamespaceIdAndPids_eachContainerGetsItsOwnId` |
| Vertices that already have a `container_id` used to identify containers | `inputWithItsLabeledCopy_asQuickGrailStoresIt_givesTheSameLabelsAndNothingNew` |
| Entries not checked against the lifetime (members still are) | none: the members' check covers it |
