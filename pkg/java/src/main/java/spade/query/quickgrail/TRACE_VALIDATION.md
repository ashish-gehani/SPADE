# Container Methods: Validation on a Live System

Companion to `CLARITY_METHODS.md`, for its open items Q1 and Q2. Part A checks
the audit kernel module's namespace records, which both container methods
read. Part B, still to do, runs the methods on real traces.

## Environment

| | |
|---|---|
| Machine | VirtualBox VM from the Vagrant box `bento/ubuntu-24.04` 202508.03.0, 16 vCPUs, 8 GB RAM |
| OS | Ubuntu 24.04.3 LTS, Linux 6.8.0-146-generic, gcc 13.3.0, glibc 2.39 |
| Containers | Docker 29.0.0, containerd v2.1.5, runc 1.3.3 |
| Tools | util-linux 2.39.3 (`unshare`, `nsenter`), systemd 255, OpenSSH 9.6p1, Python 3.12.3 |
| Code | branch `clarity-of-container` at `9d6e210b`, on top of the two `ns pid` fixes (up to `d445c7cc`) and `prov-query` `6b744f7c`. The branches were restructured later, so no branch contains `9d6e210b`; its kernel-module code is exactly `d445c7cc`'s |

Three containers left over from an unrelated benchmark stack restarted in a
loop during the runs (one crashed with SIGSEGV each time). Their records were
judged like any other; they are why most scenario windows hold hundreds of
records.

## Part A: the kernel module's namespace records

Q2 asks for `ns pid` to be the PID of a process inside its own PID namespace.
Two fixes on branch `bug-fixes` are about that (`BUG_FIXES.md`):

- F1, `9fd54e0c`, records `ns_ns_pid` in the process's own PID namespace
  instead of the clone/fork/vfork return value, which is the PID in the
  creator's namespace.
- F2, `d445c7cc`, makes the unshare and setns hooks look up the caller by its
  PID in its own namespace instead of its host PID.

Part A judged `ns_ns_pid` and `ns_inum_pid` only, not `ns_inum_pid_children`.
That missed F7, found in Part B; its own A/B, with the same harness, is in
`BUG_FIXES.md` F7 and `pkg/linux/kernel_modules/test/ns-pid/README.md`.

### Method

**Two builds.** The module was built twice against Linux 6.8.0-146, each from
a `git archive` of its commit: *base* from `prov-query` `6b744f7c` and *fixed*
from `clarity-of-container` `9d6e210b`. Their module sources differ only in
the five files of the two commits. Both builds have no errors and the same
single warning, that the compiler differs from the one used to build the
kernel.

**The module alone.** Records were read from auditd's log, not through SPADE's
Java reporter, so the module is the only thing that differs between the runs.
The controller was loaded with the parameters SPADE passes by default
(`KernelModuleArgument`), with namespaces on:

```
nf_handle_user=0 nf_hooks=0 nf_hooks_log_all_ct=-1 net_io=0 namespaces=1
pid_trace_mode=1 ppid_trace_mode=1 uid_trace_mode=1 config_file=/opt/spade/audit/audit.config
```

The module only stores `config_file`; it never opens it. Each scenario is
bracketed by `auditctl -m` markers. The audit backlog limit was raised to
65536, and the kernel's `lost` counter stayed at 0 throughout, so a missing
record means the module never wrote it.

**Ground truth.** A sampler reads every process's `NSpid` line, PID namespace
inode and `pid_for_children` inode from the host's `/proc` every 50 ms. Each
record's subject (the new child for `NEWPROCESS`, the caller for `SETNS` and
`UNSHARE`) is matched by host PID and time. The expected `ns_ns_pid` is the
last `NSpid` entry and the expected `ns_inum_pid` is the sampled inode.
Subjects the sampler missed get a structural check instead: in the host PID
namespace `ns_ns_pid` must equal the host PID, and anywhere else equality is
the creator's-view bug.

### Scenarios

| # | Scenario | What it tests |
|---|---|---|
| S1 | `sh -c 'sleep 1; true'` on the host | Baseline |
| S2 | `unshare --pid --fork sleep 2` | `9fd54e0c`: first process of a new namespace |
| S3 | `unshare --pid --fork --mount-proc sh -c 'sleep 2 & sleep 2 & wait'` | Control: children created inside the namespace |
| S4 | `nsenter --target <init> --pid` from the host into S4's namespace | `9fd54e0c`: setns, then fork |
| S5 | inside an outer namespace, `unshare --pid --fork` and `nsenter --pid` | `d445c7cc`: callers outside the host namespace |
| S6 | `docker run --rm alpine sh -c 'sleep 1; true'` | runc's container start |
| S7 | `docker exec` into a running alpine container | runc's setns, then fork |

### Results

| | base | fixed |
|---|---|---|
| Namespace records | 2211 | 2237 |
| Correct | 2185 | 2236 |
| Wrong | **25** | **0** |
| Unverified | 1 | 1 |

All 25 wrong base records are `NEWPROCESS` records whose `ns_ns_pid` holds the
child's PID in its creator's namespace: 18 container inits
(`runc:[2:INIT]`), 4 `sh` and 3 `sleep`. The unverified record in each run is
a `pgrep` inside S5 that exited before the sampler saw it; its `ns_ns_pid` of 5
is what the scenario's order of forks implies.

The records each fix is about:

| Scenario | Process | base | fixed | Truth |
|---|---|---|---|---|
| S2 | first process after `unshare` on the host | 16706 | 1 | 1 |
| S3 | namespace init | 16797 | 1 | 1 |
| S3 | its two children | 2, 3 | 2, 3 | 2, 3 |
| S4 | `nsenter`'s child in the target namespace | 16931 | 2 | 2 |
| S5 | inner namespace init, created from the outer namespace | 4 | 1 | 1 |
| S5 | inner `nsenter`'s child | 7 | 2 | 2 |
| S6 | container init | 17552 | 1 | 1 |
| S7 | `docker exec` process | 18711 | 7 | 7 |

S5 shows that the old value is the PID in the creator's namespace, not the
host PID: the inner init was created by a process in the outer namespace, and
base recorded 4, its PID there.

`UNSHARE` and `SETNS` records for each `unshare` and `nsenter` process the
scenarios started:

| Scenario | Caller | `NSpid` | base | fixed |
|---|---|---|---|---|
| S2, S3, S4 | `unshare` on the host | host only | 1 record each | 1 record each |
| S4 | `nsenter` on the host | host only | 1 record | 1 record |
| S5 | outer `unshare` on the host | host only | 1 record | 1 record |
| S5 | inner `unshare` | `[17052, 2]`, `[20323, 2]` | **none** | 1 record, `ns_ns_pid=2` |
| S5 | inner `nsenter` | `[17075, 6]`, `[20342, 6]` | **none** | 1 record, `ns_ns_pid=6` |

Before `d445c7cc`, an unshare or setns by a process outside the host namespace
wrote no record at all. In every such call observed, the caller's host PID
did not exist in its own namespace, so the lookup found nothing.
`CLARITY_METHODS.md` §4.1 also allows for the lookup finding a different
process; that would need a host PID that happens to be in use inside the
caller's namespace, and it did not occur here.

### The pid filters still match host PIDs

The module filters on the calling task's `current->pid` and
`current->real_parent->pid`, both host PIDs. The fixes left that path alone
and uses the new own-namespace helper only in the unshare and setns audit
actions. To check, an alpine container forked a child every half second.
Its init had host PID 33180, in-namespace PID 1, and the parent 33096
(`containerd-shim`). The table counts the container's `NEWPROCESS` records in
3 seconds under each filter (`pid_trace_mode=1` and `ppid_trace_mode=1` mean
ignore the listed IDs):

