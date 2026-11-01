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
| Code | branch `clarity-of-container` at `9d6e210b`, based on `fix-ns-pid` `d445c7cc` and `prov-query` `6b744f7c` |

Three containers left over from an unrelated benchmark stack restarted in a
loop during the runs (one crashed with SIGSEGV each time). Their records were
judged like any other; they are why most scenario windows hold hundreds of
records.

## Part A: the kernel module's namespace records

Q2 asks for `ns pid` to be the PID of a process inside its own PID namespace.
Branch `fix-ns-pid` has two commits for that (`CLARITY_METHODS.md` §4.1):

- `9fd54e0c` records `ns_ns_pid` in the process's own PID namespace instead of
  the clone/fork/vfork return value, which is the PID in the creator's
  namespace.
- `d445c7cc` makes the unshare and setns hooks look up the caller by its PID in
  its own namespace instead of its host PID.

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
`current->real_parent->pid`, both host PIDs. `fix-ns-pid` left that path alone
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

The harness is six small scripts: the `/proc` sampler, the scenarios, a
runner that loads one module variant and collects its slice of the audit log,
the analyzer, the filter check and the clone3 check. They are not in the
repository yet.

## Part B: the methods on real traces

To do. SPADE runs in the same VM with the Audit config `prov-query` added in
`6b744f7c`, `cfg/spade.reporter.Audit-with-namespace-awareness.config`. It sets
`namespaces=true` and also `fileIO`, `netIO`, `IPC`, `inode` and
`networkAddressTranslation`, which the default config leaves off. For each
scenario, the expected output of `getContainerInit()` and
`getContainerBoundary()` is written down before the trace is looked at, and
the methods' output is then compared with it.
