# Fixes Outside the Container Methods

The QuickGrail container methods, `getContainerInit` and
`getContainerBoundary` (branch `clarity-of-container`, `CLARITY_METHODS.md`
beside this file), were checked against the audit kernel module and against
real traces. That turned up bugs in the kernel module, the audit bridge, the
Audit reporter and all three storages. Branch `bug-fixes` holds their fixes, on top of upstream
`prov-query` (`6b744f7c`), and `clarity-of-container` is rebased onto it. This
file describes every fix on the branch: what went wrong, why, what changed,
how it was checked, and what it means for traces recorded before it.

Paths are relative to the repository root. The kernel module is
`pkg/linux/kernel_modules/audit/`, and Java classes are under
`pkg/java/src/main/java/spade/`.

## Summary

| # | Component | Bug | Commits | Checked by |
|---|---|---|---|---|
| F1 | kernel module | `ns_ns_pid` held the PID in the creator's namespace, not the process's own | `9fd54e0c` | `test/ns-pid` A/B on a live system |
| F2 | kernel module | an unshare or setns inside a container wrote no record | `d445c7cc` | `test/ns-pid` A/B |
| F3 | kernel module | the netfilter hook could sleep in atomic context and freeze the system | `da9c05b8`, `1527278b` | `test/netfilter-atomic` reproduction |
| F4 | Audit reporter | process names from kernel-module records ended in NULs | `ed373f13` | unit tests; a real trace |
| F5 | PostgreSQL storage | one NUL made COPY drop a whole batch; quotes were not escaped | `ff827871` | unit tests; a real trace |
| F6 | Neo4j storage | QuickGrail's Cypher used `exists()`, which Neo4j 5 removed | `d36e055a` | a real trace on both storages |
| F7 | kernel module | an unshare(CLONE_NEWPID) record never named the new namespace | `2728115f`, `5c8c1db2`, `0cdc679a` | in-kernel unit test; `test/ns-pid` A/B |
| F8 | audit bridge | auditd's af_unix plugin silently dropped events whenever SPADE fell behind | `b457bb46`, `41afaf09` | a real trace: auditd's log against SPADE's input |
| F9 | kernel module, Audit reporter | processes and threads created with clone3 had no creation record | `cea5d23c`, `e09693b6`, `1b55e895` | in-kernel unit tests; `test/ns-pid/clone3-test.sh`; unit tests |
| F10 | Audit reporter | `netfilterHooksLogCT=false` logged every packet instead of only new connections | `3bbb7352` | unit test |
| F11 | Audit reporter | a thread's exit ended its whole process's state, so multi-threaded processes split into thousands of unlabeled versions | `d997414e` | unit test; trace 3 replayed |
| F12 | Quickstep storage | every new vertex cost a query that scanned the whole vertex table | `d0d1cf7e` | trace 3 replayed |
| F13 | Audit reporter | settings and bridge messages that mean lost records went unnoticed | `c3b8eaa0` | unit test |
| F14 | Audit reporter | the netfilter hooks' tables had no indexes, so every packet record scanned them | `583ec2d7` | trace 3 replayed |

`test/...` is `pkg/linux/kernel_modules/test/...`. All checks ran on Ubuntu
24.04.3 with Linux 6.8.0-146-generic in VirtualBox, with Docker 29.0.0 and runc
1.3.3.

## F1. `ns_ns_pid` in the process's own PID namespace

**Symptom.** `ns_ns_pid`, which the reporter stores as `ns pid`, is meant to
be the process's PID inside its own PID namespace. For a namespace's first
process it should be 1. A container init got its host PID instead, for
example 17552.

**Cause.** For clone, fork and vfork, the module recorded the syscall's return
value (`msg->ns_pid = target_pid`). That is the child's PID as its creator
sees it: the host PID when the creator is on the host, and in general the PID
in the creator's namespace. It is right only when creator and child share a
namespace.

**Fix.** `kernel_helper_namespace_populate_msg` records
`pid_nr_ns(pid, ns_of_pid(pid))`, the PID in the namespace the process lives
in.

**Checked.** `test/ns-pid`, base `6b744f7c` against fixed: 25 of 2211
namespace records wrong without the fix (18 container inits, 4 `sh`,
3 `sleep`), 0 of 2237 with it. A namespace created from inside another one
showed that the old value is the creator's view, not the host PID: an inner
init got 4, its PID in the outer namespace. Results are in
`test/ns-pid/README.md`.

