# Container Methods in QuickGrail: Design Notes

`getContainerInit` and `getContainerBoundary` are QuickGrail graph methods
for the two container patterns in CLARION (Chen et al., *CLARION: Sound and
Clear Provenance Tracking for Microservice Deployments*, USENIX Security
2021), §4.2. This file records how they work, every design decision behind
them, the facts they rely on, and their limits. User-facing signatures and
examples belong on the wiki's
[QuickGrail Reference](https://github.com/ashish-gehani/SPADE/wiki/QuickGrail-Reference)
page.

Status: implemented on branch `clarity-of-container`, which is based on
upstream `prov-query`. The tests use synthetic traces modeled on the Audit
reporter's source code. Neither method has been run on a real trace yet
(§8).

Code, relative to `pkg/java/src/main/java/spade/`:

| File | Role |
|---|---|
| `query/quickgrail/instruction/ContainerAnalysis.java` | Shared analysis: lineage edge sets, container starts, containers |
| `query/quickgrail/instruction/GetContainerInit.java` | `getContainerInit` |
| `query/quickgrail/instruction/GetContainerBoundary.java` | `getContainerBoundary` |
| `query/quickgrail/core/QuickGrailQueryResolver.java` | Argument parsing; reads the host PID namespace ID |
| `reporter/audit/LinuxConstants.java` | `getProcPidInitIno()` |

## 1. What the methods return

```
$init = $base.getContainerInit()
$all  = $base.getContainerBoundary()
$one  = $base.getContainerBoundary('4026532270')
$nth  = $base.getContainerBoundary('4026532270', 2)
$mine = $base.getContainerBoundary($processes)
```

- **`getContainerInit()`** covers every container started during the trace,
  nested ones included. For each one it returns:
  - the unshare/setns steps of the process that started the container,
  - the creation of the container's first process (its init),
  - the init's versions up to and including its first execve, which runs the
    application.
- **`getContainerBoundary(...)`** returns:
  - the selected containers' processes,
  - every vertex those processes connect to, such as the files and sockets they
    used and the runtime process that created or entered them,
  - all edges among those vertices.

  Selecting a container also selects the containers started inside it.

Both methods need the Audit reporter with `namespaces=true`, which is off by
default and requires the kernel module. Without namespace labels, both
methods return empty graphs.

For a `docker run`, `getContainerInit` returns these vertices, with the
edges between them. They are listed in time order; provenance edges point the
other way, from new to old.

```
stage 1 of runc init                               PID namespace: host
  └ unshare → stage 1                              host; its children now go to X
      └ fork (CLONE_PARENT|SIGCHLD) → stage 2      X: the container's init
          └ unshare(CLONE_NEWCGROUP) → stage 2     X
              └ execve → nginx                     X: the application
```

Processes above stage 1 are not part of the result. That includes
containerd-shim, `runc create`, and stage 0 of runc init. The application's
children and later execves are also left out.

The reporter names a new process after its creator's command name at the
clone. So stage 1 appears as `runc:[0:PARENT]` and the init as
`runc:[1:CHILD]`, until the init's execve renames it.

## 2. Paper anchoring

| Paper | What it says | What the method does |
|---|---|---|
| §4.2.1 Boundary of Containers | A container at runtime is the set of processes in one PID namespace. An artifact belongs to a container if a process inside it used the artifact. | Selects processes by PID namespace and splits a reused ID into separate containers. Adds the vertices those processes touch and the edges among them. |
| §4.2.2 Initialization of Containers | Initialization starts with `unshare` or `clone` with new-namespace flags and ends with the `execve` that launches the application. | Finds each start from recorded namespace changes and clone flags, then follows the init up to its first execve. |
| §5.2 Cross-container evaluation | Per-container figures (Figs. 14–16) and measurements across multi-container traces (Tables 7–9). | Boundary selects one container or all of them. Init reports every start in the trace. |

In CLARION, §4.2.1 was a labeling property and §4.2.2 was an
ingestion-time filter. Neither had a query API, so the method shapes here
come from how §5 uses the patterns. Both methods are query-side only, and
the reporter is unchanged.

## 3. Decisions

| # | Topic | Decision | Reason |
|---|---|---|---|
| D1 | Architecture | Build both methods from existing executor primitives. Add no executor methods and change no storage backend or reporter. | One implementation serves PostgreSQL, Neo4j and Quickstep. `getPath` uses the same approach. |
| D2 | Linking processes | Link processes through WasTriggeredBy edges only. Never pair them by equal `pid`, `pid namespace` or `ns pid` values, so no `getMatch` on those keys. | The kernel reuses host pids and namespace IDs within a boot, so equal values can join unrelated processes. `ns pid` holds the pid as seen from the parent's namespace, so an init never has `ns pid` 1 (§4.1). |
| D3 | ID reuse | Handle PID namespace ID reuse, assuming only that the trace spans one boot and the clock never moves backwards. | IDs are reused as soon as a namespace is freed (§4.2). Treating one ID as one container merges containers that ran at different times. |
| D4 | Host namespace | Treat the host's PID namespace as `PROC_PID_INIT_INO` (4026531836). Read it from the Audit reporter's Linux constants file instead of hard-coding it. | The kernel fixes this value for the root PID namespace. The constants file is where SPADE already keeps kernel constants. |
| D5 | Unobserved processes | Exclude processes labeled `-1` (unobserved namespaces) from both methods, without an error. | Their container membership is unknown. |
| D6 | Containers started before tracing | `getContainerInit` reports no start for such containers and raises no error. `getContainerBoundary` still returns their processes, as a container "running when tracing started". | The start itself was not recorded, but the processes were. |
| D7 | Nested containers | Selecting a container includes the containers started inside it. `getContainerInit` reports nested starts. | The kernel counts processes of a nested PID namespace as inside the outer one too. A nested start is still a container start. |
| D8 | Boundary API | Support four forms: `()`, `('<id>')`, `('<id>', <n>)`, `($processes)`. `('<id>')` fails with a numbered list when the ID belonged to more than one container. | The ID form covers the common case. The error shows when an ID was reused. The number and seed forms pick one container unambiguously. |
| D9 | ID argument type | Take the PID namespace ID as a string literal. | Annotation values are strings, and QuickGrail passes annotation values as strings. |
| D10 | Init arguments | `getContainerInit()` takes no arguments. The `maxDepth` setting is no longer used. | Initialization always ends at an execve, so a search-depth limit only risks truncating results. |
| D11 | End of initialization | Initialization ends at the init's first execve. Later execves belong to the application. | This is where CLARION §4.2.2 ends the pattern. |
| D12 | Start of initialization | The result starts at the process that changed its children's PID namespace, or that cloned with CLONE_NEWPID. It also includes that process's unshare/setns steps. The runtime processes above it are left out. | CLARION §4.2.2 starts the pattern at the unshare/clone. |
| D13 | Incomplete starts | If an init never reaches execve in the trace, `getContainerInit` fails with a RuntimeException that names up to five of those processes. The SPADE client keeps running. | A partial result can't be told apart from a complete one, so the failure is loud. `QuickGrailExecutor` turns instruction exceptions into a failed query. |
| D14 | Containers over time | A container is a PID namespace ID plus a time window, from its recorded start to the next start of that ID. Processes are assigned by `start time`, or `seen time` if they have no start time. | This works under ID reuse given D3's assumptions (§5.4). |
| D15 | Unknown ID | `getContainerBoundary('<id>')` returns an empty graph. `getContainerBoundary('<id>', <n>)` fails because container n does not exist. | This matches how `getVertex` handles no matches, while an explicitly numbered container must exist. |
| D16 | Invalid ID | Reject the host ID and `-1` when the query is resolved. | Neither is a container, so both inputs are mistakes. |
| D17 | Boundary shape | The boundary is the members, every vertex adjacent to them in either direction, and all edges among those vertices. This is unchanged from the first version. | This is CLARION §4.2.1: container processes, the artifacts they used, and the connecting edges. |
| D18 | Test fidelity | Run every behavior test under both adjacency semantics of the storage backends. | The code must not depend on a difference between backends (§5.1). |

## 4. Facts the design relies on

### 4.1 Audit reporter and kernel module

Verified by reading the source. `cfg/` is at the repository root, Java
classes are under `pkg/java/src/main/java/spade/`, and the kernel module is
under `pkg/linux/kernel_modules/audit/`.

| Fact | Where |
|---|---|
| Process vertices carry `pid namespace`, `children pid namespace`, `mount namespace` and the other namespace labels only when `namespaces=true`. The default is `false`. | `cfg/spade.reporter.Audit.config`, `reporter/audit/process/NamespaceIdentifier.java` |
| For clone/fork/vfork, the kernel module looks up the child from the syscall's return value and reports the child's namespaces. `ns pid` is that return value: the child's pid as seen from the parent's namespace. | kernel module `kernel/helper/namespace.c` (`kernel_helper_namespace_populate_msg`), `ProcessManager.handleForkVforkClone` |
| A process that was neither created nor exec'd in the trace has `-1` for every namespace until it calls execve, unshare or setns. | `ProcessStateManager` (`ProcessState` defaults), `ProcessManager.buildNamespaceIdentifierForPid` |
| A process created in the trace carries `start time`, the time of its clone or execve. A process first seen in another syscall carries `seen time`. | `ProcessManager.handleForkVforkClone`, `handleExecve`, `buildProcessIdentifierFromSyscall` |
| clone with SIGCHLD is recorded as `fork`. With CLONE_VM and CLONE_VFORK as well, it is recorded as `vfork`, shown as `fork` when `simplify=true` (the default). The clone flags stay in the edge's `flags` annotation, e.g. `CLONE_NEWNS\|CLONE_NEWPID\|SIGCHLD`. | `ProcessManager.handleForkVforkClone`, `LinuxConstants.stringifyCloneFlags` |
| unshare and setns create a new version of the process, joined by a WasTriggeredBy edge from new to old. The version keeps the name and the `start time` or `seen time`; only namespace labels change. If the process had the same labels before, the earlier vertex is reused, so version edges can form cycles. | `ProcessWithoutAgentManager.handleNamespaceUpdate`, `ProcessUnitState.hasTheNamespaceEverBeenSeenForProcess` |
| execve creates a new version with a new `start time`. | `ProcessManager.handleExecve` |
| With the default `agents=false` and `units=false`, setuid and setgid produce WasControlledBy edges, not versions. With `agents=true` they produce WasTriggeredBy versions, and units add `unit` versions. | `ProcessWithoutAgentManager.handleAgentUpdate`, `ProcessWithAgentManager.handleAgentUpdate` |
| After unshare or setns, a setuid/setgid (or any detected agent change) relabels the process with the namespaces it had before the unshare/setns. unshare/setns don't update the per-process namespace state that those updates read. | `ProcessManager.handleNamespaceUpdateFromSyscall` (never calls `ProcessStateManager.setNamespaces`), `handleSetuidSetgid` |
| Edges carry `time` as seconds and three-digit milliseconds (e.g. `1700000000.123`), plus an `event id`. An exit is a WasTriggeredBy edge from a process to itself. | `reporter/Audit.putEdge`, `ProcessManager.handleExit` |
| `clone3` is not handled: neither the kernel module nor the reporter mentions it. | `pkg/linux/kernel_modules/audit`, `pkg/java` |
| Storage keeps annotation values as strings (PostgreSQL uses `varchar` columns), so ordering comparisons compare strings. | `storage/postgresql/PostgreSQLInstructionExecutor.java` |

### 4.2 Linux kernel rules

- A new process goes into its parent's PID namespace for children
  (`pid_ns_for_children`). clone(CLONE_NEWPID) instead creates a new
  namespace nested in the parent's.
- A process's `pid_ns_for_children` differs from its own namespace only after
  unshare(CLONE_NEWPID) or setns(CLONE_NEWPID).
  - While they differ, unshare(CLONE_NEWPID) and clone(CLONE_NEWPID) fail with
    EINVAL (`copy_pid_ns`).
  - setns can only enter the process's own PID namespace or one nested in it
    (`pidns_install`).
- The first process created in a new PID namespace becomes its init (pid 1).
  When the init exits:
  - every process in the namespace is killed, including processes in nested
    namespaces (`zap_pid_ns_processes`),
  - no new process can enter the namespace (`alloc_pid` fails with ENOMEM).
- A thread can't be created into another PID namespace: CLONE_THREAD fails
  with EINVAL when `pid_ns_for_children` differs. So every crossing into a
  namespace is a new process.
- Namespace IDs are proc inode numbers.
  - The root PID namespace always has `PROC_PID_INIT_INO` = 0xEFFFFFFC =
    4026531836 (Linux ≥ 3.8; the kernel module supports 5.4–6.17).
  - Other namespaces take the lowest free number from 0xF0000000
    (`proc_alloc_inum`), and the number is reused once the namespace is freed.
  - A namespace stays allocated while any of these hold it: a process in it, a
    process that has it as `pid_ns_for_children`, an open file descriptor, or a
    bind mount.

### 4.3 Assumptions

- A trace covers a single boot, with no reboot partway through.
- The system clock is not set backwards during the trace.
- Audit records are not lost. §7 lists what breaks if they are.
- `time`, `start time` and `seen time` keep the reporter's format (§7, L8).

## 5. How it works

### 5.1 Building blocks

The composite uses these primitives: `getVertex`, `getEdge`,
`getEdgeEndpoint`, `getAdjacentVertex`, `getWhereAnnotationsExist`,
`unionGraph`, `intersectGraph`, `subtractGraph`, `insertLiteralVertex`,
`getSubgraph` and `getGraphCount`. `exportVertices` and `exportEdges` are
used only for small sets: unshare/setns steps, crossing edges, and seed
processes.

The backends differ in ways the code must not depend on:

- **Adjacency.** In `getAdjacentVertex`, PostgreSQL and Quickstep add every
  source vertex to the result. Neo4j adds a source only through a matching
  edge, and both endpoints of that edge must be vertices of the subject graph.
  So edge sets are always passed along with their endpoints (the `*Graph`
  helpers in `ContainerAnalysis`), and closures add their seeds explicitly.
- **Subtracting the base graph.** Neo4j returns an empty graph when the base
  graph is subtracted, so the code never does it.
- **Missing annotation columns.** In PostgreSQL, a comparison on a column
  that doesn't exist matches nothing, except `!=`, which matches everything.
  The code only uses `==`, `LIKE`, `>=` and `<`.

Lineage edge sets are the subject graph's WasTriggeredBy edges, selected by
`operation`:

| Set | Operations |
|---|---|
| creation | `clone`, `fork`, `vfork` |
| execve | `execve` |
| same-program versions | `unshare`, `setns`, `setuid`, `setreuid`, `setresuid`, `setfsuid`, `setgid`, `setregid`, `setresgid`, `setfsgid`, `update`, `unit` |
| namespace steps | `unshare`, `setns` |

`closure(edges, seeds, direction, stop)` repeats `getAdjacentVertex` until
no new vertex appears. It runs one storage query per lineage step, and cycles
don't break it.

### 5.2 Finding container starts (`ContainerAnalysis.crossings`)

A crossing is a creation edge whose child lands in a container PID namespace
other than its parent's. By §4.2, such children come only from two places: a
process after it changed its `children pid namespace`, or a clone flagged
CLONE_NEWPID.

1. **Classify recorded namespace steps.** Export the unshare/setns steps. A
   step counts only if the new version's `pid namespace` and
   `children pid namespace` are both observed and `children pid namespace`
   changed. Each counting step is one of:
   - **restorer:** the new version's children go back to its own namespace.
   - **namespace creation:** an unshare from an observed version. Record the
     new ID and the step's time and event id.
   - **joiner:** a setns, or an unshare from a version labeled `-1`. That
     unshare may have been for another namespace type after an unshare of the
     PID namespace before tracing, so a new namespace is not assumed.
2. **Find the versions after each change.** Follow all version edges forward,
   stopping at a restorer or a change of the other kind. A process that
   returns to labels it had before reuses a vertex, so one vertex can be both
   an unshare result and a setns result. Such a vertex is followed from both
   and is never stopped by its own kind of change.
3. **Collect candidates.** Take the creation edges out of those versions, plus
   every creation edge whose `flags` contain `CLONE_NEWPID`.
4. **Classify the candidates** whose child is in a container namespace:
   - A CLONE_NEWPID clone is a **start**.
   - A child of a version after an unshare is grouped under the namespace
     creation behind it: the latest recorded creation of the child's namespace
     ID before the child, in event order (time, then event id). The first child
     in each group is a **start**; the rest **enter** the namespace.
   - A child of a version after a join **enters**.

Grouping uses namespace ID plus time rather than lineage, because version
cycles make "the unshare before this version" ambiguous. It is still exact:
the creating process holds the namespace while its children go there, so no
other creation of that ID can come in between.

### 5.3 `getContainerInit`

1. With no starts, return an empty graph.
2. Collect the init's versions before the application: follow same-program
   version edges forward from each init. Follow the execve edges out of those
   versions; their new versions are the applications.
3. If an init has no application among its versions, fail as described in
   D13. This is checked by walking back over same-program versions from the
   versions that called execve.
4. Collect the start side:
   - the creators, meaning the parents of start edges,
   - each creator's versions since its namespace change: its ancestor versions
     intersected with the versions after a change,
   - the unshare/setns step chain leading to that change.
5. Return `getSubgraph` of the lineage graph over the vertices from steps 2
   and 4.

### 5.4 Containers over time (`ContainerAnalysis.Instance`)

For a PID namespace ID X with starts s1, s2, … in event order, container k
covers the window [time(s_k), time(s_{k+1})). If X has processes whose time
is before time(s1), or X has no starts at all, one more container comes
first: "running when tracing started".

A process version belongs to the container of its PID namespace whose window
contains its `start time`, or its `seen time` if it has no start time. This
is sound:

- Every process of a container is created, or first seen, after the
  container starts.
- unshare/setns versions keep the process's time.
- A later container with the same ID can only start after every process of
  the earlier one is gone (§4.2).

**Nesting.** Container C contains container D if D's start edge has a parent
in C's namespace whose time falls in C's window. This is applied
recursively, so any depth works.

### 5.5 `getContainerBoundary`

| Form | Members |
|---|---|
| `()` | Every process whose `pid namespace` is neither the host's nor `-1`. No windows are needed. |
| `('X')` | The containers of X. More than one: fail with a numbered list. Exactly one: its processes plus nested containers. None: no members. |
| `('X', n)` | Container n of X, with nested containers. An out-of-range n fails. |
| `($p)` | Export the processes of `$p` that are container processes. Each one selects the container whose window holds its time. Members are the union of those containers, with nested containers. |

The result is `getSubgraph(subject, members ∪ getAdjacentVertex(subject, members, both))`.

The error for a reused ID looks like this:

```
getContainerBoundary: PID namespace 4026532270 belonged to 3 containers in this trace, since the kernel
reuses IDs. Pick one with getContainerBoundary('4026532270', <number>):
  1: running when tracing started
  2: started 1700000000.042, first process runc:[1:CHILD] (host pid 4103)
  3: started 1700000000.108, first process runc:[1:CHILD] (host pid 4103)
```

As noted in §1, an init carries the name of the runtime process that created
it.

### 5.6 Errors and the SPADE client

- **When the query is resolved**, it fails on:
  - a wrong number or type of arguments,
  - the host ID or `-1` as the ID,
  - a container number below 1,
  - a constants file without `PROC_PID_INIT_INO`.
- **When the query runs**, it fails on:
  - an init that never reaches execve,
  - a reused ID given without a number,
  - a container number out of range.

`QuickGrailExecutor` catches exceptions from both phases
(`UnexpectedFailure`). The query fails with its message, and the client
keeps accepting queries.

## 6. Configuration

- `cfg/spade.reporter.audit.LinuxConstants.config` defines
  `PROC_PID_INIT_INO = 0xEFFFFFFC`.
- `LinuxConstants.getProcPidInitIno()` returns the value. The key is optional
  when the file loads, so older constants files still work for the Audit
  reporter. It becomes required when a container method runs.
- The resolver finds the constants file through `constantsSource` in
  `cfg/spade.reporter.Audit.config`.

## 7. Limitations

| # | Limitation | Effect | Workaround |
|---|---|---|---|
| L1 | Starts before tracing were not recorded. | Such containers have no start in `getContainerInit`. Containers nested in them before tracing can't be linked to their outer container and show up as separate containers "running when tracing started". | None. Start tracing before the containers. |
| L2 | PID namespace changes before tracing were not recorded. | A process that called unshare(CLONE_NEWPID) before tracing and creates its first child during tracing isn't seen as starting a container. That container counts as running before tracing. | None. |
| L3 | An unshare by a process whose namespaces were never observed (`-1`) is taken as joining, not creating. | If it really did create a PID namespace, that start is missed. | None. The alternative would invent starts. |
| L4 | `clone3` is not recorded (§4.1). | A container whose init is created with clone3 has no start and no creation edge. Some runtimes may use clone3, e.g. to create processes directly in a cgroup. | Add clone3 to the kernel module and the reporter. |
| L5 | A setuid/setgid between unshare/setns and the fork reverts the reporter's namespace labels (§4.1). | That start or entry is missed. runc calls setresuid before its PID namespace unshare, so it is not affected. | Fix in the reporter: update the per-process namespace state on unshare/setns. |
| L6 | Lost or incomplete audit records. | A missing unshare, clone or execve can hide a start, merge containers that reuse an ID, or make `getContainerInit` fail. | None. |
| L7 | Time has millisecond granularity. | A process created in the same millisecond as the next start of its ID would be placed in the next container. This is practically impossible, because the earlier container must already be gone. | None needed. |
| L8 | Windows compare `time`, `start time` and `seen time` as strings. | Correct while seconds have ten digits (until 2286) and milliseconds three. A filter that rewrites time values (e.g. `spade.filter.ConvertTime`) or a different timestamp format breaks the ordering. | Don't rewrite those annotations before storage. |
| L9 | "Host" means the root PID namespace. | If the traced system is itself inside a PID namespace (e.g. a system container), all of its processes count as container processes. | Select one container by ID. |
| L10 | A process that creates a PID namespace but never calls execve in it counts as an incomplete start. An example is a sandbox or test harness that runs its own code in the new namespace. | `getContainerInit` fails for the whole trace. | Run it on a subgraph without those processes (§8, Q3). |
| L11 | Initialization ends at the init's first execve (D11). | With `docker run --init`, the result ends at docker-init (tini), not at the application tini starts. | None. |
| L12 | Pods with a shared process namespace (Kubernetes `shareProcessNamespace: true`). | The other containers join the pause container's PID namespace, so they are entries and the pod is one container. | None. That is what the kernel sees. |
| L13 | Only PID namespaces define containers. | Mount, network, IPC, user and cgroup namespaces play no part. | Use `getVertex` on those labels. |
| L14 | `getContainerBoundary()` returns one merged graph. | It is not split per container. | Use the ID or number forms per container. |
| L15 | Starts are found from the subject graph's lineage edges. | On a subject graph missing some WasTriggeredBy edges or their endpoints, starts can be missed and containers that reuse an ID get merged. | Run on `$base` or a graph that keeps process lineage. |
| L16 | The seed form reads the labels of its seed processes into memory. | A huge seed graph is slow. | Keep `$p` small, or use `getContainerBoundary()`. |
| L17 | Performance has not been measured. | Each lineage step is a storage query, and container lookups run a few queries per container. | Measure on real traces. |

## 8. Open questions and follow-ups

| # | Item | Status |
|---|---|---|
| Q1 | Validate on real traces: `docker run` and `docker exec` with `namespaces=true`. | Asked Hassaan for traces. If he has none, collect our own. |
| Q2 | Is `ns pid` meant to be the pid as seen from the parent's namespace? | To ask Hassaan. The design doesn't depend on the answer. |
| Q3 | Should `getContainerInit` fail on inits that never call execve (L10), or report them some other way? | Currently fails (D13). Needs a decision once real traces show whether such processes occur. |
| Q4 | Reporter: namespace labels revert after setuid/setgid (L5). | Worth reporting upstream. |
| Q5 | Reporter and kernel module: `clone3` (L4). | Worth reporting upstream. |
| Q6 | Wiki QuickGrail Reference entries for both methods. | Drafts exist from the first version and need updating to the forms in §1 before they go on the wiki. |

## 9. Test design

Tests are in `pkg/java/src/test/java/spade/`, run with JUnit Jupiter via
`mvn test` in `pkg/java`. Results for review are in
`query/quickgrail/instruction/TEST_RESULTS.md`.

### 9.1 Layers

| Class | Kind | What it covers |
|---|---|---|
| `reporter/audit/LinuxConstantsTest` | unit | `PROC_PID_INIT_INO` is read from the shipped constants file; it is optional when loading and required when requested |
| `.../instruction/InMemoryQueryHarnessTest` | unit | The harness behaves like the backends: both adjacency semantics, per-component subtraction, string ordering, SQL `LIKE` |
| `.../instruction/GetContainerInitTest` | unit | Fields, label, plan printing, and the host ID check. The resolver reads the host ID from the real constants file and rejects arguments. |
| `.../instruction/GetContainerBoundaryTest` | unit | The four constructor forms and their validation, plan printing, and resolver parsing of every form and invalid argument |
| `.../instruction/GetContainerInitIntegrationTest` | integration | 18 trace scenarios × 2 adjacency semantics |
| `.../instruction/GetContainerBoundaryIntegrationTest` | integration | 12 trace scenarios × 2 adjacency semantics |

### 9.2 In-memory harness

`InMemoryQueryHarness` implements the executor primitives the methods use,
over in-memory sets. Every other primitive throws, so a test that reaches an
unintended primitive fails loudly. The harness follows backend semantics
where they matter:

- **Adjacency.** It has two modes, `SOURCES_ALWAYS` (PostgreSQL, Quickstep)
  and `SOURCES_VIA_EDGES` (Neo4j). Each integration scenario runs once per
  mode through two `@Nested` classes.
- **Comparisons.** Annotation values compare as strings, and `LIKE` uses SQL
  wildcards.
- **Subtraction.** Subtracting the base graph is rejected.
- **No-argument queries.** `getVertex` and `getEdge` without arguments copy
  every vertex or edge.

### 9.3 Trace fixtures

`ContainerTrace` writes provenance the way the reporter records it (§4.1):

- `start time` and `seen time`,
- `-1` labels for processes that existed before tracing,
- clone recorded as `fork` with its `flags`,
- unshare/setns versions that keep the process's time and reuse vertices when
  labels repeat,
- `time` and `event id` on edges,
- `exit` self-loops, and artifacts with Used/WasGeneratedBy edges.

It also builds `docker run` and `docker exec` as runc performs them. For
`docker run`, stage 1 unshares and then forks stage 2 with
CLONE_PARENT|SIGCHLD. Stage 2 unshares its cgroup namespace, starts Go
runtime threads, and calls execve. For `docker exec`, stage 1 calls setns
and then forks.

### 9.4 Scenarios

`getContainerInit`:

| Scenario | Checks |
|---|---|
| docker run | Exact vertex and edge sets: unshare step, init, cgroup unshare, first execve. Excluded: runtime chain above stage 1, application's children, later execve, files, exit edges. |
| docker exec into a running container | The exec is an entry. The result is only the run's start. |
| docker exec into a container started before tracing | Empty result. |
| clone with CLONE_NEWPID (LXC style) | The creator, the init and its execve. |
| clone with CLONE_NEWPID and no SIGCHLD | Recorded as `clone` and still found. |
| unshare then execve (`unshare --pid bash`) | The new program's first child is the init; the result includes the execve between unshare and fork. |
| creator joins another container's namespace before unshare | The setns step is in the result. |
| nested container | Both starts. The nested creator is inside the outer container. |
| later children of one unshare, same millisecond | Only the first child (by event id) is a start. |
| process returns to earlier labels (vertex reuse) | Two starts, then a setns back into the first container is an entry. |
| reused ID and host pids | Two separate starts. |
| container started before tracing; unshare before tracing | Empty result, no error (L1, L2). |
| unshare by a process with `-1` labels | Entry, not start (L3). |
| mount namespace unshare on the host | Empty result. |
| empty graph | Empty result. |
| init never calls execve | RuntimeException with count, host pid and namespace. |
| reused ID and pids, second init never calls execve | Still fails: the first container's execve doesn't hide it. |

`getContainerBoundary`:

| Scenario | Checks |
|---|---|
| every container | Exact boundary. Includes the runc processes that created or entered containers and a file written inside. Excludes the host process one step further out, a host process using the same file, and `-1` processes. |
| one ID | Only that container, including its docker exec processes. |
| container running before tracing | Its processes, including a `seen time` version, under `('X')`, `('X', 1)` and `()`. |
| unknown ID | Empty for `('X')`. `('X', 1)` fails with the exact message. |
| empty graph | Empty result. |
| reused ID (before tracing, run, run with the same pids) | Exact numbered error message. Containers 1–3 each select exactly their processes. 4 fails. `()` returns them all. |
| nesting three levels deep | Each level includes the deeper ones; a sibling container is excluded. |
| nested container under a reused outer ID | Belongs only to the outer container that started it. |
| seed processes | The container a seed process belongs to, with nested containers. Seeds in two containers select both. Two seeds in one container select it once. A seed in a nested container selects only that one. |
| seeds outside containers | Empty result. |

### 9.5 Checking the tests themselves

During development, ten deliberate bugs were planted one at a time, and each
made at least one test fail under both adjacency semantics.
`TEST_RESULTS.md` lists them.

### 9.6 Not covered

- **Real backends.** The harness mirrors PostgreSQL, Neo4j and Quickstep
  semantics, but the methods have not run against a real database.
- **Real traces.** See Q1 in §8.
- **Performance and concurrency.**
