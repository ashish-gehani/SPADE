# Container Methods in QuickGrail: Design Notes

`getContainerInit` and `getContainerBoundary` are QuickGrail graph methods
for the two container patterns in CLARION (Chen et al., *CLARION: Sound and
Clear Provenance Tracking for Microservice Deployments*, USENIX Security
2021), §4.2. This file records how they work, every design decision behind
them, the facts they rely on, and their limits. User-facing signatures and
examples belong on the wiki's
[QuickGrail Reference](https://github.com/ashish-gehani/SPADE/wiki/QuickGrail-Reference)
page.

Status: implemented on branch `clarity-of-container`, which sits on branch
`bug-fixes`. `bug-fixes` starts from upstream `prov-query` and holds every fix
the work needed outside the methods themselves, in the kernel module, the
audit bridge, the Audit reporter and the storages. `BUG_FIXES.md`, beside
this file, describes each of them (F1 to F10) and how it was checked; this
file refers to them by those numbers. Fixes found later go on `bug-fixes` too,
and `clarity-of-container` is rebased onto it.
The tests use synthetic traces modeled on the Audit reporter's source code.
The methods have run on real traces, but no trace has passed every
completeness check yet (`TRACE_VALIDATION.md`, Part B; §8).

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
default and requires the kernel module; the Audit config named in §6 sets it.
Without namespace labels, both methods return empty graphs.

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
| D2 | Linking processes | Link processes through WasTriggeredBy edges only. Never pair them by equal `pid`, `pid namespace` or `ns pid` values, so no `getMatch` on those keys. | The kernel reuses host pids and namespace IDs within a boot, so equal values can join unrelated processes. `ns pid` is meant to be the PID inside the process's own namespace. But kernel modules without F1 recorded a namespace's first process with its PID in the creator's namespace, never 1 (§4.1), and traces from those modules keep that value. |
| D3 | ID reuse | Handle PID namespace ID reuse, assuming only that the trace spans one boot and the clock never moves backwards. | IDs are reused as soon as a namespace is freed (§4.2). Treating one ID as one container merges containers that ran at different times. |
| D4 | Host namespace | Treat the host's PID namespace as `PROC_PID_INIT_INO` (4026531836). Read it from the Audit reporter's Linux constants file instead of hard-coding it. | The kernel fixes this value for the root PID namespace. The constants file is where SPADE already keeps kernel constants. |
| D5 | Unobserved processes | Exclude processes labeled `-1` (unobserved namespaces) from both methods, without an error. | Their container membership is unknown. |
| D6 | Containers started before tracing | `getContainerInit` reports no start for such containers and raises no error. `getContainerBoundary` still returns their processes, as a container "running when tracing started". | The start itself was not recorded, but the processes were. |
| D7 | Nested containers | Selecting a container includes the containers started inside it. `getContainerInit` reports nested starts. | The kernel counts processes of a nested PID namespace as inside the outer one too. A nested start is still a container start. |
| D8 | Boundary API | Support four forms: `()`, `('<id>')`, `('<id>', <n>)`, `($processes)`. `('<id>')` fails with a numbered list when the ID belonged to more than one container. | The ID form covers the common case. The error shows when an ID was reused. The number and seed forms pick one container unambiguously. |
| D9 | ID argument type | Take the PID namespace ID as a string literal. | Annotation values are strings, and QuickGrail passes annotation values as strings. |
| D10 | Init arguments | `getContainerInit()` takes no arguments. The `maxDepth` setting is no longer used. | Initialization always ends at an execve, so a search-depth limit only risks truncating results. |
| D11 | End of initialization | Initialization ends at the init's own first execve. Later execves belong to the application. Kept for now even though some sandboxes start their application from a child of the init (Q3). | This is where CLARION §4.2.2 ends the pattern. |
| D12 | Start of initialization | The result starts at the process that changed its children's PID namespace, or that cloned with CLONE_NEWPID. It also includes that process's unshare/setns steps. The runtime processes above it are left out. | CLARION §4.2.2 starts the pattern at the unshare/clone. |
| D13 | Incomplete starts | If an init never reaches execve in the trace, `getContainerInit` fails with a RuntimeException that names up to five of those processes. The SPADE client keeps running. This also applies to sandboxes whose init never calls execve (L10, Q3). | A partial result can't be told apart from a complete one, so the failure is loud. `QuickGrailExecutor` turns instruction exceptions into a failed query. |
| D14 | Containers over time | A container is a PID namespace ID plus a time window, from its recorded start to the next start of that ID. Processes are assigned by `start time`, or `seen time` if they have no start time. | This works under ID reuse given D3's assumptions (§5.4). |
| D15 | Unknown ID | `getContainerBoundary('<id>')` returns an empty graph. `getContainerBoundary('<id>', <n>)` fails because container n does not exist. | This matches how `getVertex` handles no matches, while an explicitly numbered container must exist. |
| D16 | Invalid ID | Reject the host ID, `-1` and `4294967295` when the query is resolved. | None of them is a container, so these inputs are mistakes. |
| D17 | Boundary shape | The boundary is the members, every vertex adjacent to them in either direction, and all edges among those vertices. This is unchanged from the first version. | This is CLARION §4.2.1: container processes, the artifacts they used, and the connecting edges. |
| D18 | Test fidelity | Run every behavior test under both adjacency semantics of the storage backends. | The code must not depend on a difference between backends (§5.1). |
| D19 | Older kernel modules | Fix the unnamed new PID namespace in the kernel module (F7) and also handle it in the methods: count `4294967295` as unobserved, and name a creation recorded that way after the first process its caller creates (§5.2). Run every integration scenario on traces recorded both ways. | Decided by the user (2026-11-02), option (c) of Q11. The module fix makes new traces correct and checkable against `/proc`; the methods' part keeps traces from today's upstream module usable. |

## 4. Facts the design relies on

### 4.1 Audit reporter and kernel module

Verified by reading the source. The rows on `ns pid`, on the unshare and
setns hooks, and on `clone3` were also checked on a live system
(`TRACE_VALIDATION.md`, Part A), and so was the row on the `children pid
namespace` after unshare (`BUG_FIXES.md` F7). `cfg/` is at the repository root, Java
classes are under `pkg/java/src/main/java/spade/`, and the kernel module is
under `pkg/linux/kernel_modules/audit/`.

| Fact | Where |
|---|---|
| Process vertices carry `pid namespace`, `children pid namespace`, `mount namespace` and the other namespace labels only when `namespaces=true`. The default is `false`. | `cfg/spade.reporter.Audit.config`, `reporter/audit/process/NamespaceIdentifier.java` |
| For clone/fork/vfork, the kernel module looks up the child from the syscall's return value and reports the child's namespaces. | kernel module `kernel/helper/namespace.c` (`kernel_helper_namespace_populate_msg`), `function/sys_{clone,fork,vfork}/action/audit.c` |
| `ns pid` is the PID of the process inside its own PID namespace (confirmed by the CLARION author, Q2). The reporter reads it only at process creation (`nsChildPid`), and later versions copy it. Kernel modules without F1 recorded the PID in the creator's namespace instead, which differs for a namespace's first process, a later child of an unshare, and a process a runtime starts inside an existing container (`docker exec`); traces from those modules keep the old values. The methods don't use `ns pid` (D2). | `kernel/helper/namespace.c`, `ProcessManager.handleForkVforkClone`, `ProcessManager.handleExecve`, `BUG_FIXES.md` F1 |
| An unshare or setns inside a non-host PID namespace writes a namespace record with the caller's labels. Kernel modules without F2 wrote none, so in their traces such a step leaves the process's labels unchanged. | `kernel/function/sys_unshare/action/audit.c`, `kernel/function/sys_setns/action/audit.c`, `kernel/helper/task.c`, `BUG_FIXES.md` F2 |
| A process not created in the trace has `-1` for every namespace until it calls unshare or setns. The kernel module has no execve hook, so an execve never reports namespaces. | `ProcessStateManager` (`ProcessState` defaults), `ProcessManager.buildNamespaceIdentifierForPid`, kernel module `kernel/function/` (the hooked functions) |
| A process created in the trace carries `start time`, the time of its clone or execve. A process first seen in another syscall carries `seen time`. | `ProcessManager.handleForkVforkClone`, `handleExecve`, `buildProcessIdentifierFromSyscall` |
| clone with SIGCHLD is recorded as `fork`. With CLONE_VM and CLONE_VFORK as well, it is recorded as `vfork`, shown as `fork` when `simplify=true` (the default). The clone flags stay in the edge's `flags` annotation, e.g. `CLONE_NEWNS\|CLONE_NEWPID\|SIGCHLD`. | `ProcessManager.handleForkVforkClone`, `LinuxConstants.stringifyCloneFlags` |
| unshare and setns create a new version of the process, joined by a WasTriggeredBy edge from new to old. The version keeps the name and the `start time` or `seen time`; only namespace labels change. If the process had the same labels before, the earlier vertex is reused, so version edges can form cycles. | `ProcessWithoutAgentManager.handleNamespaceUpdate`, `ProcessUnitState.hasTheNamespaceEverBeenSeenForProcess` |
| The version after unshare(CLONE_NEWPID) has the new namespace as its `children pid namespace`. Kernel modules without F7 could not name it, because the kernel reports no namespace until it has a process, and labeled the version `4294967295` instead; a namespace they could not read for another reason, as for a child that had already exited, also became `4294967295`. With F7 the module writes `-1` for those. The methods count both values as unobserved and take a new namespace's ID from the first process created after the unshare (§5.2, L19). | kernel module `kernel/helper/namespace.c`, `msg/namespace/serialize/audit.c`, `BUG_FIXES.md` F7 |
| execve creates a new version with a new `start time`. | `ProcessManager.handleExecve` |
| With the default `agents=false`, the reporter uses `ProcessWithAgentManager` (agent IDs are labels of the process vertex), and setuid and setgid produce a WasTriggeredBy version, a self-loop when the IDs don't change; runc's container init makes dozens. With `agents=true` (`ProcessWithoutAgentManager`) they produce WasControlledBy edges to Agent vertices instead, and `units=true` adds `unit` versions. The names of the two managers read the other way round; Part B corrected an earlier version of this row. | `Audit` (manager choice), `ProcessWithAgentManager.handleAgentUpdate`, `ProcessWithoutAgentManager.handleAgentUpdate`, `TRACE_VALIDATION.md` |
| After unshare or setns, a later execve or setuid/setgid (or any detected agent change) relabels the process with the namespaces it was created with (`-1` if it wasn't created in the trace). unshare/setns don't update the per-process namespace state those events read. | `ProcessManager.handleNamespaceUpdateFromSyscall` (never calls `ProcessStateManager.setNamespaces`), `handleExecve`, `handleSetuidSetgid` |
| Edges carry `time` as seconds and three-digit milliseconds (e.g. `1700000000.123`), plus an `event id`. An exit is a WasTriggeredBy edge from a process to itself. | `reporter/Audit.putEdge`, `ProcessManager.handleExit` |
| clone3, which glibc's `posix_spawn` uses (and `pthread_create` since glibc 2.34), is recorded like clone: the kernel module writes the same namespace record, with the flags it read from `struct clone_args` (`ns_clone_flags`), and the reporter handles syscall 435 as clone (F9). Without F9 a child created with clone3 got no creation record, as Part A showed. runc 1.3.3 creates container processes with `clone` either way. | `kernel/function/sys_clone3/`, `ProcessManager.handleForkVforkClone`, `BUG_FIXES.md` F9, `TRACE_VALIDATION.md` |
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
   changed. "Observed" excludes `-1` and `4294967295` (§4.1). Each counting
   step is one of:
   - **restorer:** the new version's children go back to its own namespace.
   - **namespace creation:** an unshare from an observed version. Record the
     new ID, the caller's host pid, and the step's time and event id.
   - **joiner:** a setns, or an unshare from a version labeled `-1`. That
     unshare may have been for another namespace type after an unshare of the
     PID namespace before tracing, so a new namespace is not assumed.

   An unshare whose new `children pid namespace` is `4294967295`, recorded by
   a module without F7, also counts: as a namespace creation whose ID is not
   known yet if the old version was observed, else as a joiner.
2. **Find the versions after each change.** Follow all version edges forward,
   stopping at a restorer or a change of the other kind. A process that
   returns to labels it had before reuses a vertex, so one vertex can be both
   an unshare result and a setns result. Such a vertex is followed from both
   and is never stopped by its own kind of change.
3. **Collect candidates.** Take the creation edges out of those versions, plus
   every creation edge whose `flags` contain `CLONE_NEWPID`.
4. **Name the unnamed creations.** A creation without an ID takes the
   namespace of the first candidate child its caller created after it and
   before the caller's next counting step. By §4.2 that child is the new
   namespace's init: the first process created after unshare(CLONE_NEWPID)
   goes to the new namespace, and a thread cannot be created there. The
   caller is matched by host pid. A later process that reuses the pid can
   only have candidate children after namespace steps of its own, which end
   the window. A creation whose namespace never gets a process stays unnamed
   and groups nothing.
5. **Classify the candidates** whose child is in a container namespace:
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
- `prov-query` (`6b744f7c`) adds
  `cfg/spade.reporter.Audit-with-namespace-awareness.config`: the default Audit
  config with `namespaces=true`, and also `fileIO`, `netIO`, `IPC`, `inode` and
  `networkAddressTranslation` on. Use it with the container methods. Files
  reach `getContainerBoundary` either way, since `open`, `creat`, `rename` and
  the like are always audited; `fileIO` adds read and write edges. With the
  kernel module loaded, network I/O is reported by the module and gated by
  `netIO` (`AuditControlManager`), so with `netIO=false` sockets may be
  missing from the boundary. Part B of `TRACE_VALIDATION.md` checks this.
- `networkAddressTranslation=true` turns on the kernel module's netfilter hook
  (`nf_hooks`). Without F3, that hook could sleep in atomic context and freeze
  the system while the audit backlog was full; it did while recording Part B.
  Use a module with F3 whenever `networkAddressTranslation=true`. With it, a
  full backlog drops packet records and counts them as lost.
- Both storages need F4 to F6. Without them, PostgreSQL silently drops every
  batch of vertices that holds a process named by a kernel-module record, and
  on Neo4j every container method returns an empty graph.

## 7. Limitations

| # | Limitation | Effect | Workaround |
|---|---|---|---|
| L1 | Starts before tracing were not recorded. | Such containers have no start in `getContainerInit`. Containers nested in them before tracing can't be linked to their outer container and show up as separate containers "running when tracing started". | None. Start tracing before the containers. |
| L2 | PID namespace changes before tracing were not recorded. | A process that called unshare(CLONE_NEWPID) before tracing and creates its first child during tracing isn't seen as starting a container. That container counts as running before tracing. | None. |
| L3 | An unshare by a process whose namespaces were never observed (`-1`) is taken as joining, not creating. | If it really did create a PID namespace, that start is missed. | None. The alternative would invent starts. |
| L4 | Traces recorded without F9, or with `namespaces=false` (§4.1). | Without F9, a process created with clone3 has no creation edge; inside a container it also has `-1` namespace labels, so `getContainerBoundary` leaves it out, and a container whose init is created with clone3 has no start. With F9 and `namespaces=false`, its flags are unknown, so a clone3 thread looks like a process. | Record traces with F9 and `namespaces=true`. |
| L5 | After unshare or setns, a later execve or setuid/setgid gets the namespace labels the process was created with (§4.1). | After an execve only the labels are wrong. Starts are still found, because later versions are followed through their edges; for example, after `unshare --pid bash` without `--fork`, the bash version is labeled as if its children stayed on the host. With the default `agents=false`, a setuid/setgid makes a version edge (§4.1), so as after an execve only the labels are wrong. With `agents=true` there is no version edge: later children then seem to come from the process's earlier vertex, so that start or entry is missed. runc calls setresuid before its PID namespace unshare, so it is not affected either way. | Fix in the reporter: update the per-process namespace state on unshare/setns. |
| L6 | Lost or incomplete audit records. | A missing unshare, clone or execve can hide a start, merge containers that reuse an ID, or make `getContainerInit` fail. | None. |
| L7 | Time has millisecond granularity. | A process created in the same millisecond as the next start of its ID would be placed in the next container. This is practically impossible, because the earlier container must already be gone. | None needed. |
| L8 | Windows compare `time`, `start time` and `seen time` as strings. | Correct while seconds have ten digits (until 2286) and milliseconds three. A filter that rewrites time values (e.g. `spade.filter.ConvertTime`) or a different timestamp format breaks the ordering. | Don't rewrite those annotations before storage. |
| L9 | "Host" means the root PID namespace. | If the traced system is itself inside a PID namespace (e.g. a system container), all of its processes count as container processes. | Select one container by ID. |
| L10 | A PID namespace whose init never calls execve counts as an incomplete start, even when a child of the init starts the application. bubblewrap (which Flatpak uses to launch apps) keeps its sandbox's PID 1 as its own reaper, `do_init`, and starts the app from another process. `systemd-nspawn --as-pid2` runs a stub init as PID 1 and the command as PID 2. | `getContainerInit` fails for the whole trace, e.g. whenever a Flatpak app starts during tracing. | Run it on a subgraph without those processes. Changing the rule is Q3. |
| L11 | Initialization ends at the init's first execve (D11). | With `docker run --init`, the result ends at docker-init (tini), not at the application tini starts. | None. |
| L12 | Pods with a shared process namespace (Kubernetes `shareProcessNamespace: true`). | The other containers join the pause container's PID namespace, so they are entries and the pod is one container. | None. That is what the kernel sees. |
| L13 | Only PID namespaces define containers. | Mount, network, IPC, user and cgroup namespaces play no part. | Use `getVertex` on those labels. |
| L14 | `getContainerBoundary()` returns one merged graph. | It is not split per container. | Use the ID or number forms per container. |
| L15 | Starts are found from the subject graph's lineage edges. | On a subject graph missing some WasTriggeredBy edges or their endpoints, starts can be missed and containers that reuse an ID get merged. | Run on `$base` or a graph that keeps process lineage. |
| L16 | The seed form reads the labels of its seed processes into memory. | A huge seed graph is slow. | Keep `$p` small, or use `getContainerBoundary()`. |
| L17 | Performance has not been measured. | Each lineage step is a storage query, and container lookups run a few queries per container. | Measure on real traces. |
| L18 | Traces recorded with a kernel module without F1 and F2 (§4.1). | An unshare or setns inside a container writes no record. So containers started with unshare from inside a container (e.g. Docker in Docker) are missed: `getContainerInit` doesn't report them, and `getContainerBoundary` doesn't attach them to the outer container. `ns pid` also holds the creator's view. | Record traces with a module that has the fixes. |
| L19 | Traces recorded with a kernel module without F7 (§4.1). | A new PID namespace's ID is not in the trace. §5.2 takes it from the first process the creator creates after the unshare, matched by host pid up to the creator's next namespace step. With lost records (L6), a later child of the creator can be taken for the init, as it can with F7. | Record traces with a module that has F7. |

## 8. Open questions and follow-ups

| # | Item | Status |
|---|---|---|
| Q1 | Validate on real traces: `docker run` and `docker exec` with `namespaces=true`. | Hassaan has no real traces (2026-10-11), so we collect our own in a VM (`TRACE_VALIDATION.md`). Part A, the kernel module's namespace records, is done. Part B, the methods themselves, is in progress. |
| Q2 | What is `ns pid` meant to be? | Answered by the CLARION author: the PID of the process inside its own PID namespace. The kernel module recorded something else for a namespace's first process and for processes started into a namespace from outside, and a related bug in the unshare and setns hooks lost records inside containers. F1 and F2 fix them; `TRACE_VALIDATION.md` Part A checked both on a live system. The container methods don't use `ns pid` (D2). |
| Q3 | Where should initialization end when the init never calls execve (L10)? | Decided for now: (a) keep D11 and D13, so such starts make the query fail. A trace can't settle this: a trace without such sandboxes proves nothing. The evidence came from the tools' source instead (L10). Not chosen for now: (b) end at the first execve by the init or by any process it created in the namespace, which fixes bubblewrap and nspawn but would end runc containers at OCI `startContainer` hooks, since those run inside the container before the application; (c) prefer the init's own first execve, else the first execve by a process it created in the namespace, and fail only when no process there calls execve. |
| Q4 | Reporter: namespace labels revert after execve or setuid/setgid (L5). | Not fixed; listed for the maintainers as N5 in `BUG_FIXES.md`. |
| Q5 | Reporter and kernel module: `clone3` (L4). | Fixed at the user's request (2026-11-02): F9. `pkg/linux/kernel_modules/test/ns-pid/clone3-test.sh` shows the record and its flags; B10 checks a clone3 child inside a container. |
| Q6 | Wiki QuickGrail Reference entries for both methods. | Written for `QuickGrail-Reference.md`, under Graph Methods, in the forms of §1: `getContainerInit()`, `getContainerBoundary()`, `('<id>' [, <number>])` and `($seeds)`, each with the `namespaces=true` prerequisite, and notes on the execve failure (L10) and on reused IDs (§5.5). They sit in a local clone of the wiki, which belongs to the upstream repository, so they go to the maintainers with the pull request. |
| Q7 | Kernel module: the netfilter hook could sleep in atomic context and freeze the system (§6). | Found while recording Part B (2026-11-01). Fixed: F3. |
| Q8 | Reporter: process names from kernel-module records ended in NULs (§6). | Found while recording Part B (2026-11-01). Fixed: F4. |
| Q9 | PostgreSQL storage: one value with a NUL made COPY reject its whole batch, and quotes were not escaped (§6). | Found while recording Part B (2026-11-01). Fixed: F5. |
| Q10 | Neo4j storage: QuickGrail's Cypher used `exists()`, which Neo4j 5 removed (§6). | Found while querying Part B (2026-11-01). Fixed: F6. |
| Q11 | The unshare(CLONE_NEWPID) record named no namespace, only `4294967295` (§4.1). | Found in Part B (2026-11-01): §5.2 counted the unshare as creating namespace `4294967295`, found no recorded creation of the namespace the init landed in, and merged every container that reuses an ID; on trace 1, namespace 4026532474 held 8 containers and the methods saw 1. Decided (c) (D19): F7 fixes the module, and §5.2 handles traces from older modules (L19). |

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
| `.../instruction/GetContainerInitIntegrationTest` | integration | 20 trace scenarios × 2 adjacency semantics |
| `.../instruction/GetContainerBoundaryIntegrationTest` | integration | 12 trace scenarios × 2 adjacency semantics |

### 9.2 In-memory harness

`InMemoryQueryHarness` implements the executor primitives the methods use,
over in-memory sets. Every other primitive throws, so a test that reaches an
unintended primitive fails loudly. The harness follows backend semantics
where they matter:

- **Adjacency.** It has two modes, `SOURCES_ALWAYS` (PostgreSQL, Quickstep)
  and `SOURCES_VIA_EDGES` (Neo4j). Each integration scenario runs in both,
  on traces recorded both with and without F7 (§9.3): four `@Nested` classes
  per test class.
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
- execve versions labeled with the namespaces the process was created with,
  even after an unshare or setns,
- `time` and `event id` on edges,
- `exit` self-loops, and artifacts with Used/WasGeneratedBy edges,
- optionally (`withUnnamedNewPidNamespaces`), unshare(CLONE_NEWPID) versions
  labeled `4294967295`, as modules without F7 record them; children still go
  to the new namespace.

It also builds `docker run` and `docker exec` as runc performs them. For
`docker run`, stage 1 unshares and then forks stage 2 with
CLONE_PARENT|SIGCHLD. Stage 2 unshares its cgroup namespace, starts Go
runtime threads, and calls execve. The cgroup step is what older runc
versions do; runc 1.3.3 creates the cgroup namespace before stage 2, so its
init has no such version (`TRACE_VALIDATION.md`, Part B). The methods don't
depend on it either way. setuid and setgid versions are not modeled; real
traces have dozens per init (§4.1). For `docker exec`, stage 1 calls setns
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
| unshare whose namespace never gets a process | No start. |
| second unshare before any child | Only the second namespace starts; the first dies empty. |
| process returns to earlier labels (vertex reuse) | Two starts, then a setns back into the first container is an entry. Without F7 both unshares are labeled `4294967295`, so they share a vertex; still two starts. |
| reused ID and host pids | Two separate starts. |
| container started before tracing; unshare before tracing | Empty result, no error (L1, L2). |
| unshare by a process with `-1` labels | Entry, not start (L3). |
| mount namespace unshare on the host | Empty result. |
| empty graph | Empty result. |
| init never calls execve | RuntimeException with count, host pid and namespace. |
| reused ID and pids, second init never calls execve | Still fails: the first container's execve doesn't hide it. |
| bubblewrap sandbox (reaper as PID 1, app started by its child) | Fails and names the reaper (Q3 (a), L10). |
| `systemd-nspawn --as-pid2` (stub init as PID 1, command as PID 2) | Fails and names the stub init (Q3 (a), L10). |

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
| processes labeled `4294967295` | Never container processes. |

### 9.5 Checking the tests themselves

During development, ten deliberate bugs were planted one at a time, and each
made at least one test fail under both adjacency semantics.
`TEST_RESULTS.md` lists them. The classes for traces without F7 were checked
against the analysis before it handled them: there, 6 scenarios failed under
each adjacency semantics (4 for `getContainerBoundary`, 2 for
`getContainerInit`), and none failed in the other classes.

### 9.6 Not covered

- **Real backends.** The harness mirrors PostgreSQL, Neo4j and Quickstep
  semantics. The methods have run on PostgreSQL and Neo4j with real traces
  (`TRACE_VALIDATION.md`, Part B), and both returned the same results, but
  never on Quickstep.
- **Real traces.** See Q1 in §8.
- **Performance and concurrency.**