**Older traces.** `ns pid` keeps the creator's view for a namespace's first
process, a child of an unshare, and a process started into a container from
outside (`docker exec`). The container methods don't use `ns pid`.

**Files.** `audit/kernel/helper/namespace.c`.

## F2. unshare and setns callers looked up in their own namespace

**Symptom.** An unshare or setns called from inside a non-host PID namespace,
for example `unshare` inside a container, wrote no namespace record.

**Cause.** The hooks looked up the caller by `current->pid`, its host PID, in
the caller's own PID namespace. Inside a container that finds no process, so
no record is written, or in principle a different process, whose namespaces
would be reported.

**Fix.** The hooks pass the caller's PID in its own namespace
(`kernel_helper_task_ns_view_current_pid()`, `task_pid_vnr(current)`).
`kernel_helper_task_task_view_current_pid()` still returns the host PID: the
`pids` and `ppids` filters compare host PIDs.

**Checked.** `test/ns-pid`: without the fix, the inner `unshare` and
`nsenter` of the scenario that runs them inside a namespace wrote no record;
with it, each wrote one with its own-namespace PID. `filter-test.sh` showed
that both builds' filters match host PIDs and never in-namespace PIDs.

**Older traces.** A process that unshares or calls setns inside a container
keeps its old namespace labels, so a container started inside a container
(Docker in Docker, B4 in the trace checks) is not seen.

**Files.** `audit/kernel/function/sys_unshare/action/audit.c`,
`audit/kernel/function/sys_setns/action/audit.c`, `audit/kernel/helper/task.c`,
`audit/kernel/helper/task.h`.

## F3. The netfilter hook must not sleep

**Symptom.** With `networkAddressTranslation=true` (as in
`cfg/spade.reporter.Audit-with-namespace-awareness.config`), recording a trace
froze the VM. The serial console showed `BUG: scheduling while atomic` in
`nf_handle_packet`, then soft lockups and an RCU stall. The process
interrupted was PostgreSQL, SPADE's storage, whose loopback traffic went
through the hook.

**Cause.** `networkAddressTranslation=true` turns on the module's netfilter
hook (`nf_hooks`), which logs packets with `kernel_helper_audit_log`.
That passed `GFP_KERNEL` to `audit_log`, which waits for room while the audit
backlog is full. A netfilter hook runs in an RCU read-side section and, for
received packets, in softirq, where it must never sleep.

**Fix.** `kernel_helper_audit_log_atomic` passes `GFP_ATOMIC`, and the
netfilter hook uses it. With a full backlog, the kernel now drops the packet
record and counts it in the audit `lost` counter. The syscall hooks run in
process context and keep the waiting variant.

**Checked.** `test/netfilter-atomic/nf-repro.sh` fills the backlog (limit 64,
auditd stopped) and sends loopback TCP traffic. Without the fix the guest
froze within a second, with 5 `BUG` reports. With it, 15 connections lost 908
records and 10 s of traffic lost 235,690, with no BUG, warning or lockup. The
script was run as committed. Results are in `test/netfilter-atomic/README.md`.

**Older traces.** None are affected: without the fix, the system froze
instead.

**Files.** `audit/kernel/helper/audit_log.c`, `audit/kernel/helper/audit_log.h`,
`audit/kernel/netfilter/netfilter.c`.

## F4. Process names cut at the first NUL

**Symptom.** Process names from kernel-module records carried trailing NULs:
`gdbus` arrived as `gdbus` followed by 11 NUL bytes. Such a vertex differed
from the same process's vertex built from a syscall record, and PostgreSQL
rejected it (F5). In one trace, 13,264 vertices were lost that way.

**Cause.** The module hex-encodes the whole `comm` buffer, all
`TASK_COMM_LEN` (16) bytes, so every name from its records ends in at least
one NUL. `AuditEventReader` decoded the hex as is.

**Fix.** The reader cuts hex-encoded values at the first NUL
(`AuditEventReader.decodeHexValue`). The values decoded there (`comm`, `cwd`,
`name`, execve arguments) are C strings, so a NUL always ends them, and the
kernel's own hex encoding never contains one.

**Checked.** `AuditEventReaderTest` (5 tests: padded, full-length and
all-NUL names, kernel hex without NUL, invalid hex). With F4 and F5, a real
trace stored 21,164 vertices in PostgreSQL with no failed batch.

**Older traces.** Audit logs recorded before the fix are read correctly,
since the fix is in the reader. Graphs stored before it have names with NULs
in Neo4j, and miss whole vertex batches in PostgreSQL.

