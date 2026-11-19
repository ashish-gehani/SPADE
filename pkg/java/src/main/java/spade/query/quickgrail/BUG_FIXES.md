# Fixes for Container Labeling

Container labeling (CLARION §4.2, the `ContainerLabels` transformer) was
checked against the audit kernel module and against real traces. That turned
up bugs in the kernel module, the audit bridge, the Audit reporter, the SPADE
kernel and its query server, QuickGrail and all three storages. A stack of pull
requests on upstream `prov-query` (`6b744f7c`) fixes them, one area at a time,
and adds the transformer last. This file describes each fix in the summary
below: what went wrong, why, what changed, how it was checked, and what it
means for traces recorded before it. Fixes are numbered in the order they were
found, so an entry can name a fix that a later pull request of the stack adds.
The commits named are on branch `bug-fixes`, where the fixes were first made.

The summary says which fixes container labeling needs: **Required** means
that without it, in the case named, labels are wrong or missing, or the graph
that a boundary query reads misses artifacts; **Optional** fixes are about
speed, warnings, safety or other parts of SPADE.

Paths are relative to the repository root. The kernel module is
`pkg/linux/kernel_modules/audit/`, and Java classes are under
`pkg/java/src/main/java/spade/`.

## Summary

| # | Component | Bug | Container labels | Commits | Checked by |
|---|---|---|---|---|---|
| F1 | kernel module | `ns_ns_pid` held the PID in the creator's namespace, not the process's own | Required | `9fd54e0c` | `test/ns-pid` A/B on a live system |
| F2 | kernel module | an unshare or setns inside a container wrote no record | Required | `d445c7cc` | `test/ns-pid` A/B |
| F3 | kernel module | the netfilter hook could sleep in atomic context and freeze the system | Required with `networkAddressTranslation=true` | `da9c05b8`, `1527278b` | `test/netfilter-atomic` reproduction |
| F4 | Audit reporter | process names from kernel-module records ended in NULs | Required, unless the module has F25 | `ed373f13` | unit tests; a real trace |
| F7 | kernel module | an unshare(CLONE_NEWPID) record never named the new namespace | Required | `2728115f`, `5c8c1db2`, `0cdc679a` | in-kernel unit test; `test/ns-pid` A/B |
| F8 | audit bridge | auditd's af_unix plugin silently dropped events whenever SPADE fell behind | Required (live) | `b457bb46`, `41afaf09` | a real trace: auditd's log against SPADE's input |
| F9 | kernel module, Audit reporter | processes and threads created with clone3 had no creation record | Required | `cea5d23c`, `e09693b6`, `1b55e895` | in-kernel unit tests; `test/ns-pid/clone3-test.sh`; unit tests |
| F10 | Audit reporter | `netfilterHooksLogCT=false` logged every packet instead of only new connections | Optional | `3bbb7352` | unit test |
| F14 | Audit reporter | the netfilter hooks' tables had no indexes, so every packet record scanned them | Optional (speed) | `583ec2d7` | trace 3 replayed |
| F18 | Audit reporter, kernel module | a process sudo started for SPADE's user was not recorded as created | Required when SPADE's user starts containers with sudo | `3d9fc798` | B11 in trace 2; `test/ns-pid/filter-test.sh` |
| F24 | kernel module | every logged packet was also counted as discarded | Optional (a counter) | `c0a567c4` | in-kernel tests |
| F25 | kernel module | process names were encoded with their NUL padding | Optional, given F4 | `dd2b921b` | in-kernel tests; trace 1 |
| F26 | kernel module | a task's namespaces were read without holding the task | Optional (safety; recommended) | `a3a35f19` | in-kernel tests; `test/ns-pid` A/B |
| F29 | kernel module | the pid and ppid filters compared thread ids, so other threads and children of a process left out were recorded | Optional (keeps SPADE's own processes out) | `f0532696` | `test/ns-pid/filter-test.sh`; trace 1 |

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
outside (`docker exec`). Container labeling doesn't use `ns pid`.

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
`test/ns-pid/ns-ab-analyze.py` judges `ns_inum_pid_children` too.

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
that could not be read. The `ContainerLabels` transformer counts
`4294967295` as unobserved and takes a new namespace's ID from the first
process created after the unshare, so it works on traces from either module.

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
lost its container. glibc's `posix_spawn` uses clone3,
and since glibc 2.34 so does `pthread_create` for threads.

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

## F18. SPADE's user is left out by effective uid

The audit rules left out the user SPADE runs as with `-F uid!=`,
the real uid, and so did the kernel module's user filter. sudo keeps the real
uid, so a process sudo started for that user was not recorded as created,
though it acts as root: it first appeared in its own later syscalls, with
`-1` namespace labels, and an unshare(CLONE_NEWPID) by it counted as
joining, not creating (CLARITY_METHODS.md L3).

**Fix.** The rules use `-F euid!=` (`euid=` when capturing one user), and
the module compares the effective uid, for syscalls (`hook.h`) and for
packets (`netfilter.c`). The audit bridge is setuid root, so its pid joins
the pids to ignore, and SPADE's own pid the ppids, in the rules and in the
module's arguments; the reporter built these sets but did not use them. The
module's pid filters compared thread ids until F29.

**Checked.** B11 in trace 2: `sudo unshare --pid --fork sh -c '...'`, run by
SPADE's user directly. The `unshare` process is recorded as created, and its
namespace (the first run of 4026532203) has a start whose init is the
`unshare` child.
`test/ns-pid/filter-test.sh` gave the same results with the module as
before.

**Files.** `reporter/Audit.java`, `reporter/audit/AuditControlManager.java`,
kernel module `audit/kernel/function/hook.h`,
`audit/kernel/netfilter/netfilter.c`.

## F24. A logged packet is not counted as discarded

`nf_handle_packet` logged a packet and then fell through to
`discard_and_exit`, so every logged packet was also counted as discarded.
It returns after logging now. Only the module's discard counter changes,
which nothing in SPADE reads.

**Checked.** The in-kernel tests pass and traces 1, 2 and 4, recorded with
the module, are complete; the counter itself was not looked at.

**Files.** kernel module `audit/kernel/netfilter/netfilter.c`.

## F25. The module encodes a process's name without its padding

The module hex-encoded all `TASK_COMM_LEN` bytes of `comm`, the NULs
that pad a short name included, and the reader had to cut them off (F4). It
encodes `strnlen(comm)` bytes now. F4 stays, for logs of older modules.

**Checked.** In-kernel tests (`test_msg` now expects the name alone, e.g.
`comm=746573745f70726f63657373` for `test_process`). None of the
module's 14,433 network records in trace 1 (`trace1e`) has the padding; an
earlier recording without F25 had it in all 13,022.

**Files.** kernel module `audit/msg/common/serialize/audit.c`, test
`pkg/linux/kernel_modules/test/kernel/audit/msg.c`.

## F26. The module holds a task while it reads its namespaces

`kernel_helper_namespace_populate_msg` released the RCU read lock
and then called `pid_task()`, and read the task's namespaces, without a
reference on the task. `pid_task()` needs the RCU read lock, and a task
exiting at that moment could be freed under the reads. Found by reading the
code; never observed.

**Fix.** The task is looked up under the lock and held with
`get_task_struct` until its namespaces are read.

**Checked.** In-kernel tests (132 global, 44 message, 9 argument, 6 context
and 1 state test pass). The ns pid A/B (`test/ns-pid`) between the module of
`583ec2d7` and the one with F24 to F26: no namespace record wrong in either,
no audit record lost; `filter-test.sh` and `clone3-test.sh` gave the same
results as before.

**Files.** kernel module `audit/kernel/helper/namespace.c`.

## F29. The module's pid filters name processes

**Symptom.** In a recording of trace 1 with F18, the kernel module recorded
25 `recvfrom` calls of the audit bridge on auditd's socket, although the
bridge's pid was among the pids to ignore.

**Cause.** The module's filters compared the calling thread's id and the id
of the thread that created its process (`current->pid`,
`current->real_parent->pid`), while the reporter fills them with process ids,
as the audit rules' `-F pid` and `-F ppid` compare. So only the main thread
of a process left out was left out (the bridge reads auditd's socket in a
thread of its own, F8), and a child of a multi-threaded process (SPADE's JVM
starts its children from other threads) never matched its parent's entry.
Before F18 the user filter left all of these out anyway.

**Fix.** The filters compare the process id and the parent's process id
(`task_tgid_nr`, `task_ppid_nr`), as audit's `pid=` and `ppid=` fields. The
module's records still carry the thread id.

**Checked.** `test/ns-pid/filter-test.sh` with the module of F29: the same
results as before (a pid filter on the container init's host pid leaves its
records out, one on its namespace pid does not, a ppid filter on
containerd-shim leaves out its children). In trace 1 recorded with F29 (`trace1e`), no module record of the bridge, where the recording before F29 had 25.

**Files.** kernel module `audit/kernel/function/hook.h`,
`audit/kernel/helper/task.c`, `audit/kernel/helper/task.h`.

## Running the checks

| Check | How | Needs |
|---|---|---|
| Java unit tests (F4, F9, F10) | in `pkg/java`: `mvn test -Dtest='AuditEventReaderTest,Clone3FlagsTest,KernelModuleArgumentTest'` | JDK 21, Maven, SPADE's `cfg/` reachable from `pkg/java` |
| In-kernel tests (F7, F9, F25, F26) | `make KERNEL_MODULES=true`, then `pkg/linux/kernel_modules/bin/test/spade_audit_test/run.sh --dry-run` | kernel headers; no other SPADE module loaded |
| ns pid A/B (F1, F2, F7, F26; `filter-test.sh` for F18, F29) | `pkg/linux/kernel_modules/test/ns-pid/README.md` | a disposable Linux VM with Docker |
| netfilter (F3) | `pkg/linux/kernel_modules/test/netfilter-atomic/README.md` | a disposable VM: without the fix it freezes |
| bridge (F8) | compare auditd's log with the reporter's `outputLog` over a trace, by serial number and, within each event, by record type (N17); the bridge's own count of missing serials is in SPADE's log | auditd writing its log, enough log rotation to keep the trace |
| replay (F14) | `test/query/container/replay.sh 3` on `clarity-of-container`, then `query.sh` and `check-labels.py` | a recorded trace and the VM it was recorded in |
| traces (F18, F25, F29) | `test/query/container/trace.sh 1|2|4`, then `query.sh`, `check-labels.py`, `fdcheck.py`, `nofd.py` and `f17check.py` (TRACE_VALIDATION.md) | the VM; trace 4 also DeathStarBench's media microservices |

## Noticed, not changed

Problems found during the work and not fixed here, left for the maintainers.

| # | Component | What |
|---|---|---|
| N10 | auditd | The af_unix plugin (audit 3.1.2) serves one client at a time and closes any other connection without a log, and it drops events its client cannot take at once without a log (F8). The bridge works around the second; only auditd can fix it. |
| N17 | auditd | auditd's queue to its plugins (`q_depth`, 2,000 by default) drops a record when it stays full for about 8 ms (`enqueue` in `audisp/queue.c`, audit 3.1.2: four tries 2 ms apart), and reports only the first five drops of each run ("queue to plugins is full - dropping event"). A record dropped from an event leaves the event's serial number in place, so the bridge's count of missing serial numbers (F8) does not see it. In trace 3, three records were dropped this way, two of them SYSCALL records; comparing auditd's log with the reporter's `outputLog` record by record finds them. A larger `q_depth` gives the plugins more room. |