| Filter | base | fixed |
|---|---|---|
| none | 14 | 11 |
| `pids=33180` (the init's host PID) | 0 | 0 |
| `pids=1` (the init's in-namespace PID; host PID 1 is systemd) | 12 | 12 |
| `ppids=33096` (the shim) | 0 | 0 |

Both variants match host PIDs and never the in-namespace PID.

### clone3

The module hooks `clone`, `fork` and `vfork`, not `clone3` (L4). To check
whether that matters, one Python process created a child with
`os.posix_spawn` and another with `os.fork`, under `strace`:

| Child | Created by | `NEWPROCESS` record |
|---|---|---|
| 29639 | `clone3(CLONE_VM\|CLONE_VFORK\|CLONE_CLEAR_SIGHAND, …)`, from glibc's `posix_spawn` | none |
| 29647 | `clone(CLONE_CHILD_CLEARTID\|CLONE_CHILD_SETTID\|SIGCHLD)` | yes |

So any process created through glibc's `posix_spawn` is missing. Across the
scenario windows, the only processes without a `NEWPROCESS` record were
children of `sshd` (shells for SSH sessions) and of systemd
(`systemd-tmpfiles`). Every runc process, container init and scenario process
was recorded: runc 1.3.3 creates container processes with `clone`.

### Other observations

- Some `SETNS` records come from Go runtime threads in dockerd and containerd,
  which call setns to work in container network namespaces. They carry the
  thread's ID as `ns_host_pid`.

### Reproducing Part A

The scripts are in `pkg/linux/kernel_modules/test/ns-pid/`, with a README
that lists what they need and the order to run them in: build both module
variants, run the scenarios under each, analyze, then the filter check and
the clone3 check.

## Part B: the methods on real traces

The expectations below were written and committed before any trace for Part B
was recorded. They follow `CLARITY_METHODS.md` §1, §4.1 and §5; where a
scenario tests a fact rather than the methods, that is said. A trace can
disagree with them in two ways: the methods are wrong, or a fact they rely on
is. Either way the difference is recorded, not the expectation adjusted.

### Setup

SPADE runs in the same VM as user `vagrant`, from `clarity-of-container` with
the fixed module packaged into `lib/kernel-modules/` (`make
KERNEL_MODULES=true`). SPADE leaves out the activity of the user it runs as,
so every scenario runs under `sudo` or inside containers, which root's Docker
daemon creates. PostgreSQL, Neo4j and Quickstep storages are all attached, so
each query runs on all three against the same records, and the results must
agree. The scripts that record a trace, query it on each storage and compare
the results with the ground truth are in `test/query/container/`, whose
README lists them and what they need.

| Trace | Audit reporter arguments | Scenarios |
|---|---|---|
| 1 | the six keys `cfg/spade.reporter.Audit-with-namespace-awareness.config` changes: `namespaces=true fileIO=true netIO=true IPC=true inode=true networkAddressTranslation=true` | B1–B7 |
| 2 | `namespaces=true` only, so `netIO` stays off | B8, B9, B10 |
| 3 | the six keys, as trace 1 | M |

B8 is in its own trace because by Q3 (a) it makes `getContainerInit` fail for
the whole trace.

### Recording notes

Added after the expectations below, which are unchanged.

The first attempt at trace 1 froze the VM about two minutes in and was
discarded; none of it was queried. Both causes lay outside the container
methods:

- **The VM ran without hardware virtualization.** The host's Windows
  hypervisor, needed for WSL 2, held VT-x, so VirtualBox fell back to its much
  slower NEM mode in every session (`VBox.log`: "Attempting fall back to
  NEM"). The hypervisor is now off while the VM runs. The VM has 16 GB, and
  SPADE runs with a heap of at most 8 GB (`--mem-max 8g`) instead of its
  16 GB default.
- **The kernel module's netfilter hook slept in atomic context**
  (`BUG_FIXES.md` F3). With `networkAddressTranslation=true`, a full audit
  backlog made the hook wait in softirq, and the kernel reported `BUG:
  scheduling while atomic`. Part A ran with `nf_hooks=0`, so it never reached
  that code. Every later attempt used a module with F3.

The second attempt ran to the end and was queried, but it is not used as a
result either: records were missing on the way to both storages, for reasons
outside the container methods.

- **auditd fell behind.** Every container's veth device made udev open and
  close files thousands of times. Of the 48,014 syscall records auditd wrote,
  28,417 came from udev workers, mostly `close` (13,834) and `openat`
  (13,525). As B1 started, auditd logged `Error receiving audit netlink packet
  (No buffer space available)`, and the kernel counted 34 records lost.
- **PostgreSQL dropped 13,264 vertices** in whole batches, because process
  names from kernel-module records ended in NULs (`BUG_FIXES.md` F4, F5).
- **Neo4j failed every query** that builds a graph (F6).

Two more attempts were discarded by the completeness rule below, before any
query:

- **The third recorded nothing.** Rebuilding SPADE for the storage fixes
  replaced `bin/spadeAuditBridge` without the setuid bit `bin/allowAuditAccess`
  gives it, so the bridge could not read auditd's socket ("Permission
  denied"). SPADE logs that only at INFO level and still reports the reporter
  as added. The kernel module was loaded and the scenarios ran, but the
  storages stayed empty.
- **The fourth lost 61 records.** The audit backlog passed its limit, Ubuntu's
  8192, from B2 through B6. In Linux 6.8, a caller that may sleep waits for
  room (`backlog_wait_time` is 60 s), and only callers that may not, such as
  the module's netfilter hook, drop their record. So the lost records were
  most likely packet records, but nothing in the log shows which ones.

The harness changed for the later attempts:

- The audit backlog limit is 65536 while a trace records, as in Part A, and
  is restored afterwards.
- The harness stops before tracing if the bridge is not setuid or cannot
  connect, and a trace whose storage holds almost nothing is not complete.
- The Audit reporter also leaves out `systemd-udevd` (and so its workers) and
  `postgres`, SPADE's own storage, through `ignoreProcesses` and
  `ignoreParentProcesses`, on top of its defaults. Neither takes part in any
  scenario. The six keys are unchanged.
- A trace is judged only if it is complete: the kernel's `lost` counter
  unchanged, no receive or queue error from auditd, no failed storage write,
  and a start in `getContainerInit` for every container that started during
  tracing. Otherwise it is recorded again, not interpreted.
- SPADE saves its running configuration on shutdown, to
  `cfg/spade.client.Control.config`, and replays it on start, `reset=true`
  included. The harness now restores the tracked file, which adds nothing,
  before it starts SPADE. A restart before that change reset both storages
  and erased the second attempt's graphs; its query results were already
  saved.
- SPADE runs with F4 to F6 (`bug-fixes` at `d36e055a`) plus the container
  methods. The kernel module is the same build as before, without F7.

The fifth attempt passed those checks: the kernel's `lost` counter stayed at
236521, auditd and the storages reported no error, and PostgreSQL held 21,023
vertices. PostgreSQL and Neo4j returned the same vertices for every query.
It is still not used as a result, because its graph lacks the creation of
several container processes whose records auditd did write. For example, the
`clone` that created B7's init (event 858667) is in auditd's log together with
its `ns_NEWPROCESS` record (`ns_ns_pid=1`, the container's namespace), but the
graph has that process only as first seen later, with `-1` labels. SPADE logs
nothing about it. Of the 10 inits runc created in trace 1 (B1, B2, B4's
outer container, the six B6 runs and B7), 6 have a creation in the graph, and
no process carries B4's nested namespace. Where the records are dropped,
between auditd and the storages, is being traced (`BUG_FIXES.md` O1).

The fifth attempt also showed that a module without F7 never names the
namespace an unshare(CLONE_NEWPID) creates (`BUG_FIXES.md` F7). The methods
then merged the containers that reused an ID: 4026532410 held 2 containers
and they saw 1, and 4026532474 held 8 and they saw 1. Both the module and the
methods were fixed (`CLARITY_METHODS.md` Q11, D19). B1's expectation that
the unshare version's `children pid namespace` is X holds only for a module
with F7; with an older one that label is `4294967295`. The expectation stays
as committed, and the results say which module recorded each trace.

The sixth attempt used a module with F7 and passed the completeness checks
above, but it was not used either. This time the reporter wrote everything it
read (`outputLog`), and auditd's log was compared with it by serial number:
58,526 of the 151,096 events auditd logged (39%) never reached SPADE. They
went in runs of up to 1,291 consecutive serials during container starts, with
no message from auditd, its plugin or SPADE. auditd's af_unix plugin drops an
event its client cannot take at once, and the audit bridge read the socket
only between handling events (`BUG_FIXES.md` F8).

- **The seventh attempt recorded nothing.** To see whether the plugin drops
  per client, a second client read the same socket during the trace. The
  plugin serves one client and closes any other, so the bridge was the one
  refused ("Server closed the connection"). The second client was also root,
  and its writes were audited, so it read its own records back. The harness
  now also stops on that message.
- **The eighth attempt** ran the bridge with F8: 427 of 164,642 events were
  missing, all during B6 and B7, and the bridge's own count of missing
  serials agreed exactly.
- **The ninth attempt** also raised `net.core.wmem_default`, which sizes the
  plugin's socket buffer when the bridge connects, from 212,992 bytes to
  16 MB: none of the 166,639 events was missing. It was the first complete
  trace, but it was queried on PostgreSQL and Neo4j only, and it showed that
  B5 tested the harness rather than the methods: `sudo` keeps the real uid of
  the user SPADE runs as, the audit rules leave that uid out, so the
  `unshare` process sudo forked was not recorded as created and its unshare
  counted as joining (L3; `BUG_FIXES.md` N11).

The harness changed for the tenth attempt:

- Quickstep, the third storage QuickGrail can query, is attached as well,
  with a fresh database for each trace, and every query runs on all three.
  Its storage sends a batch only when the batch is full or a timer fires,
  and queries don't flush it, so the timer is 10 s instead of 180 s.
- B5 and B8 run their command through a root shell, `sudo sh -c '...'`, so
  that the process that unshares is created by an audited process. The
  commands themselves are unchanged.
- `trace.sh` saves auditd's log for the trace and the reporter's input, and
  the completeness check compares them; `net.core.wmem_default` is raised as
  in the ninth attempt.

### Scenarios and expectations

PIDs and namespace IDs are not known in advance, so expectations name
processes by role. X stands for a container's PID namespace ID.

**B1. `docker run --rm alpine sh -c 'sleep 1; true'`**, started after tracing.

- `getContainerInit()` holds one start for X, made of: runc's stage 1 before
  and after its unshare (the unshare version's `children pid namespace` is X);
  the fork, whose `flags` include `CLONE_PARENT`, to stage 2, the init, in X;
  the init's version after `unshare(CLONE_NEWCGROUP)`; and its execve version
  named `sh`. Expected names (§1): stage 1 `runc:[0:PARENT]`, init
  `runc:[1:CHILD]` until the execve.
- It holds nothing above stage 1 (no containerd-shim, `runc create` or stage
  0), not the `sleep` child, no later execve, no file, no exit edge.
- `getContainerBoundary('X')` holds the init's versions, `sh`, `sleep` and the
  files they used, and the runc process that created the init, as a
  neighbour. It holds no host process further out.

**B2. `docker exec`** into a container `docker run -d alpine sleep 60` started
during tracing.

- `getContainerInit()` holds that container's start and nothing for the exec:
  runc's setns makes the exec an entry, not a start.
- `getContainerBoundary('X')` adds the exec'd `sh` and its `sleep`, with the
  runc process that entered X as a neighbour.

**B3. A container started before tracing**, whose shell forks a `sleep` every
two seconds.

- `getContainerInit()` holds nothing for it (L1).
- Its shell was not created during tracing, so it carries `-1` labels (§4.1)
  and is not a member; the `sleep`s it forks during tracing are. So
  `getContainerBoundary('X')` holds the `sleep`s, with the shell as a
  neighbour. `X` has exactly one container, "running when tracing started".
  The fixtures (§9.4) model a pre-existing process with the container's labels
  and a `seen time`; this scenario checks that against §4.1.

**B4. A PID namespace created inside a container**:
`docker run --rm --privileged alpine unshare -p -f -m --mount-proc sh -c 'sleep 1; true'`
(busybox `unshare`). Its unshare runs inside X, so only a module with
`d445c7cc` records it.

- `getContainerInit()` holds two starts: X, by runc, and the nested namespace
  Y, by busybox `unshare` inside X.
- `getContainerBoundary('X')` holds Y's processes too (nesting);
  `getContainerBoundary('Y')` holds only Y's.

**B5. `sudo unshare --pid --fork --mount-proc sh -c 'sleep 1; true'`** on the
host.

- `getContainerInit()` holds one start: the `unshare` process's unshare step,
  its forked child, the init, and the init's execve to `sh`.

**B6. Reused namespace IDs.** Short containers run one after another, each
gone before the next starts. The kernel hands out the lowest free namespace
inode number, so IDs should repeat.

- For a repeated ID X, `getContainerBoundary('X')` fails with the numbered
  list (§5.5), one entry per container, each with its own start time and host
  PID.
- `getContainerBoundary('X', n)` holds exactly container n's processes.
- `getContainerBoundary()` holds all of them.

**B7. Network use from a container**: `wget` from an alpine container.

- With `netIO=true`, `getContainerBoundary('X')` holds a network artifact
  connected to the `wget` process.

**B8. bubblewrap**: `sudo bwrap --unshare-pid --dev-bind / / sh -c 'sleep 1; true'`.

- `getContainerInit()` fails: bwrap's PID 1 is its own reaper and never calls
  execve (L10, Q3 (a)). The message names the reaper.
- `getContainerBoundary()` still works and holds the sandbox's processes.

**B9. Network use from a container with `netIO=false`**: B7 again in trace 2.

- Tests §6: whether a network artifact reaches `getContainerBoundary('X')`
  when `netIO` is off and the module is loaded.

### Added scenarios

Added on 2026-11-02, after the user asked for `clone3` to be fixed (F9 in
`BUG_FIXES.md`) and for a realistic workload. Like B1–B9, these expectations
were committed before their traces were recorded.

**B10. A process created with clone3 inside a container**, in trace 2. A
static glibc program in an alpine container starts `/bin/true` with
`posix_spawn`, which glibc 2.39 does with clone3 (alpine's own musl would
use clone). The program and its child are created during tracing.

- With F9, `getContainerBoundary('X')` holds the child, a member of X joined
  to the program by a creation edge whose `flags` include `CLONE_VFORK` (with
  `simplify=true` the edge is a `fork`, §4.1).
- Without F9 the child would appear only as first seen, with `-1` labels,
  and not be a member (L4). The trace is recorded with F9 only.

**M. The DeathStarBench media microservices**, trace 3, recorded like trace 1
(the six keys). Its 33 containers (12 services from the
`yg397/media-microservices` image, 8 MongoDB, 7 memcached, 3 Redis,
OpenResty, Jaeger and a DNS proxy) exist but are stopped when tracing
starts. During tracing, `docker compose up -d` starts them all at once; once
the web server answers, the dataset's users and movies are registered and
wrk2 sends `compose-review` requests for 30 s. The ground truth is each
container's init host PID and PID namespace from `docker inspect` and
`/proc`, and the processes a `/proc` sampler sees in each namespace.

- M1: `getContainerInit()` holds exactly one start per container, 33 in all,
  and its init is the process `docker inspect` names. The starts overlap in
  time, which no B scenario tests: the grouping in §5.2 must still give each
  init to its own namespace.
- M2: For each container's namespace X, `getContainerBoundary('X')` holds
  every process the sampler saw in X, and no process of another container.
  Its only host neighbours are the runc processes that created or entered
  the container. No ID is used twice while all run, so no numbered list is
  expected.
- M3: The services' network sockets reach their boundaries (`netIO=true`).
- M4: All three storages return the same results.
- M5: Not an expectation but a measurement (L17): the trace's size, how long
  SPADE needs to store it, and how long each query takes on each storage.

**After F15 to F29.** Trace 3 led to more fixes (BUG_FIXES.md F15 to F27;
F28 and F29 came from the recordings below).
To check that they leave every result above standing, traces 1 and 2 are
recorded again with them, and in place of the full media stack, which takes
hours to record and query, a subset of it (trace 4). B11 to B13 check fixes
that only a live recording shows:

**B11. An unshare that sudo starts**, in trace 2: `sudo unshare --pid --fork
sh -c '...'`, run directly by SPADE's user, without the root shell B5 needs.

- With F18 (the user left out by effective uid), sudo's fork of `unshare` is
  recorded, so `unshare` is created in the trace, its unshare(CLONE_NEWPID)
  is a creation (L3 no longer applies), and `getContainerInit()` has a start
  for its namespace, as B5 has.

**B12. Descriptors through container starts**, in traces 1, 2 and 4.

- With F15, runc's `openat2` and `close_range` are recorded and handled, and
  execve drops close-on-exec descriptors. The reporter logs no "Missing
  directory FD" for runc's processes, and of the closes of descriptors it
  does not know, none has a recorded creation as the last recorded event on
  that descriptor (`nofd.py`).

**B13. Labels after an execve that follows an unshare or setns**, in trace 2,
added after trace 1 was recorded again, which has no such execve. From a root
shell: (a) `unshare --pid sh -c 'sleep 2; true'`, without `--fork`: the
`unshare` process calls unshare(CLONE_NEWPID) and then execs `sh`, which stays
in the host's PID namespace while its children start in the new one (Y);
(b) `nsenter -t <init> -m sh -c 'true'` on a running container: setns into
the container's mount namespace and then execve of the container's `sh`.

- With F17, the `sh` version of (a) has `children pid namespace` Y, and the
  `sh` version of (b) the container's `mount namespace`; before F17 both had
  the labels the process was created with (L5). (a) is a start in
  `getContainerInit()`, its init the `sleep`.
- `f17check.py`: every execve in the trace that follows an unshare or setns
  gives a version with the namespace step's labels.

**S. A subset of the media microservices**, trace 4, recorded like trace 3
(the six keys, auditd with `log_format = RAW`): `docker compose up -d
--no-deps` starts six of its containers at once (the DNS proxy, Jaeger,
OpenResty, user-service, its MongoDB and its memcached), with the stack's
`restart: always`, and 100 users are registered through OpenResty. The
ground truth is as in trace 3.

- S1: `getContainerInit()` has one start per run, with the init the kernel
  module recorded.
- S2: each run's boundary holds every process the sampler saw in it, and no
  process of another run; its only host neighbour is the runc process that
  created its init; and (F11) no version of its own processes labeled `-1`.
- S3: user-service's and OpenResty's network sockets reach their boundaries.
- S4: all three storages return the same results, for every query.
- S5: Not an expectation but a measurement: the trace's size, how long SPADE
  needs to store it, and the query times.

### Results

The three traces were recorded with `bug-fixes` at F1 to F10
(`BUG_FIXES.md`) and the container methods on top, the kernel module with
F7 and F9, and three storages attached; the replay of trace 3 at the end
used the build with F11 to F14 as well. Each trace
passed the completeness checks: the kernel's `lost` counter did not move,
and every event auditd logged reached the reporter. In trace 3, two records
of two events did not (Trace 3 below says which, and what they changed).

#### Trace 1 (B1-B7)

Recorded 2026-11-02 at 01:37 (the eleventh attempt), from
`clarity-of-container` at `b26bdf7e`. auditd logged 174,656 events and SPADE
read all of them. PostgreSQL, Neo4j and Quickstep each held 32,755 vertices
and 99,257 edges, and returned the same vertices and edges, and the same
errors, for all 19 queries.

`getContainerInit()` found 12 starts: two in 4026532410 (B1, B2), eight in
4026532474 (B4's outer container, the six B6 runs, B7), and one each in
4026532534 (B4's nested namespace) and 4026532472 (B5). Every init's host
PID matches the kernel's where the harness recorded it (B2, B4).

| Scenario | Expectation | Result |
|---|---|---|
| B1 | One start for X: stage 1 before and after its unshare, the unshare version's `children pid namespace` X, a fork with `CLONE_PARENT` to the init in X, the init's cgroup-unshare version, its execve to `sh` | Held, except the cgroup-unshare version: runc 1.3.3 creates the cgroup namespace before forking the init, so the init is born in it and never calls unshare (no UNSHARE record, no unshare syscall). The `children pid namespace` is X only with F7. |
| B1 | Nothing above stage 1, not the `sleep` child, no later execve, no file, no exit edge | Held. Not foreseen: the inits' setuid and setgid self-loops (67 of each) are in the result, since with `agents=false` they are versions (§4.1, corrected). |
| B1 | Boundary: the init's versions, `sh`, `sleep` and their files, the creating runc process as a neighbour, no host process further out | Held. Also members: runc init's threads (`runc:[2:INIT]`, 4 or 5 per container), created through glibc's `pthread_create`, that is clone3, and so recorded only with F9. |
| B2 | A start for the `sleep 60` container, nothing for the exec | Held: the second start in 4026532410 is the container's init, host PID 91666. |
| B2 | Boundary adds the exec'd `sh` and its `sleep`, with the entering runc process as a neighbour | Held: `('4026532410', 2)` holds both, and two `runc:[0:PARENT]` neighbours, the creator and the one that entered. |
| B3 | No start; the pre-existing shell (`-1`) is a neighbour; its `sleep`s are members; one container | Held: 23 `sleep`s (with their `sh` versions); the shell is the only outside neighbour; no numbered list. |
| B4 | Two starts, X by runc and Y by busybox `unshare` inside X | Held. |
| B4 | X's boundary includes Y's processes; Y's only its own | Held: `('4026532474', 1)` has 8 processes of X and 6 of Y; Y's boundary has its 6 and the creating `unshare` as a neighbour. |
| B5 | One start: the `unshare` step, its child, the child's execve to `sh` | Held, with B5 run through a root shell. Run directly under `sudo`, the `unshare` process is not recorded as created, and its unshare counts as a join (recording notes; `BUG_FIXES.md` N11). |
| B6 | The numbered list, one entry per container with start time and host PID; `(X, n)` exactly container n; `()` all | Held: 8 entries for 4026532474 and 2 for 4026532410; each numbered form returns one container's processes and its creator; `(X, 9)` and `(4026532410, 3)` fail with the out-of-range message. |
| B7 | A network artifact connected to `wget` (`netIO=true`) | Held: 14 network sockets, all connected to `wget`. |

#### Trace 2 (B8-B10)

Recorded 2026-11-02 at 01:48 with `namespaces=true` only. auditd logged
13,990 events and SPADE read all of them. The three storages returned the
same results for all 7 queries. B9 and B10 ran in the same PID namespace,
4026532324, one after the other, so the ID has two containers.

| Scenario | Expectation | Result |
|---|---|---|
| B8 | `getContainerInit()` fails, naming the reaper | Held: "1 container init process(es) never reached execve in this trace: bwrap (host pid 101821, pid namespace 4026532322)". The message's suggestion that the trace may have ended too early does not fit a sandbox whose PID 1 never calls execve (L10). |
| B8 | `getContainerBoundary()` still works and holds the sandbox's processes | Held: Y's boundary holds `bwrap`'s PID 1 and the `sh`, `readlink` and `sleep` it started, with the outer `bwrap` as a neighbour. |
| B9 | Does a network artifact reach the boundary with `netIO=false`? (§6) | Yes, partly. `('4026532324', 1)` holds 10 sockets joined to `wget` by `connect` and `close` only. With `netIO=true` (B7) the same download also had `send`, `recv`, `read` and `write` edges, and 4 more sockets, its DNS queries to 10.0.2.3:53, which use no `connect`. So with `netIO=false` a container's connections show, but no data that crossed them, and no unconnected UDP traffic. |
| B10 | The clone3 child is a member of X, created by an edge with `CLONE_VFORK` (`fork` with `simplify=true`) | Held: `('4026532324', 2)` has `spawn-child` -> `spawn-child` (the child, host PID 102064) with `fork`, flags `SIGCHLD|CLONE_VM|CLONE_VFORK`, then its execve to `true`. runc init's threads, also created with clone3, carry their flags (`CLONE_THREAD|CLONE_VM|...`). |

#### Trace 3 (M)

How it was recorded, before the results:

- **The first attempt** lost events: the bridge's queue reached its fixed
  1 GB within four minutes, and 163,482 events were missing by the end. Part
  of them may have been lost in the kernel, as in the next attempts; auditd's
  log of that window has since been rotated away. The stack produced about 10,000 audit events a second during
  registration and load, more than three times what SPADE handled with three
  storages attached. The queue size became an option (`BUG_FIXES.md` F8).
  Also, 7 of the 12 services had exited by the end: they had started before
  the names they connect to resolved, crashed, and were not restarted,
  because an earlier session of this work had set every container's restart
  policy to `no`. The harness now restores the stack's own `restart: always`
  during the trace, so restarts are part of the trace, and records every
  container start as it happens (`docker events`), each with its init and
  PID namespace: M1 is judged per run, not per container.
- **The second attempt** (queue 4 GB) lost events nonetheless: the bridge
  had counted 209,012 missing serial numbers by the end of the workload. The
  kernel counted 8,921 as lost (its backlog of 65,536 overflowed). I took the
  rest for drops by auditd's af_unix plugin, and for the third attempt raised
  the backlog limit to 262,144 and `net.core.wmem_default` to 128 MB, and ran
  the bridge's threads at nice -10. That reading was wrong; see the third
  attempt. This attempt's start watcher also recorded nothing: Docker 29's
  events have no `.ID`, so the template failed; it uses `.Actor.ID` now.
- **The third attempt** lost 354,825 events, and auditd's own log lacks every
  one of them, in 8 gaps of up to 184,343 serial numbers, during the bursts:
  33 containers starting at once, and the start of the load. auditd's own
  log for the second attempt lacks 288,838 (of the part not yet rotated
  away). So the events never reached auditd, yet the kernel counted only
  1,387 as lost. The cause is in Linux's `kernel/audit.c` (6.8):
  - `kauditd` waits at most 0.1 s (`sk_sndtimeo = HZ / 10`) for room in
    auditd's netlink socket, whose receive buffer is
    `net.core.rmem_default`, 212,992 bytes, when auditd starts. auditd has no
    setting for it.
  - After one failed send, `kauditd` gives up on the rest of the queue.
    Those records go to a retry queue, and when sends from that queue fail
    too, to `kauditd_hold_skb`.
  - `kauditd_hold_skb` drops the record without counting it unless the
    kernel was booted with `audit=1` (`if (!audit_default) goto drop;`).
    This VM's kernel was not, like a default Ubuntu install.

  auditd logged "Error receiving audit netlink packet (No buffer space
  available)" during the bursts, and its socket had counted 877 overruns
  since boot. The bridge's count of missing serial numbers (F8) is what
  showed the loss: the kernel's `lost` counter did not. For the fourth
  attempt, trace 3 restarts auditd while `net.core.rmem_default` is 512 MB,
  so that its socket can absorb the bursts, and logs auditd's CPU time and
  socket once a second (`monitor.log`). The bridge's nice -10 is gone; the
  128 MB send buffer stays.
- **The fourth attempt** (512 MB socket) lost more, 420,463 events. The
  socket filled within 13 s of `docker compose up` and stayed near full, and
  auditd's main thread used 205 s of CPU in about 200 s of tracing. auditd
  took about 3.4 million records in that time, so it spent some 60 µs on each
  (36 µs in system calls), which is far more than receiving and writing a
  record costs. The trace's events, by what produced them:
  - 51% syscall events, 61% of them from host processes: `dockerd` (22%,
    container logs), `docker-proxy` (13%, the published ports),
    `containerd-shim` (9%), the harness's own `docker` and `sudo` (3%);
  - 49% records of SPADE's kernel module: half netfilter packets (many of
    them DNS on the loopback), half intercepted network syscalls, of which
    `systemd` made 16%, answering userdb queries on
    `/run/systemd/userdb/io.systemd.DynamicUser` (170,000 messages).
- **The fifth attempt** kept auditd's log on a tmpfs, because auditd had at
  times written nothing for 10-20 s, which looked like waiting for the disk.
  It lost 598,972 events, and the monitor showed auditd's main thread never
  blocked on I/O (no `D` state). So the disk was not the cause either.
- **The cause** was auditd's `log_format = ENRICHED` (Ubuntu's default),
  which adds user and group names to each record. The services run as uids
  the host does not know (memcached's 11211, for one). auditd looks each of
  them up again for every record, through `/etc/nsswitch.conf` (`files
  systemd sss`), and does not remember the failure. Tested directly with 2,000
  events of a process running as uid 11211, auditd made 12,858 `openat` and
  839 `connect` calls; it made none for the known uid 65534, and none with
  `log_format = RAW`. The userdb queries `systemd` answered were these
  lookups, and SPADE recorded them as well. SPADE's reader takes only the raw
  fields, so the names it loses with RAW are ones it never read; but the
  input differs from traces 1 and 2, which were recorded with ENRICHED. The
  trace's own processes are unchanged.
- **The sixth attempt**, with `log_format = RAW` for the trace and the 512 MB
  socket (the tmpfs is gone), is the trace used below. No event was lost:
  the bridge counted no missing serial numbers, the kernel's `lost` counter
  did not move, auditd's socket never overran, and SPADE read all 2,489,681
  events in auditd's log. auditd averaged 73% of a CPU. The socket peaked at
  511 MB of its 512 MB, so the margin for the start of 33 containers was
  small.

  Three records were lost all the same, at 02:52:30, during registration:
  auditd logged "queue to plugins is full - dropping event" three times.
  When its queue to the af_unix plugin (2,000 records) stays full for about
  8 ms, auditd drops the record (BUG_FIXES.md N17). A record dropped from an
  event leaves the event's serial number in place, so neither the bridge's
  count nor the harness's comparison by serial number saw it, and the
  completeness check failed only on auditd's message. Comparing record by
  record (`compare-input.py` does that now) finds two of the three; the
  third was probably a record auditd does not write to its own log, such as
  EOE.

  | Serial | Process | Syscall | Records SPADE has of the event |
  |---|---|---|---|
  | 18,753,899 | `CastInfoService` (in a container) | clone (56) | PROCTITLE, and the kernel module's namespace record |
  | 18,754,126 | `docker-proxy` (host) | close (3) | PROCTITLE |

  The trace is used with these two records missing, rather than recorded a
  seventh time (another 80 minutes, most of it Quickstep's drain); the
  results below say where the missing clone shows. Traces 1 and 2, compared
  record by record as well, miss nothing (334,416 and 37,617 records).

Recorded 2026-11-02 at 02:51 (the sixth attempt), with SPADE built from
`1d6bb929`, whose code is that of the final build (later commits changed
only documentation and the harness). PostgreSQL, Neo4j and Quickstep each
held 777,310 vertices and 2,083,077 edges.

Three things about the ground truth differ from what the expectations
assumed:

- **Restarts.** The 33 containers ran 46 times: the first runs of 13
  services ended within seconds (in the first attempt such services logged
  "Error resolving address"), and Docker restarted each once. A crashed run
  was usually gone before `docker inspect` could name its init, and the
  start watcher then recorded the next run's init twice. So the runs come from the kernel module's own records in
  auditd's log (Part A checked those records against the kernel): every
  process created with ns pid 1 in a namespace other than the host's is a
  run's init, with its host PID and namespace.
- **A reused ID.** The kernel reused 4026532470: movie-id-service's first
  run (init host PID 310425, 1793587902.905) and, 2.2 s after it ended,
  nginx's first run (311812, 1793587905.097). The other 44 runs had a
  namespace each.
- **Members** are the processes the sampler saw in each container's cgroup
  with a nested PID (50), each placed in a run by its own NEWPROCESS record.
  The sampler also saw 22 processes of runc's in the cgroups, still in the
  host's PID namespace: runc's earlier stages, above the process that
  creates the init.

PostgreSQL ran every query: `getContainerInit()`, `getContainerBoundary()`,
and a boundary for each of the 46 runs (the 33 IDs Docker named, the 11
other namespaces of first runs, and the reused ID's two numbered forms, plus
its unnumbered form). Neo4j and Quickstep ran a subset of 12, since a Neo4j
boundary takes about four minutes (Neo4j also ran 4026532661 before its full
run was stopped): the two methods, the first five IDs, the
reused ID's three forms, a first run, and the run with the dropped clone
record (`query-trace3.sh`). `check-media.py` compares the results with the
ground truth.

| Expectation | Result |
|---|---|
| M1: one start per container, 33 in all, whose init is the one Docker names | Held per run: 46 starts for the 46 runs, each with the init the kernel recorded (host PID and namespace). The expectation said 33 because it did not foresee restarts. The starts overlap in time (33 inits within 2.4 s, all 46 within 10.2 s), and each went to its own namespace. |
| M2: each boundary holds every process the sampler saw in X, and no process of another container | Held: none of the 50 sampled processes is missing from its run's boundary, and no boundary holds a process labeled with another run's namespace. |
| M2: the only host neighbours are the runc processes that created or entered the container | Held: each of the 46 boundaries has exactly one host neighbour, the `runc:[0:PARENT]` whose clone made the init, found by the creation edge (runc creates the init with `CLONE_PARENT`, so its `ppid` names another runc process). Nothing entered a container. Not foreseen: the boundaries of the multi-threaded services also hold many versions of their own main process labeled `-1`, which the reporter created after each thread exit (BUG_FIXES.md N18, fixed as F11; the replay below has none): 4,663 for cast-info-service, 2,013 for movie-id-service, 1,000 for user-service, 651 for compose-review-service, 643 for rating-service, 299 for movie-info-service, 267 for plot-service, 3 for each MongoDB. The methods leave `-1` processes out of the members (D5); these come in as neighbours of the threads they start. |
| M2: no ID is used twice, so no numbered list | Did not hold, and the numbered list worked: `('4026532470')` fails with "PID namespace 4026532470 belonged to 2 containers in this trace, since the kernel reuses IDs", listing both runs with their start times and host PIDs, and `('4026532470', 1)` and `('4026532470', 2)` each hold exactly their run's processes. |
| M3: the services' network sockets reach their boundaries | Held: 46,760 sockets in nginx's boundary, 18,364 in cast-info-service's, 2,075 in movie-id-service's, 1,130 in movie-info-service's, 1,049 in plot-service's, 1,033 in user-service's, 716 in compose-review-service's; each first run that ended at once holds 8. Jaeger's boundary has none. |
| M4: all three storages return the same results | Held for the 12 queries all three ran: the same vertices and edges for the 11 graphs (from 199 vertices for `getContainerInit()` to 163,255 for `getContainerBoundary()`), and the same numbered-list error. |
| (the dropped records) | The clone record auditd dropped created thread 324795 of cast-info-service. Without it, the thread appears only in four network records of the kernel module, as first seen with `-1` labels, so it is in no boundary: one thread of the 18,200 member vertices of its run, and its four receives. The dropped `close` of `docker-proxy` is on the host. |

**M5, recording and storing.** One client at a time; the VM has 16 vCPUs
and 16 GB.

| What | Measured |
|---|---|
| Workload, reporter added to reporter removed | 175 s: `docker compose up` 10.4 s, registration 102.1 s, wrk2 30.0 s (652 requests, 21.7 a second) |
| Trace | 2,489,681 events, 4,143,402 records, 14,200 events a second on average; SPADE's `outputLog` 1.11 GB |
| auditd | 73% of a CPU on average, with `log_format = RAW`; its netlink socket peaked at 511 MB of its 512 MB |
| Bridge | queue peak 836 MB, nothing dropped, no serial number missing |
| Storing | about 2,700 events a second in the first minutes, 250 to 300 after 45 minutes, with Quickstep using three CPUs (BUG_FIXES.md N16); the bridge exited 4,013 s (67 minutes) after the reporter was asked to stop |
| On disk | PostgreSQL 612 MB, Neo4j 8.2 GB (its `spade.graph` directory, transaction logs included), Quickstep 1.1 GB |

**M5, queries,** in seconds from sending a query to the client's next
prompt (`times.tsv`). The 46 boundaries have 206 to 73,590 vertices (median
717) and 447 to 171,531 edges.

| Query | PostgreSQL | Neo4j | Quickstep |
|---|---|---|---|
| `getContainerInit()` (199 vertices, 756 edges) | 22.9 | 325.3 | 22.3 |
| `getContainerBoundary()` (163,255 vertices, 569,525 edges) | 21.9 | 104.6 | 3.4 |
| `getContainerBoundary('X')` and `('X', n)`: shortest, median, longest | 15.5, 17.1, 22.7 (46) | 209.7, 228.5, 236.5 (10) | 12.4, 12.8, 14.3 (9) |
| `getContainerBoundary('4026532470')`, the numbered-list error | 13.0 | 175.4 | 11.2 |
| `dump all` into a `.dot`, longest (the boundary of everything) | 75.7 | 73.6 | 74.9 |

On Neo4j a boundary takes about as long whatever its size (210 s to 237 s,
for boundaries of 216 to 41,644 vertices), which points at a fixed cost per
query, such as a scan of all processes for a namespace label.

#### Trace 3 replayed with F11 to F14

Trace 3 led to four more fixes on `bug-fixes` (BUG_FIXES.md): F11, a
thread's exit no longer ends its process's state (the unlabeled versions
above); F12, the Quickstep storage looks up a batch's vertices in one query;
F13, warnings for settings and bridge messages that mean lost records; F14,
indexes on the netfilter hooks' tables. To check them without recording
again, `replay.sh` stored trace 3 a second time, into fresh storages, from
the input the reporter read live (`spade-input.log`), with the trace's
arguments and `handleLocalEndpoints=true` as live. Without that, playback
leaves out the kernel module's network records (BUG_FIXES.md N20): a first
replay had no sockets at all. The replay used the build with all four fixes
(`583ec2d7`), and the same queries ran on it (`query.sh 3r`, `TRACE=3r
query-trace3.sh`, with Neo4j and Quickstep on the same 12).

| | Live trace | Replay with F11 to F14 |
|---|---|---|
| Input | the reporter read 2,489,681 events, 4,143,400 records (auditd dropped 2) | the reporter's live input, all of it read |
| Graph | 777,310 vertices, 2,083,077 edges | 753,993 vertices, 2,105,655 edges |
| Unlabeled versions of a boundary's own processes | 9,564 | none |
| `close` calls on a descriptor SPADE did not know | 30,881 | 9,665 |
| Sockets in the 46 boundaries | 72,139 | 72,283: more in the second runs of 8 multi-threaded services, from +1 (text-service) to +105 (cast-info-service) |
| M1 | 46 starts | the same |
| M2 | none of 50 missing; the init's creator the only host neighbour | the same |
| M4 | the same on the 12 queries all three ran | the same on the same 12: 11 graphs and the numbered-list error |
| Storing | 67 minutes after the workload ended | the reporter handled the input in 190 s; the storages had all of it 6.6 minutes after the replay started (PostgreSQL's last batch went out with the first query) |

So F11 removes the unlabeled versions and gives the multi-threaded services
back the operations on their descriptors that the live trace lost, with
every expectation still holding. Of the 23,317 fewer vertices, 9,564 were
the unlabeled versions in the boundaries; the others are in no boundary,
and with the live graph gone they were not examined further. A replay is
not a recording: the containers did the same, but the storing ran without
the workload beside it.

Query times on the replay, in seconds as in M5:

| Query | PostgreSQL | Neo4j | Quickstep |
|---|---|---|---|
| `getContainerInit()` | 21.3 | 315.7 | 5.5 |
| `getContainerBoundary()` | 21.8 | 132.6 | 1.5 |
| `getContainerBoundary('X')` and `('X', n)`: shortest, median, longest | 15.9, 17.9, 36.1 (46) | 219.6, 230.5, 264.1 (9) | 3.4, 3.6, 4.4 (9) |

PostgreSQL and Neo4j take as long as on the live trace (PostgreSQL's
longest boundary ran beside another query on the same database).
Quickstep answers three to four times faster than on the live trace; why
was not examined.

#### After F15 to F29: the traces recorded again

The expectations B11 to B13 and S1 to S5 above were committed before these
recordings (B13 after trace 1 was recorded again, which has no execve after
an unshare or setns). The recordings, with the harness of
`clarity-of-container`:

| Recording | Build | What it is for |
|---|---|---|
| `trace1d` | F1 to F27 (F27's first version) | B1 to B7, B12; found that trace 1 has no case for F17 |
| `trace2b` | the same | B8 to B11, B13; found F28 |
| `trace2br` | trace 2b replayed with F28 | B13a, B8 |
| `trace4` | F1 to F28 | S1 to S5 |
| `trace1e` | F1 to F29, after a reboot (a fresh kernel; N22) | B1 to B7, B12, F29 |
| `trace3n` | trace 3 replayed with F1 to F29, without `handleLocalEndpoints` | F16, F28, M1 to M4 |

Two earlier recordings of trace 1 (`trace1b`, `trace1c`) are not used: in
both the Quickstep server stopped taking connections after its first batch,
because the first version of F27 removed the main kernel module while the
server's accept thread was in a hooked `accept` (BUG_FIXES.md N22). They
showed the openat2 operation missing (fixed in F15) and led to the storage
check `trace.sh` now ends with. Every recording used below passed it: the
kernel's `lost` counter did not move, every event and record auditd logged
reached the reporter, and the three storages held the same graph.

| Recording | Events read | Graph (each storage) |
|---|---|---|
| `trace1e` | 152,957 events, 364,303 records | 42,425 vertices, 136,393 edges |
| `trace2b` | 37,192 events, 102,055 records | 10,849 vertices, 37,642 edges |
| `trace2br` | the same input | 10,827 vertices, 37,657 edges |
| `trace4` | 120,466 events, 278,417 records | 40,056 vertices, 122,462 edges |
| `trace3n` | trace 3's 2,489,681 events, 4,143,400 records | 754,109 vertices, 2,105,042 edges |

**Trace 1 (`trace1e`).** All 31 queries (the 7 of `query.sh` and the 12
numbered forms of each reused ID, past their ends included) returned the
same vertices, edges and errors on the three storages. After the reboot the
kernel numbered the namespaces differently: B1 and B2 ran in 4026532418, B4,
B6 and B7 in 4026532482, B4's nested namespace was 4026532542 and B5's
4026532480.

| Scenario | Result after F15 to F29 |
|---|---|
| B1, B2 | Held as before: `getContainerInit()` has 12 starts, two in 4026532418; `('4026532418', 1)` has 12 processes with the creating runc process as the only host neighbour, `('4026532418', 2)` 19 with the entering runc process as a second; `(…, 3)` fails. |
| B3 | Held: the pre-existing shell is the only neighbour of its 40 members (the count depends on how many `sleep`s the loop starts in the trace). |
| B4 | Held: `('4026532482', 1)` holds 9 processes of X and the 6 of Y; Y's boundary only its own. |
| B5 | Held: 7 processes, the `unshare` process the only neighbour. |
| B6 | Held: 8 runs of 4026532482 numbered, each run its own processes, `(…, 9)` fails. |
| B7 | Held: run 8 has 14 network sockets, all joined to `wget`. |
| B12 | Did not hold as written. runc's openat2 and close_range are recorded and handled (2,051 openat2 calls, 614 of them giving `open` edges to runc's files, which no earlier trace had; 158 close_range calls), and execve drops close-on-exec descriptors. But 1,437 openat2 calls, and 828 openat and 263 mknodat calls, all of runc, still name a directory descriptor the reporter does not know. `nofd.py` and strace show where they come from: runc's procfs and overlay handles, which it gets from fsmount, a mount without a path, and the rules do not audit fsopen, fsmount or open_tree (BUG_FIXES.md N21; CLARITY_METHODS.md L20). Of the 3,372 closes of unknown descriptors, 2,153 follow an open relative to such a handle and 1,208 were created by other syscalls the rules leave out (epoll, eventfd, pidfd, ...); 11 remain. |
| F25 | The module's 14,433 network records name their processes without NUL padding; trace 1's first recording had padding in all 13,022. |
| F29 | No module record from the audit bridge; `trace1d`, before F29, had 25, its reader thread's `recvfrom` calls. |

**Trace 2 (`trace2b`, `trace2br`).** IDs were reused twice: B9, B10 and
B13b's container all ran in 4026532324, and B11 and B13a in 4026532203. The
12 queries returned the same results on all three storages, live and
replayed.

| Scenario | Result after F15 to F29 |
|---|---|
| B8 | Held in the replay: `getContainerInit()` fails naming only bubblewrap's init (`bwrap`, 4026532322), as in the first trace 2, and Y's boundary holds `bwrap`'s PID 1 and the `sh`, `readlink` and `sleep` it started. Live, before F28, it also named B13a's init (below). |
| B9 | Held as before: `('4026532324', 1)` holds 10 sockets joined to `wget`. |
| B10 | Held as before: `('4026532324', 2)` has `spawn-child`, its clone3 child and the child's execve to `true`. |
| B11 | Held: 4026532203's first run is a start whose first process is the `unshare` child, with the `unshare` process, which sudo started for SPADE's user, as its only host neighbour; before F18 that process was not created in the trace and its unshare counted as a join (L3). |
| B13 | Held, after F28. `f17check.py`: both execve calls after a namespace step give versions with that step's labels, (a)'s `children pid namespace` 4026532203 and (b)'s `mount namespace` the container's, not the labels they were created with. Live, `('4026532203', 2)` held B13a's init only as `sh`, and `getContainerInit()` failed on it: dash had created the init with vfork, whose record comes after the child's execve, and the reporter took the child for one first seen, with `-1` labels. That is F28: in the replay with it, the run holds the init and its execve to `sleep`, and `getContainerInit()` fails only on B8's bubblewrap. |

**Trace 4 (`trace4`), the media microservices' subset.** Six containers
started at once with `docker compose up -d --no-deps`, none restarted, and
98 of the 100 users were registered; it took 3 minutes to record, where
trace 3 took hours. `check-media.py`:

| Expectation | Result |
|---|---|
| S1: one start per run, with the init the kernel module recorded | Held: 6 starts, 6 inits in auditd's log, all 6 the same. |
| S2: each boundary holds every sampled process of its run and none of another; the init's creator is its only host neighbour; no unlabeled version of its own processes | Held: none of the 10 sampled processes missing; every boundary's only host neighbour is the runc process that created its init; no unlabeled versions. |
| S3: user-service's and OpenResty's sockets reach their boundaries | Held: 30 sockets in user-service's boundary and 131 in OpenResty's (nginx-web-server); 1 in the DNS proxy's, 2 in MongoDB's, 1 in memcached's, none in Jaeger's. |
| S4: the same results on every storage | Held: 8 graphs and no differences. |
| S5: a measurement | 40,056 vertices and 122,462 edges, stored within the 70 s the harness waits; `getContainerInit()` 1.7 s on PostgreSQL, 3.1 s on Quickstep and 36.1 s on Neo4j, a boundary 1.1 s, 1.9 to 2.7 s and 20.8 to 21.1 s. |

**Trace 3 replayed (`trace3n`).** `GIVE_HANDLE=0 replay.sh 3 3n`: trace 3's
input, with F1 to F29 and without `handleLocalEndpoints`, then `query.sh` and
`query-trace3.sh` on PostgreSQL (`SUBSET=0`: the Neo4j subset takes an hour
on this graph, and the storages' agreement is shown on traces 1, 2 and 4).

| Expectation | Result |
|---|---|
| F16 | Held: playback set `handleLocalEndpoints` to false at a network SYSCALL record and to true at the next network record of the kernel module, and the 46 boundaries hold 72,283 sockets, exactly as the replay that passed it (trace 3 replayed with F11 to F14); a replay without it had none before F16. |
| M1 | Held: 46 starts, 46 inits in auditd's log, the same 46. |
| M2 | Held: none of the 50 sampled processes missing from its run's boundary; every boundary's only host neighbour is the runc process that created its init; no unlabeled versions; 29,403 members in all, as in the earlier replay. |
| M2, the reused ID | Held: `('4026532470')` fails and asks for a number; its two runs are movie-id-service's first and nginx-web-server's first. |
| M3 | Held: 46,760 sockets in OpenResty's second run, 18,469 in cast-info-service's. |
| F28 | 3,933 events of 537 processes waited for their creation, 2 in vain; the boundaries are as before F28, so trace 3's vforked processes were on the host. |
| M5 | The reporter handled the input in 173 s. On PostgreSQL, `getContainerInit()` 17.6 s, `getContainerBoundary()` 18.6 s, a boundary 11.2 to 19.2 s (median 13.6 s, 48 boundaries). |

**What this shows.** With F15 to F29, every result of the first recordings
stands: B1 to B10 on traces 1 and 2, and M1 to M4 on trace 3, replayed. The
new scenarios hold too (B11, B13, S1 to S5), except B12 as written, whose
remaining descriptor gaps come from runc's mount-API handles (N21, L20).
Two fixes came out of these recordings themselves: F28, a vfork child seen
before its creation, which made B13a's start fail, and F29, the module's pid
filters naming threads. A first version of F27 crashed threads (N22) and was
replaced before the recordings above.

## Part C: the ContainerLabels transformer on real traces

The maintainer asked (2026-11-03) for the two QuickGrail methods to become
one SPADE transformer, `ContainerLabels`, that labels container processes in
memory (`CLARITY_METHODS.md`). Part C checks it on the recordings of Part B,
replayed into fresh storages with the transformer's build (`87e38e49`; the
reporter, the module and the storages are unchanged since F29, so a replay
gives the graph a recording would). Traces 1, 2 and 4 are replayed and
queried on all three storages; trace 3, whose replay takes hours, on
PostgreSQL only if at all.

The expectations below were written and committed before the replays. They
follow `CLARITY_METHODS.md` §1 and §5 as rewritten for the transformer.

**Ground truth.** The kernel module's NEWPROCESS records in auditd's log,
which Part A checked against the kernel: for every process created in the
trace, its host pid, its pid in its own namespace and its PID namespace. A
container run is a record with ns pid 1 in a namespace other than the host's;
runs of a reused ID are told apart by time. Traces 3 and 4 add the cgroup
sampler's processes. `query.sh` runs `$labeled = $base.transform(ContainerLabels, '')`
on each storage and exports the labeled vertices, every process vertex of
the copy, and every network socket with its neighbours; `check-labels.py`
compares them with the ground truth.

| # | Expectation |
|---|---|
| C1 | One `container_id` per kernel run whose init's creation is in the trace, naming the run's namespace and the time and event id of that creation; the init is labeled. No `container_id` without a kernel run. |
| C2 | Every process version that the kernel recorded as created in a run is labeled with that run's `container_id`, and so is every sampled process (traces 3 and 4). A version the reporter first saw without its creation is not linked to the start and stays unlabeled; none is expected in these traces, whose input was complete. |
| C3 | Nothing else is labeled: no host process (runc's stages, the shim, `unshare`, `bwrap` outside), no process of B3's container, which started before tracing (a change from the methods, where it was a container "running when tracing started"), and no process with another run's label. |
| C4 | A labeled socket's processes all have its label and its `net namespace`. B7's 14 sockets and B9's 10, used only by `wget` in its own network namespace, are labeled. For the other sockets next to container processes, the reason each is left is reported; no rule is broken. |
| C5 | The same labeled vertices, with the same `container_id`s, on every storage. |
| C6 | A measurement: the transform's time on each storage, which exports the graph, labels it in memory, and puts the labeled copy back. |

Per scenario, these mean: B1 one container per `docker run`, its init, `sh`
and `sleep` labeled and runc's stage 1 not; B2 the `docker exec`'d `sh` and
`sleep` labeled with the `sleep 60` container's ID; B3 nothing labeled; B4 X's
processes with X's ID and the nested Y's with Y's; B5 the `unshare` child and
its `sh`; B6 eight different IDs for the eight runs of the reused namespace;
B8 bubblewrap's sandbox labeled, where `getContainerInit()` failed; B10 the
clone3 child; B11 the container started through sudo; B13 the processes
after an unshare or setns and execve. S1 to S3 and M1 to M3 become C1, C2
and C4 on traces 4 and 3.