**Files.** `reporter/audit/AuditEventReader.java`; test
`pkg/java/src/test/java/spade/reporter/audit/AuditEventReaderTest.java`.

## F5. PostgreSQL COPY values escaped

**Symptom.** PostgreSQL silently lost whole batches of vertices: the log
showed `Failed to persist N vertices` with `invalid byte sequence for encoding
"UTF8": 0x00`. In one trace, 13,211 of the 31,429 vertices that edges point
to were missing.

**Cause.** `PostgreSQL.persist` loads vertices and edges in batches with COPY
in CSV format, and `formatToCSV` quotes each value. PostgreSQL text cannot
hold NUL, so a single value with a NUL (F4) failed the whole batch. Also,
`formatToCSV` meant to escape quotes with `item.replace('"', '\"')`, but
`'\"'` is the same character in Java, so the call changed nothing. CSV needs
the quote doubled; an undoubled one ends the field early. No batch in the
trace failed on a quote; that bug was found by reading the code.

**Fix.** `formatToCSV` drops NULs and doubles quotes.

**Checked.** `PostgreSQLTest` (6 tests: NULL, empty string, plain value,
quotes, delimiter and newline, NULs). On a real trace recorded with F4 to F6,
PostgreSQL and Neo4j held the same 21,164 vertices and 58,135 edges. For all
19 container-method queries they returned the same counts and the same error
messages, and the five non-empty results compared vertex by vertex were
identical.

**Older traces.** A PostgreSQL database filled before the fix may miss whole
batches; replaying the audit log fills them in.

**Files.** `storage/PostgreSQL.java`; test
`pkg/java/src/test/java/spade/storage/PostgreSQLTest.java`.

## F6. Neo4j Cypher without `exists()`

**Symptom.** On Neo4j, every QuickGrail instruction that builds a graph
failed, even `$base.getVertex(type = 'Process')`, with "The property existence
syntax `... exists(variable.property)` is no longer supported". The container
methods returned empty graphs.

**Cause.** SPADE bundles Neo4j 5.26 (`pkg/java/pom.xml`), and Neo4j 5 removed
`exists(variable.property)`. The executor uses it when it records which graphs
an edge belongs to, a step of every graph-building instruction, and in
`getWhereAnnotationsExist`, the vertex and edge cases of `describeGraph`, and
`saveRemoteSymbol`.

**Fix.** `x IS NULL` and `x IS NOT NULL` replace `not exists(x)` and
`exists(x)` at those five places. They work in Neo4j 3, 4 and 5.

**Checked.** On the real trace of F5, Neo4j and PostgreSQL returned the same
results.

**Older traces.** None are affected: the fix is in the queries, so Neo4j
databases filled before it can be queried now.

**Files.** `storage/neo4j/Neo4jInstructionExecutor.java`,
`storage/neo4j/Neo4jQueryEnvironment.java`.

## F7. The unshare record names the new PID namespace

**Symptom.** After unshare(CLONE_NEWPID), the record's `ns_inum_pid_children`
was `4294967295` instead of the new namespace's inode number, for every
container runc started and every `unshare --pid`. The reporter stored it as
the version's `children pid namespace`. The same value appeared on the
`NEWPROCESS` records of children that had already exited.

**Cause.** Two things. The module read the children's namespace through
`pidns_for_children_operations`, whose `get()` (`pidns_for_children_get` in
Linux `kernel/pid_namespace.c`) returns no namespace while the namespace has
no child reaper, that is, until its first process exists. So right after
unshare(CLONE_NEWPID), it hid exactly the namespace just created, and the
module recorded -1. Second, the namespace inode numbers are `unsigned int`
and were printed with `%u`, so -1 came out as `4294967295`, which reads like
a real ID; the Audit reporter's value for an unobserved namespace is `-1`.

**Fix.** The module reads the children's namespace from the task's `nsproxy`
(`pid_ns_for_children`, under `task_lock`), which points to the new namespace
as soon as unshare returns; its inode number is allocated when the namespace
is created. The serializer prints an unreadable namespace as `-1`.
`test/ns-pid/ns-ab-analyze.py` now judges `ns_inum_pid_children` too, which
Part A of the earlier checks had not.

