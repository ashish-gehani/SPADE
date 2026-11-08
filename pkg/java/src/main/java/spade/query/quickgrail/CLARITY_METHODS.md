# Container Labels: Design Notes

`ContainerLabels` is a SPADE transformer for the container patterns of CLARION
(Chen et al., *CLARION: Sound and Clear Provenance Tracking for Microservice
Deployments*, USENIX Security 2021), §4.2. It labels each container's
processes in a provenance graph, and the network sockets and System V IPC
objects that only they used, with a `container_id`. Its requirements are the
SPADE maintainer's specification (2026-11-03), cited below as "the
specification". This file records how it works, its decisions, the facts it
relies on, and its limits. User documentation is on the wiki's
[Available transformers](https://github.com/ashish-gehani/SPADE/wiki/Available-transformers)
page.

Branch `clarity-of-container` holds the transformer, on top of branch
`bug-fixes`, which starts from upstream `prov-query` and holds the fixes the
work needed in the kernel module, the audit bridge, the Audit reporter, the
SPADE kernel, QuickGrail and the storages. `BUG_FIXES.md` describes them (F1
to F29) and says which ones container labeling needs; this file cites them by
number. `TRACE_VALIDATION.md` checks the kernel module's namespace records and
the transformer on real traces.

Code, relative to `pkg/java/src/main/java/spade/`:

| File | Role |
|---|---|
| `transformer/ContainerLabels.java` | The transformer |
| `reporter/audit/LinuxConstants.java` | `getProcPidInitIno()`: the host's PID namespace ID |
| `query/quickgrail/core/QueryInstructionExecutor.java` (`transformGraph`) | Runs a transformer for QuickGrail's `transform` |

## 1. What the transformer returns

```
$labeled = $base.transform(ContainerLabels, '')
$c       = $labeled.getVertex(container_id = '4026532270:1700000000.123:7012')
$all     = $labeled.getVertex(container_id like '%')
```

`$labeled` is a copy of the input graph in which these vertices also have a
`container_id`:

- every version of every process positively identified as a container's
  process (§5.4): its init, the processes created in the container's PID
  namespace from the init, and the processes created into that namespace by a
  process outside it during the container's lifetime (e.g. `docker exec`);
- network sockets with a `net namespace`, and System V message queues and
  shared memory with an `ipc namespace`, when every process connected to them
  has the same `container_id` and the artifact's namespace (§5.5).

Nothing else is labeled: host processes (the container runtime among them),
processes of containers whose start is not in the input, processes not linked
to a start, files and other artifacts.

The value is the container's PID namespace ID, then the `time` and `event id`
of the edge that created its first process (its init), e.g.
`4026532270:1700000000.123:7012`. It is the same for the same trace whatever
subgraph holds that edge, on every storage, and it leads to the start:
`$labeled.getEdge("event id" = '7012')`.

The two CLARION patterns, from the labels:

- **§4.2.1, the boundary of a container**: its labeled processes, the
  artifacts they used, and the edges between them, e.g.
  `$labeled.getLineage($c, 1, 'both')`.
- **§4.2.2, the initialization of a container**: from the start edge, the
  init's versions up to its first execve, and the unshare or setns steps of
  the process that created it.

In QuickGrail, `transform` puts the copy into the storage (§4.1). A labeled
vertex is a new vertex, since SPADE derives a vertex's hash from its
annotations, so `$base` then holds both the original and the labeled copy.
Transforming it again gives the same labels and adds nothing (D11).

The transformer needs the Audit reporter with `namespaces=true`, which
requires the kernel module (§6). Without namespace labels, nothing is labeled.

A `docker run`, in time order:

```
stage 1 of runc init                               PID namespace: host          not labeled
  └ unshare → stage 1                              host; its children go to X   not labeled
      └ fork (CLONE_PARENT|SIGCHLD) → stage 2      X: the init, the start       labeled
          ├ clone (runc's Go threads)              X                            labeled
          └ execve → nginx                         X: the application           labeled
              └ fork → nginx worker, ...           X                            labeled
```

The reporter names a new process after its creator, so stage 1 appears as
`runc:[0:PARENT]` and the init as `runc:[1:CHILD]` until its execve.

## 2. Paper anchoring

| Paper | What it says | What the transformer does |
|---|---|---|
| §4.2.1 Boundary of Containers | A container at runtime is the set of processes in one PID namespace. An artifact belongs to a container if a process inside it used the artifact. | Labels the processes of each PID namespace, one container per lifetime of a reused ID, and the namespaced artifacts only its processes used. |
| §4.2.2 Initialization of Containers | Initialization starts with `unshare` or `clone` with new-namespace flags and ends with the `execve` that launches the application. | Finds each start from recorded namespace changes and clone flags and names the container after it. |
| §5.2 Cross-container evaluation | Per-container figures (Figs. 14–16) and measurements across multi-container traces (Tables 7–9). | One label per container: select one, or all of them. |

## 3. Decisions

| # | Topic | Decision | Reason |
|---|---|---|---|
| D1 | Form | A SPADE transformer that works on the input graph in memory and returns a labeled copy. | The specification: no repeated storage queries, and the Audit reporter's interpretation stays out of QuickGrail, which is data-model agnostic. One implementation serves every storage. |
| D2 | Linking processes | Link processes only through WasTriggeredBy creation and version edges, never by equal `pid`, `pid namespace` or `ns pid` values. | The kernel reuses pids and namespace IDs within a boot. `ns pid` is wrong in traces from kernel modules without F1 (§4.1). |
| D3 | Reused IDs | A container is a PID namespace ID from one recorded start to the next recorded start of that ID. | IDs are reused once a namespace is freed (§4.2). The specification: tell reused IDs apart by recorded creation events and their times. |
| D4 | Host namespace | `PROC_PID_INIT_INO` (4026531836) from the Audit reporter's Linux constants file; `hostPidNamespace=<id>` overrides it. | The kernel fixes this value for the root PID namespace. |
| D5 | Positive identification | Label a process version only when it is in the container's PID namespace, its `start time` (or `seen time`) is in the container's lifetime, and it descends through creation and version edges inside that namespace from the init or from a process created into the namespace during the lifetime. | The specification: label only processes positively identified; the input may lack a process's history, and including it is the analyst's part. The edges show the process came from the start; namespace and time keep a reused ID's lifetimes apart. |
| D6 | Containers started before tracing | Not labeled, nor what is created into them. | Their start is not recorded, so nothing names them, and in a subgraph their processes could belong to several lifetimes of the ID. |
| D7 | Nested containers | A process gets its innermost container's label. | One annotation holds one value. The nesting stays in the graph: the nested start edge comes from a process of the outer container. |
| D8 | The label | `container_id` = `<pid namespace>:<time>:<event id>` of the init's creation edge. | The specification asks for a deterministic `container_id`. The start event names a lifetime uniquely within a trace, does not depend on the rest of the input, and leads back to the start. |
| D9 | What is labeled | Process vertices, every version, and the artifacts of D10. | The specification. A file's container cannot be told from its annotations; a boundary query finds the files a container used. |
| D10 | Artifacts | A `network socket` with a `net namespace`, or a `system v message queue` or `system v shared memory` with an `ipc namespace`, is labeled when at least one process is connected to it and every connected process has the same label and the artifact's namespace. | The specification: annotations and connections must tie the artifact to one container; a matching namespace alone is not enough; shared or ambiguous artifacts stay unchanged. |
| D11 | Output | A copy of the whole input with `container_id` added. Unlabeled vertices, and edges between them, keep their hashes; a labeled vertex is new, with the same annotations plus the label, and so is an edge to it. Vertices that already have a `container_id` are copied as they are and not used to identify containers. | The specification. The last rule makes a second transform of `$base`, which holds the first one's labeled copies, give the same labels and add nothing. |
| D12 | Inits that never call execve | Labeled like any container (bubblewrap's reaper, `systemd-nspawn --as-pid2`'s stub init). | A container is its PID namespace; where initialization ends does not matter for labels. |
| D13 | Errors | The transformer fails only before `initialize` or without a host namespace ID. Each run logs a summary: containers found, versions labeled, versions in a container namespace left unlabeled, artifacts labeled, and artifacts next to a labeled process left as shared or ambiguous. | Nothing in a graph is an error for labeling; what was left is reported. |
| D14 | Edges followed | Creation: `clone`, `fork`, `vfork`. Versions: `execve`, `unshare`, `setns`, `setuid`, `setreuid`, `setresuid`, `setfsuid`, `setgid`, `setregid`, `setresgid`, `setfsgid`, `update`, `unit`. Not `kill`, `ptrace`, `exit` or `unknown`. | Only creation and versions continue a process's lineage. |
| D15 | Older traces | Handle the unnamed new PID namespace of kernel modules without F7 (§5.2) and the labels reverted at execve by reporters without F17. Every test scenario runs on traces recorded each way. | Decided by the user (2026-11-02): traces from today's upstream module and reporter stay usable. |

## 4. Facts the design relies on

### 4.1 Audit reporter, kernel module and QuickGrail

Verified in the source; the rows on `ns pid`, the unshare and setns hooks,
`clone3` and the `children pid namespace` after unshare also on a live system
(`TRACE_VALIDATION.md`, Part A). Java classes are under
`pkg/java/src/main/java/spade/`, the kernel module under
`pkg/linux/kernel_modules/audit/`.

| Fact | Where |
|---|---|
| Process vertices carry `pid namespace`, `children pid namespace`, `mount namespace`, `net namespace`, `ipc namespace` and the other namespace labels only when `namespaces=true` (default `false`). | `cfg/spade.reporter.Audit.config`, `reporter/audit/process/NamespaceIdentifier.java` |
| For clone/fork/vfork/clone3, the kernel module reports the child's namespaces. clone3 is recorded like clone (F9). | `kernel/helper/namespace.c`, `function/sys_{clone,clone3,fork,vfork}/` |
| `ns pid` is the PID inside the process's own PID namespace (confirmed by the CLARION author). Kernel modules without F1 recorded the PID in the creator's namespace for a namespace's first process and for processes started into a namespace from outside. Not used (D2). | `kernel/helper/namespace.c`, `ProcessManager.handleForkVforkClone`, `BUG_FIXES.md` F1 |
| An unshare or setns inside a container writes a namespace record; modules without F2 wrote none. | `function/sys_unshare/`, `function/sys_setns/`, `BUG_FIXES.md` F2 |
| A process not created in the trace has `-1` for every namespace until it calls unshare or setns. | `ProcessStateManager`, `ProcessManager.buildNamespaceIdentifierForPid` |
| A process created in the trace carries `start time` (its clone or execve); one first seen in another syscall carries `seen time`. execve creates a new version with a new `start time`. | `ProcessManager.handleForkVforkClone`, `handleExecve` |
| clone with SIGCHLD is recorded as `fork`; with CLONE_VM and CLONE_VFORK too, as `vfork`, shown as `fork` when `simplify=true` (the default). The flags are in the edge's `flags`. | `ProcessManager.handleForkVforkClone`, `LinuxConstants.stringifyCloneFlags` |
| unshare and setns create a new version, joined by a WasTriggeredBy edge from new to old, with the same name and time and new namespace labels. A process returning to labels it had before reuses the earlier vertex, so version edges can form cycles. | `ProcessWithoutAgentManager.handleNamespaceUpdate`, `ProcessUnitState` |
| The version after unshare(CLONE_NEWPID) has the new namespace as its `children pid namespace`. Modules without F7 wrote `4294967295` instead, since the namespace has no process yet; with F7 an unreadable namespace is `-1`. Both count as unobserved (§5.2). | `kernel/helper/namespace.c`, `BUG_FIXES.md` F7 |
| With the default `agents=false`, setuid and setgid produce a WasTriggeredBy version (a self-loop when the IDs don't change); runc's init makes dozens. With `agents=true` they produce WasControlledBy edges to Agent vertices instead. | `Audit` (manager choice), `ProcessWithAgentManager`, `ProcessWithoutAgentManager` |
| After unshare or setns, a later execve or setuid/setgid keeps that step's namespaces (F17). Reporters without F17 relabeled the process with the namespaces it was created with. | `ProcessManager.handleNamespaceUpdateFromSyscall`, `handleExecve`, `BUG_FIXES.md` F17 |
| A vfork child's own events reach the reporter before the vfork that creates it; the reporter holds them until the creation is handled (F28). | `VforkOrdering`, `BUG_FIXES.md` F28 |
| Edges carry `time` as seconds with three-digit milliseconds (e.g. `1700000000.123`) and an `event id`. An exit is a WasTriggeredBy edge from a process to itself. | `Audit.putEdge`, `ProcessManager.handleExit` |
| A `network socket` carries the `net namespace` of the process whose syscall named it; a `system v message queue` or `system v shared memory` the `ipc namespace` of the process operating on it. | `Audit.getNetworkNamespaceForPid`, `NetworkSocketIdentifier`, `IPCManager`, `SystemVArtifactIdentifier` |
| Annotation values are strings, in a graph and in every storage. | `core/AbstractVertex.java`, `storage/postgresql/` |
| QuickGrail's `$out = $g.transform(Name, 'args')` initializes the transformer with the argument string, exports all of `$g`, runs the transformer, and puts the result into the storage, adding the vertices and edges it does not have; `$out` holds the result. | `QuickGrailQueryResolver.resolveTransformGraph`, `QueryInstructionExecutor.transformGraph`, `putGraph` |

### 4.2 Linux kernel rules

- A new process goes into its parent's PID namespace for children
  (`pid_ns_for_children`). clone(CLONE_NEWPID) instead creates a new
  namespace nested in the parent's.
- A process's `pid_ns_for_children` differs from its own namespace only after
  unshare(CLONE_NEWPID) or setns(CLONE_NEWPID). While they differ,
  unshare(CLONE_NEWPID) and clone(CLONE_NEWPID) fail (`copy_pid_ns`); setns
  can only enter the process's own PID namespace or one nested in it
  (`pidns_install`).
- The first process created in a new PID namespace becomes its init (pid 1).
  When the init exits, every process in the namespace is killed, nested
  namespaces included (`zap_pid_ns_processes`), and no new process can enter
  (`alloc_pid` fails).
- A thread can't be created into another PID namespace (CLONE_THREAD fails
  when `pid_ns_for_children` differs), so every crossing into a namespace is
  a new process.
- Namespace IDs are proc inode numbers. The root PID namespace is
  `PROC_PID_INIT_INO` = 0xEFFFFFFC = 4026531836. Others take the lowest free
  number from 0xF0000000 (`proc_alloc_inum`), reused once the namespace is
  freed. A namespace is held by a process in it, a process with it as
  `pid_ns_for_children`, an open file descriptor, or a bind mount.

### 4.3 Assumptions

- A trace covers one boot, and the clock is not set backwards.
- Audit records are not lost (§7, L6).
- `time`, `start time` and `seen time` keep the reporter's format (L8).
- The input holds every recorded start of a namespace ID before the processes
  that entered that namespace (L17). `$base` of a complete trace does.

## 5. How it works

### 5.1 Indexing

The input is read once. Vertices are indexed by hash, edge endpoints missing
from the vertex set included. WasTriggeredBy edges between two processes
without a `container_id` are indexed by their parent (the creator or the old
version) as creation edges, version edges and namespace steps (`unshare`,
`setns`), by the operations of D14. Everything after this runs in memory.

### 5.2 Finding container starts

A crossing is a creation edge whose child is in a container PID namespace
other than its parent's. By §4.2 such children come only from a process
after it changed its `children pid namespace`, or from a clone flagged
CLONE_NEWPID.

1. **Classify namespace steps.** A step counts if the new version's `pid
   namespace` and `children pid namespace` are observed (not `-1` or
   `4294967295`) and `children pid namespace` changed. It is a **restorer**
   if the children go back to the process's own namespace; a **creation** if
   it is an unshare from an observed version (record the new ID, the caller's
   host pid, time and event id); otherwise a **join** (a setns, or an unshare
   from a `-1` version, which may have been for another namespace type after
   a PID unshare before tracing). An unshare recorded by a module without F7,
   whose new `children pid namespace` is `4294967295`, is a creation with the
   ID still unknown if the old version was observed, else a join.
2. **Versions after each change.** Follow version edges forward from the
   steps, stopping at a restorer or a change of the other kind. A vertex that
   is both an unshare result and a setns result (vertex reuse) is followed
   from both.
3. **Candidates.** The creation edges out of those versions, and every
   creation edge whose `flags` contain CLONE_NEWPID, whose child is in a
   container namespace.
4. **Unnamed creations.** A creation without an ID takes the namespace of
   the first candidate child its caller (matched by host pid) created after
   it and before the caller's next counting step; by §4.2 that child is the
   new namespace's init.
5. **Classify.** A CLONE_NEWPID clone is a **start**. A child of a version
   after an unshare is grouped under the latest recorded creation of its
   namespace ID before it, in event order (time, then event id); the first
   child of each group is a **start**, the rest **enter**. A child of a
   version after a join **enters**.

Grouping by ID and time is exact: the creator holds the namespace while its
children go there, so no other creation of that ID comes between.

### 5.3 Containers over time

For a PID namespace ID X with starts s1, s2, … in event order, container k is
X from time(s_k) up to, not including, time(s_{k+1}), with `container_id`
X:time(s_k):eventid(s_k). By §4.2 the lifetimes never overlap.

### 5.4 Labeling processes

For each container, the seeds are its init and the processes that entered
its namespace during its lifetime (§5.2, by the entering edge's time). From
the seeds, follow creation and version edges forward, keeping a vertex only if
it is a process without a `container_id`, in the container's PID namespace,
whose `start time` (or `seen time`) is in the lifetime. Every vertex kept is
labeled. A version claimed by two containers, which §4.2 rules out, is left
unlabeled and counted.

This covers a container's processes because every one of them is created
after the start, by a process of the container or by one entering it, and
creation edges record both; versions keep the process's time or get a later
execve time. A nested container's processes are in another namespace, so the
outer closure stops at them and the nested container's own seeds reach them.

### 5.5 Labeling artifacts

An artifact without a `container_id` qualifies if it is a `network socket`
with a `net namespace`, or a `system v message queue` or `system v shared
memory` with an `ipc namespace`. It is labeled when at least one process is
connected to it by an edge, every connected process has the same label (from
§5.4 or an earlier run), and every connected process has the artifact's
namespace value. Otherwise it stays unchanged (D10).

### 5.6 The copy

Every input vertex is copied: unlabeled ones as they are (keeping a storage
hash), labeled ones as a new vertex with the same annotations plus
`container_id`. Every edge is copied between the copies of its endpoints with
the same annotations; an edge between unlabeled vertices keeps its hash. The
input is not changed.

### 5.7 Errors and the summary

The transformer fails only when used before `initialize`, or when
`initialize` finds no host namespace ID (no `hostPidNamespace=` and no
`PROC_PID_INIT_INO` in the constants file). QuickGrail turns that into a
failed query. Each run logs, at INFO:

```
ContainerLabels: 6 container(s) started in the graph; 167 process vertices labeled, 0 in a
container's PID namespace left unlabeled (no recorded start, or not linked to one); 165 artifacts
labeled, 0 next to a labeled process left unlabeled as shared or ambiguous
```

## 6. Configuration

- `cfg/spade.reporter.audit.LinuxConstants.config` defines
  `PROC_PID_INIT_INO = 0xEFFFFFFC`, found through `constantsSource` in
  `cfg/spade.reporter.Audit.config`. The key is optional when the file loads
  and required when the transformer initializes without `hostPidNamespace=`.
- Record traces with `cfg/spade.reporter.Audit-with-namespace-awareness.config`:
  the default Audit config with `namespaces=true`, and `fileIO`, `netIO`,
  `IPC`, `inode` and `networkAddressTranslation` on. With the kernel module,
  `netIO` gates the module's network I/O records: with `netIO=false` a
  connection is still recorded through `connect` and `close`, but not its
  `send`, `recv`, `read` or `write`, nor unconnected UDP. `IPC` records the
  System V objects.
- `networkAddressTranslation=true` turns on the kernel module's netfilter hook;
  use a module with F3, without which the hook can freeze the system.
- `BUG_FIXES.md` lists the fixes container labeling needs, among them F5, F6
  and F23 for the PostgreSQL, Neo4j and Quickstep storages.

## 7. Limitations

| # | Limitation | Effect | Workaround |
|---|---|---|---|
| L1 | Starts before tracing are not recorded. | Those containers, the ones nested in them before tracing, and what is created into them are not labeled (D6). | Start tracing before the containers. |
| L2 | PID namespace changes before tracing are not recorded. | A process that called unshare(CLONE_NEWPID) before tracing and forks during it is not seen starting a container, which is then not labeled. | None. |
| L3 | An unshare by a process whose namespaces were never observed (`-1`) is taken as a join. | If it created a PID namespace, that container is not labeled. Such processes are those from before tracing and those whose creation record was lost (L6). | None; the alternative would invent starts. |
| L4 | Traces without F9, or with `namespaces=false`. | Without F9, a process created with clone3 has no creation edge, so it and what it creates are not labeled, and a container whose init is created so has no start. With `namespaces=false`, nothing is labeled. | Record traces with F9 and `namespaces=true`. |
| L5 | Traces from reporters without F17, with `agents=true`. | A setuid/setgid after an unshare makes no version edge, so later children seem to come from the earlier vertex and that start or entry is missed. With the default `agents=false`, labels are unaffected (D15). | Record traces with F17. |
| L6 | Lost or incomplete audit records. | A missing creation edge leaves its process and everything after it unlabeled; a missing start leaves its container unlabeled; a missing later start of a reused ID gives the processes entering after it the earlier container's label. | None. |
| L7 | Millisecond time granularity. | A process created in the same millisecond as the next start of its ID would get the next container's label; practically impossible, since the earlier container must be gone. | None needed. |
| L8 | Times compare as strings. | Correct while seconds have ten digits (until 2286). A filter that rewrites times (e.g. `spade.filter.ConvertTime`) breaks it. | Don't rewrite time annotations before storage. |
| L9 | "Host" is the root PID namespace. | On a system itself inside a PID namespace (a system container), its own processes stay unlabeled, and its containers are labeled only if their starts are in the trace. | `hostPidNamespace=<its namespace>`. |
| L10 | Pods with a shared PID namespace (`shareProcessNamespace: true`). | The other containers join the pause container's namespace, so the pod has one label. | None; that is what the kernel sees. |
| L11 | Only PID namespaces define containers. | Other namespaces only qualify artifacts (D10). | Select by those labels with `getVertex`. |
| L12 | One label per process (D7). | A nested container's processes are not labeled with the outer container. | Follow the nested start edge to its creator, which has the outer label. |
| L13 | Labels follow the input's lineage edges (D5). | An input missing WasTriggeredBy edges or their endpoints gets fewer labels. | Transform `$base`, or a graph that keeps process lineage. |
| L14 | The whole input is held in memory, twice while copying; QuickGrail exports it from the storage and puts the copy back. | Time and memory grow with the input (L15). | Transform a subgraph that keeps the containers' process lineage. |
| L15 | Time. | PERF_LINE | |
| L16 | runc opens some files relative to descriptors from syscalls the audit rules leave out (`BUG_FIXES.md` N21). | Those files are missing from the graph, so a boundary query misses them. Labels are not affected. | None in the transformer. |
| L17 | A process entering a namespace is placed by time (§5.4). | If the input lacks a later start of the ID, a process entering after it gets the earlier container's label. `$base` of a complete trace has every start. | Include every start of the ID up to the processes of interest. |
| L18 | Sockets are judged by every process connected to them in the input (D10). | SOCKET_LINE | |

## 8. Status

- Synthetic traces: `ContainerLabelsTest` (§9) passes, on Windows and Linux.
- Real traces: `TRACE_VALIDATION.md` Part C, on traces 1, 2 and 4 replayed
  with this build into PostgreSQL, Neo4j and Quickstep.
- The wiki entry (`Available-transformers.md`) is in a local clone of the
  upstream wiki and goes to the maintainers with the pull request.

## 9. Test design

Tests are in `pkg/java/src/test/java/spade/`, run with `mvn test` in
`pkg/java`. Results are in `transformer/TEST_RESULTS.md`.

| Class | What it covers |
|---|---|
| `reporter/audit/LinuxConstantsTest` | `PROC_PID_INIT_INO` is read from the shipped constants file; optional when loading, required when requested |
| `transformer/ContainerLabelsTest` | The contract: found by name, reads the host ID from the real constants file, fails before `initialize`, resolves from QuickGrail's `transform`, handles `vfork` (simplify=false) |
| `transformer/ContainerLabelsTest` (four `@Nested` classes) | 43 scenarios, each on traces recorded four ways: today's module and reporter, a module without F7, a reporter without F17, and both |

`ContainerTrace` writes a `spade.core.Graph` as the reporter records
provenance (§4.1): `start time` and `seen time`; `-1` labels for processes from
before tracing; clone as `fork` (or `vfork`) with its `flags`; unshare/setns
versions that keep the time and reuse vertices when labels repeat (hashes come
from annotations, as in SPADE); execve with F17's labels or, optionally, the
labels reverted; optionally the `4294967295` of modules without F7; `time`
and `event id` on edges; `exit` and `kill` edges; mount, cgroup, net and ipc
namespaces; files, network sockets and System V objects with their Used and
WasGeneratedBy edges. It builds `docker run` and `docker exec` as runc does:
stage 1 unshares (or, for exec, joins with setns) and forks the process that
runs the command.

Each scenario compares the whole set of labels with the expected one, so an
extra label fails it too:

| Scenarios | Expected |
|---|---|
| docker run; the ID's format | The init and everything after it labeled, runc and the shim not; the ID is the namespace, time and event id of the init's creation |
| docker exec into a running container | The exec'd processes get the container's ID; the entering runc process does not |
| clone(CLONE_NEWPID) with and without SIGCHLD; `unshare --pid bash`; a creator that joins another namespace first | The container from its start; the creator not |
| an unshare with no child; a second unshare before any child; later children of one unshare in the same millisecond; a process returning to earlier labels | Only real starts; later children and a setns back join the right container |
| an unshare before tracing; an unshare by a `-1` process; a mount unshare on the host; an empty graph | Nothing |
| an init that never calls execve; bubblewrap; `systemd-nspawn --as-pid2` | Labeled like any container |
| a container from before tracing, and an exec into it; a process first seen inside a container; a subgraph without a creation edge, without the start, or without edges; `4294967295` labels; kill edges to the host | Not labeled (D5, D6, D14) |
| a reused ID and reused pids, with execs into each run | Each run its own ID |
| nesting three levels deep with a sibling; a nested container under a reused outer ID | Each process its innermost container's ID |
| sockets and System V objects: of one container; without a namespace; in another namespace; also used by a host process in the namespace; shared by two containers; of a container from before tracing | Labeled only in the first case (D10) |
| the copy; transformed twice; transformed again with its own labeled copy (QuickGrail's `$base`) | Every vertex, edge and annotation kept, unlabeled hashes kept, input unchanged; the same output; nothing new |

To check the tests themselves, deliberate bugs were planted in the transformer
one at a time (`TEST_RESULTS.md`).
