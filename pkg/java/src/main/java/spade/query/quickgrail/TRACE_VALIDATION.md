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
daemon creates. PostgreSQL and Neo4j storages are both attached, so each query
runs on both against the same records, and the two results must agree.

| Trace | Audit reporter arguments | Scenarios |
|---|---|---|
| 1 | the six keys `cfg/spade.reporter.Audit-with-namespace-awareness.config` changes: `namespaces=true fileIO=true netIO=true IPC=true inode=true networkAddressTranslation=true` | B1–B7 |
| 2 | `namespaces=true` only, so `netIO` stays off | B8, B9 |

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

### Results

To be filled in from the traces.