**Checked.** The in-kernel test module (`spade_audit_test`, via
`bin/test/spade_audit_test/run.sh --dry-run`) has a new serializer case for
-1 next to inode numbers above `INT_MAX`; all 188 in-kernel tests pass.
`test/ns-pid`, base `d36e055a` against fixed `2728115f`: `UNSHARE` records
with a wrong `ns_inum_pid_children` went from 7 of 10 to 0 of 10, and every
judged one named the namespace `/proc` showed as the caller's
`pid_for_children`. No namespace record of the fixed build holds
`4294967295`; its -1 values are all in `NEWPROCESS` records whose mount,
network, IPC and cgroup namespaces are -1 too, as for a child that has
already exited. Results are in `test/ns-pid/README.md`.

**Older traces.** Every unshare(CLONE_NEWPID) version has `children pid
namespace` `4294967295`, and some processes have `4294967295` for namespaces
that could not be read. The container methods on `clarity-of-container` count
`4294967295` as unobserved and take a new namespace's ID from the first
process created after the unshare, so they work on traces from either module.

**Files.** `audit/kernel/helper/namespace.c`,
`audit/msg/namespace/serialize/audit.c`; tests
`pkg/linux/kernel_modules/test/kernel/audit/msg.c`,
`pkg/linux/kernel_modules/test/ns-pid/ns-ab-analyze.py`.

## F8. The bridge reads auditd's socket in a thread of its own

**Symptom.** Traces missed events that auditd had logged, with no error
anywhere. In one trace, 58,526 of the 151,096 events in auditd's log (39%)
never reached SPADE's Audit reporter, in runs of up to 1,291 consecutive
serial numbers during container starts. The graph then lacked, for example,
the creation of 4 of 10 container inits; those processes showed up only as
first seen later, with `-1` labels. The kernel's `lost` counter did not move,
and auditd reported nothing.

**Cause.** SPADE reads audit events from auditd's af_unix plugin
(`/sbin/audisp-af_unix`, audit 3.1.2) through `spadeAuditBridge`. The plugin
writes each event to its one client on a non-blocking socket and ignores
`EAGAIN` and short writes, so an event that does not fit drops without a
trace (`read_audit_record` in `audisp/plugins/af_unix/audisp-af_unix.c`). The
bridge called `recv` only between processing events and writing them to
SPADE, so whenever SPADE was slower than the event rate, the socket filled
and events were lost. On a Unix stream socket the sender's send buffer
limits what can wait, so the bridge cannot enlarge it from its side.

**Fix.** A thread of the bridge only reads the socket and queues what
arrives, in memory (at most 1 GB; beyond that it counts what it drops), and
the main thread processes the queue. On SIGINT or SIGTERM the bridge
processes what it has received, then flushes and exits. Audit serial numbers
count the events the kernel sends, so the bridge also counts serials it never
received and reports them on stderr, which SPADE logs: "Audit events not
received so far (missing serial numbers)", and a total when it stops.

**Checked.** A log with serials 100, 101, 103, 105, 104, 106, read with
`spadeAuditBridge -F`, gives the same output as before the fix and reports 1
missing event. On real traces, auditd's log was compared with everything the
reporter read (`outputLog`):

| Trace | Events in auditd's log | Missing from SPADE | Bridge's count |
|---|---|---|---|
| without F8 | 151,096 | 58,526 | - |
| with F8 | 164,642 | 427 | 427 |
| with F8 and `net.core.wmem_default` 16 MB | 166,639 | 0 | 0 |

With F8 the bridge's queue peaked at 24 MB, which is how far SPADE fell
behind. The 427 events still lost went in bursts while the bridge's reader
thread lagged, because the plugin's socket can hold only one send buffer:
`net.core.wmem_default` when the bridge connects, 212,992 bytes by default.
Raising it before SPADE starts its Audit reporter, e.g. `sysctl -w
net.core.wmem_default=16777216`, lost nothing. auditd's own dispatcher queue
(`q_depth`) never overflowed; it would have logged that.

The queue holds at most 1 GB by default, which a busy microservice stack
can fill. The media microservices of trace 3 produced 2.49 million audit
events in under three minutes, 14,000 a second on average, while SPADE
stored at most about 2,700 a second with three storages attached, and fewer
as Quickstep's tables grew (N16). The bridge's queue peaked at 836 MB. So the
size is an option (`41afaf09`): the bridge takes `-q <megabytes>`, and the
Audit reporter passes its `spadeAuditBridgeQueueMegabytes` in live mode.
When the reporter is removed, the bridge hands SPADE the whole queue before
it exits, which took an hour for trace 3.

**Older traces.** A trace recorded without F8 may miss events with no sign
of it. Comparing auditd's log over the same time with the Audit reporter's
`outputLog`, if it was set, shows which.

**Files.** `pkg/linux/audit_bridge/src/spadeAuditBridge.c`,
`pkg/linux/audit_bridge/Makefile.am`, `pkg/linux/audit_bridge/Makefile.in`
(`-pthread`); for the queue size `reporter/Audit.java`,
`reporter/audit/SPADEAuditBridgeProcess.java`,
`cfg/spade.reporter.Audit.config` and
`cfg/spade.reporter.Audit-with-namespace-awareness.config`.

## F9. clone3 is recorded

**Symptom.** A process created with clone3 had no creation record: no edge
from its parent, and with `namespaces=true` only `-1` namespace labels, so it
dropped out of its container's boundary. glibc's `posix_spawn` uses clone3,
and since glibc 2.34 so does `pthread_create` for threads. Found in Part A of
the trace checks, which showed a `posix_spawn` child with no record.

**Cause.** The kernel module hooked clone, fork and vfork but not clone3,
SPADE's audit rules did not include it, and the reporter did not know
syscall 435. clone3 also takes its flags in a `struct clone_args` in user
memory, so its syscall record holds only a pointer where clone's holds the
flags; the reporter needs them to tell a thread from a process and to spot
`CLONE_NEWPID`.

**Fix.**

- Kernel module (`cea5d23c`): a clone3 hook writes the same `ns_NEWPROCESS`
  record as clone. Before the call it reads `flags` and `exit_signal` from the
  caller's `clone_args` and puts them in the record as `ns_clone_flags`, in the
  form clone takes them: the flags with the exit signal in the low byte. It is
  built only where `__NR_clone3` exists (Linux 5.3).
- Audit reporter (`e09693b6`): audit syscall 435 (by number, because auditctl
  before audit 3.0 may not know the name) and handle it as clone, with the
  flags from `ns_clone_flags`. The reporter keeps their low 32 bits, the part
  clone can express; the higher bits hold clone3-only flags such as
  `CLONE_CLEAR_SIGHAND`. Without the module's record (`namespaces=false`) the
  flags are unknown and taken as 0.

**Checked.** The in-kernel tests have a serializer case for `ns_clone_flags`
and the clone3 function in the namespace filter lists; all pass.
`test/ns-pid/clone3-test.sh`, which starts one child with `posix_spawn` and
one with `fork`, and compares the records with `strace`:

| Build | `posix_spawn` child (clone3) | `fork` child (clone) |
|---|---|---|
| before `cea5d23c` | no record | recorded, `ns_syscall=56` |
| with `cea5d23c` | recorded, `ns_syscall=435`, `ns_clone_flags=0x100004111` | recorded, `ns_syscall=56` |

`strace` showed `clone3({flags=CLONE_VM|CLONE_VFORK|CLONE_CLEAR_SIGHAND,
exit_signal=SIGCHLD, ...})`, which is 0x100004111. `Clone3FlagsTest` covers
syscall 435 and the conversion of the flags.

**Older traces.** Processes and threads created with clone3 are missing their
creation; nothing recovers it.

**Files.** `pkg/linux/kernel_modules/audit/kernel/function/sys_clone3/`,
`audit/kernel/function/{number.h,number.c,op.c}`,
`audit/global/function/function.c`, `audit/msg/namespace/namespace.h`,
`audit/msg/namespace/serialize/audit.c`, `pkg/linux/kernel_modules/Kbuild`;
`reporter/Audit.java`, `reporter/audit/{SYSCALL,OPMConstants,AuditEventReader,AuditControlManager}.java`,
`reporter/audit/process/ProcessManager.java`; tests
`pkg/linux/kernel_modules/test/kernel/audit/{msg,global}.c`,
`pkg/java/src/test/java/spade/reporter/audit/process/Clone3FlagsTest.java`.

## F10. netfilterHooksLogCT means what the config says

**Symptom.** With `networkAddressTranslation=true` and three storages
attached, the Audit reporter fell minutes behind: it handled 360 records a
second, and a thread dump showed it inside `NetfilterHooksManager`'s SQL
queries on its H2 tables, once for every packet. The module had been loaded
with `nf_hooks_log_all_ct=all`.

**Cause.** `cfg/spade.reporter.Audit.config` documents
`netfilterHooksLogCT=true` as logging every packet and `false` as logging only
packets that start a connection, which is what the netfilter hooks did when
they were added (`03bbab70`). `KernelModuleArgument.createArgument` mapped the
two the other way round, so the default, `false`, logged every packet,
including SPADE's own loopback traffic to its storages.

**Fix.** The mapping follows the config again.

**Checked.** `KernelModuleArgumentTest`: `false` gives `ONLY_NEW` and `true`
gives `ALL`.

**Older traces.** Recorded with every packet: correct, but much larger, and
slow to record.

**Files.** `reporter/audit/KernelModuleArgument.java`; test
`pkg/java/src/test/java/spade/reporter/audit/KernelModuleArgumentTest.java`.

## F11. A thread's exit ends only that thread

**Symptom.** In trace 3 the boundaries of the multi-threaded services held
thousands of versions of their own main process labeled `-1`, first seen
with a `seen time`: 4,663 in cast-info-service's, 2,013 in movie-id-service's.
Each of movie-id-service's 2,013 followed one of its 3,963 thread exits, by
12 ms in the median.

**Cause.** audit records a syscall's `pid=` as the thread group id, so the
`exit` (60) of any thread names the process. `ProcessManager.handleExit`
took the record's pid for the thread that exited: while other threads of
the group remained, it removed that pid's state with
`removeProcessUnitState`, which is the group leader's. The process's next
syscall found no state and created a new process vertex, first seen and
without namespaces. `removeProcessUnitState` also drops the process's file
descriptors, so later operations on them found no artifact ("No FD with
number ...") and drew no edge.

**Fix.** An exit ends the named state only when the group has no other known
thread, or when the record names a known thread of the group by its own id
(`ProcessManager.exitEndsState`). Otherwise the record names the group, the
thread that exited is unknown, and every state stays until `exit_group`. A
pid reused by a new process drops the thread group kept under it.

**Checked.** `ThreadExitTest`. Trace 3 replayed with the fix (the reporter
reads the input it read live; `test/query/container/replay.sh` on
`clarity-of-container`): no boundary holds an unlabeled version of its own
processes, where the live trace's held 9,564, and every expectation holds as
live (TRACE_VALIDATION.md, trace 3 replayed). The graph has 23,317 fewer
vertices and 22,578 more edges: SPADE knew the descriptor in 21,216 more
`close` calls (30,881 unknown live, 9,665 in the replay), and the second runs
of 8 multi-threaded services hold 144 more sockets.

**Older traces.** Multi-threaded processes appear as many first-seen
versions with `-1` labels, and some operations on their descriptors are
missing. The container methods leave such versions out of the members
(CLARITY_METHODS.md D5).

**Files.** `reporter/audit/process/ProcessManager.java`; test
`pkg/java/src/test/java/spade/reporter/audit/process/ThreadExitTest.java`.

## F12. The Quickstep storage looks up a batch's vertices in one query

**Symptom.** Storing trace 3 slowed down as the graph grew: SPADE stored
about 2,700 events a second at first and 250 to 300 after 45 minutes, with
Quickstep using three CPUs, and needed 67 minutes after the workload ended.

**Cause.** `processBatch` asks `getVertexId` for every vertex of a batch and
both ends of every edge. A vertex in neither the short-lived map nor the
Deduplicate screen's cache cost a query, `copy select id from vertex where
md5='...'`, which scans the whole vertex table, and a new vertex is never in
the cache.

**Fix.** The batch's unknown hashes go into a temporary table, and one query
returns the ids of those already stored; `getVertexId` knows the rest are
not stored. If that query fails, `getVertexId` queries per vertex as before.

**Checked.** Trace 3 replayed: Quickstep used 8% to 15% of a CPU instead of
300%, never fell back to a query per vertex, and the three storages hold
the same graph (753,993 vertices, 2,105,655 edges). With F14 as well, the
storages had all of trace 3 6.6 minutes after the replay started; after the
live trace they needed 67 minutes.

**Files.** `storage/Quickstep.java`.

## F13. The Audit reporter warns when records may be lost

Not a fix of the losses themselves, which happen outside SPADE (N14, N15),
but of their going unnoticed:

- The bridge's reports of missing serial numbers and of data dropped from
  its queue are logged as WARNING when the count is not zero. At INFO they
  went unnoticed, and they were what showed N14.
- In live mode the reporter warns when the kernel was not booted with
  `audit=1` (from `/proc/cmdline`), since the kernel then drops records
  auditd falls behind on without counting them (N14).
- The reporter warns, once, when its records come with auditd's ENRICHED
  names, since auditd then looks up every unknown uid again for every
  record (N15). SPADE's user cannot read `auditd.conf`, so the records tell.

**Checked.** `AuditCompletenessTest`. A live Audit reporter on the VM of
the traces (kernel without `audit=1`, auditd with ENRICHED) logged the
kernel warning when it was added and the ENRICHED warning once, with the
first such records.

**Files.** `reporter/audit/AuditCompleteness.java` (new),
`reporter/Audit.java`, `reporter/audit/AuditEventReader.java`; test
`pkg/java/src/test/java/spade/reporter/audit/AuditCompletenessTest.java`.

## F14. The netfilter hooks' tables are indexed

**Symptom.** Replaying trace 3 with F11 and F12, the reporter's thread was
the bottleneck, and every sample of its stack was in an H2 query of
`NetfilterHooksManager`; the replay needed 36 minutes.

**Cause.** `NetfilterHooksManager` keeps each hook's packets in an in-memory
H2 table and finds a packet's earlier hooks, and a syscall's connection,
with queries on columns that had no index; rows are updated and deleted by
a row id that is not a key. Each table holds up to `maxPerHookEntries`
rows (65,536) before old ones expire, so every netfilter record scanned up
to 65,536 rows, several times.

**Fix.** Indexes on the row id, the event time, the skb id and the ports the
lookups use.

**Checked.** Trace 3 replayed with F14: the reporter handled the 2.49
million events in 190 s instead of 2,167 s, although it handled more of
them (the replay without F14 had also left out the module's network
records, N20). The hook tables' counts match the live trace's exactly
(79,831 pre-routing and local-in rows, 103,026 post-routing, 68,503
expired); the syscall table got 90 more rows, probably network syscalls on
descriptors that F11 keeps.

**Files.** `reporter/audit/NetfilterHooksManager.java`.

## Running the checks

| Check | How | Needs |
|---|---|---|
| Java unit tests (F4, F5, F9, F10, F11, F13) | in `pkg/java`: `mvn test -Dtest='AuditEventReaderTest,PostgreSQLTest,Clone3FlagsTest,KernelModuleArgumentTest,ThreadExitTest,AuditCompletenessTest'` | JDK 21, Maven, SPADE's `cfg/` reachable from `pkg/java` |
| In-kernel tests (F7, F9) | `make KERNEL_MODULES=true`, then `pkg/linux/kernel_modules/bin/test/spade_audit_test/run.sh --dry-run` | kernel headers; no other SPADE module loaded |
| ns pid A/B (F1, F2, F7) | `pkg/linux/kernel_modules/test/ns-pid/README.md` | a disposable Linux VM with Docker |
| netfilter (F3) | `pkg/linux/kernel_modules/test/netfilter-atomic/README.md` | a disposable VM: without the fix it freezes |
| bridge (F8) | compare auditd's log with the reporter's `outputLog` over a trace, by serial number and, within each event, by record type (N17); the bridge's own count of missing serials is in SPADE's log | auditd writing its log, enough log rotation to keep the trace |
| replay (F11, F12, F14) | `test/query/container/replay.sh 3` on `clarity-of-container`, then its queries and `check-media.py` | a recorded trace 3 and the VM it was recorded in |

## Noticed, not changed

These came up during the same work. Those not fixed are left for the
maintainers. N18, fixed as F11, was the only one that changed what the
container methods return on the traces checked (TRACE_VALIDATION.md, trace 3).

| # | Component | What |
|---|---|---|
| N1 | kernel module | In `nf_handle_packet`, a packet that was logged also falls through to `discard_and_exit` and is counted as discarded. |
| N2 | kernel module | The module still pads `comm` with NULs; F4 handles that in the reader. Encoding `strnlen(comm)` bytes would fix it at the source. |
| N3 | kernel module | `kernel_helper_namespace_populate_msg` calls `pid_task()` after `rcu_read_unlock()` and without a reference on the `struct pid`; `pid_task()` needs the RCU read lock. A process exiting at that moment could make it read freed memory. It is in upstream `prov-query` as well. Found by reading the code; never observed. |
| N5 | Audit reporter | After unshare or setns, a later execve or setuid/setgid gives the process the namespace labels it was created with, because unshare and setns don't update the per-process namespace state (`ProcessManager.handleNamespaceUpdateFromSyscall`). |
| N6 | Audit reporter | When `spadeAuditBridge` cannot connect to auditd's socket ("Permission denied"), SPADE logs it at INFO level and still reports the reporter as added; nothing is recorded. |
| N7 | build | `make` replaces `bin/spadeAuditBridge` without the setuid bit `bin/allowAuditAccess` gives it, so the bridge then fails as in N6. Run `bin/allowAuditAccess` after every build. |
| N8 | SPADE kernel | On shutdown, SPADE saves its running configuration to `cfg/spade.client.Control.config` and replays it at the next start, storage arguments included. A storage added with `reset=true` is therefore reset again by a plain restart. |
| N9 | QuickGrail | `stat` on a variable whose assignment failed: PostgreSQL reports a failed query on a missing table, Neo4j prints 0 vertices and 0 edges. |
| N10 | auditd | The af_unix plugin (audit 3.1.2) serves one client at a time and closes any other connection without a log, and it drops events its client cannot take at once without a log (F8). The bridge works around the second; only auditd can fix it. |
| N11 | Audit reporter | The audit rules leave out the user SPADE runs as with `-F uid!=<uid>`, which compares the real uid. sudo keeps the caller's real uid, so a process sudo forks for that user is not recorded as created: it first appears in its own later syscalls, with `-1` namespace labels. An unshare(CLONE_NEWPID) by such a process counts as joining, not creating (CLARITY_METHODS.md L3). |
| N12 | Neo4j storage | The thread `db-pending-task-runner` (`Neo4j.java`, its pending-task loop) uses a whole CPU even when idle: 435 s of CPU in 439 s. |
| N13 | Quickstep storage | Queries do not flush the batch being filled, which the storage sends only when it is full or its timer fires (`batchTimeIntervalInSeconds`, 180 s by default), so a query can miss the last minutes of a trace. PostgreSQL flushes before every query. |
| N14 | Linux audit | Unless the kernel was booted with `audit=1`, records that `kauditd` fails to hand to auditd are dropped without being counted in `lost`. It waits 0.1 s for room in auditd's netlink socket, whose receive buffer is `net.core.rmem_default` when auditd starts (212,992 bytes; auditd has no setting for it), then gives up on the rest of its queue, and `kauditd_hold_skb` frees what fails again (`if (!audit_default) goto drop;`, `kernel/audit.c`, Linux 6.8). Under the media microservices this lost 289,000 to 599,000 events per trace attempt, in gaps of up to 184,343 serial numbers, which only the bridge's count of missing serial numbers (F8) showed. Restarting auditd while `rmem_default` is larger gives its socket room for bursts; booting with `audit=1` makes the kernel hold such records and count those it must drop. The Audit reporter warns about it at start (F13). |
| N15 | auditd | With `log_format = ENRICHED` (Ubuntu's default), auditd (3.1.2) looks up the name of each uid and gid in each record through NSS and does not remember a failed lookup. Containers that run as uids the host does not know therefore cost it file opens and socket connections for every event: for 2,000 events of a process running as uid 11211, 12,858 `openat` and 839 `connect` calls, against none for a known uid or with `log_format = RAW`. With `nss-systemd`, `systemd` answers each lookup, and the Audit reporter records those answers as further events. It made auditd too slow for the media microservices (TRACE_VALIDATION.md, trace 3). The Audit reporter reads only the raw fields, so `log_format = RAW` loses it nothing. The Audit reporter warns when it reads such records (F13). |
| N16 | Quickstep storage | Fixed: F12. Storing slowed down as the graph grew, a scan of the vertex table for every new vertex. |
| N17 | auditd | auditd's queue to its plugins (`q_depth`, 2,000 by default) drops a record when it stays full for about 8 ms (`enqueue` in `audisp/queue.c`, audit 3.1.2: four tries 2 ms apart), and reports only the first five drops of each run ("queue to plugins is full - dropping event"). A record dropped from an event leaves the event's serial number in place, so the bridge's count of missing serial numbers (F8) does not see it. In trace 3, three records were dropped this way, two of them SYSCALL records (TRACE_VALIDATION.md); comparing auditd's log with the reporter's `outputLog` record by record finds them. A larger `q_depth` gives the plugins more room. |
| N18 | Audit reporter | Fixed: F11. A thread's exit was taken for its process's exit. |
| N20 | Audit reporter | In playback, unless `handleLocalEndpoints` is given, the reporter decides from the first network event in the log whether to use the kernel module's network records: a network SYSCALL record first means no, for the whole log. A log recorded live with the module (`localEndpoints=true`) can start that way, as trace 3's does, and its replay then silently loses every socket the module recorded: trace 3's boundaries held 72,139 live and none in the replay. Passing `handleLocalEndpoints` as the live run had it avoids it (`replay.sh` reads it from the live run's SPADE log). |

## Open

Nothing at the moment. O1, events missing between auditd and SPADE, was F8.
