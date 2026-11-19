# Container Labels: Validation on a Live System

Companion to `CLARITY_METHODS.md`. Part A checks the audit kernel module's
namespace records, which container labeling reads, against the kernel. Part B
describes the real traces. Part C checks the `ContainerLabels` transformer's
labels on them against the kernel's own record of every process it created.

## Environment

| | |
|---|---|
| Machine | VirtualBox VM from the Vagrant box `bento/ubuntu-24.04` 202508.03.0, 16 vCPUs, 16 GB RAM |
| OS | Ubuntu 24.04.3 LTS, Linux 6.8.0-146-generic, gcc 13.3.0, glibc 2.39 |
| Containers | Docker 29.0.0, containerd v2.1.5, runc 1.3.3 |
| Tools | util-linux 2.39.3 (`unshare`, `nsenter`), bubblewrap, systemd 255, auditd 3.1.2, Python 3.12.3 |
| Code | `clarity-of-container`: the transformer on `bug-fixes` (F1 to F38) and `prov-query` `6b744f7c`; the kernel module with F1 to F29 loaded |

SPADE runs with a heap of at most 8 GB (`--mem-max 8g`).

## Part A: the kernel module's namespace records

**Method.** `pkg/linux/kernel_modules/test/ns-pid/` builds the module twice,
*base* and *fixed*, runs the same scenarios under each, and reads the
records from auditd's log, so the module is the only thing that differs. A
sampler reads every process's `NSpid`, PID namespace and `pid_for_children`
from `/proc` every 50 ms. Each record's subject (the child for `NEWPROCESS`,
the caller for `UNSHARE` and `SETNS`) is matched by host PID and time, and
its `ns_ns_pid`, `ns_inum_pid` and `ns_inum_pid_children` are compared with
what `/proc` showed. A subject the sampler missed is unverified. The audit
backlog is 65536 and the kernel's `lost` counter is checked, so a missing
record means the module never wrote it.

| # | Scenario |
|---|---|
| S1 | `sh -c 'sleep 1; true'` on the host |
| S2 | `unshare --pid --fork sleep 2` |
| S3 | `unshare --pid --fork --mount-proc sh -c 'sleep 2 & sleep 2 & wait'` |
| S4 | `nsenter --target <init> --pid` from the host into S3's namespace |
| S5 | inside a namespace, `unshare --pid --fork` and `nsenter --pid` |
| S6 | `docker run --rm alpine sh -c 'sleep 1; true'` |
| S7 | `docker exec` into a running alpine container |

**Result** (2026-11-02; *fixed* is the module with F1 to F26, *base*
`583ec2d7`): 395 namespace records, 379 correct, **0 wrong**, 16 unverified
(callers and children that ended before the sampler saw them); no wrong
`ns_inum_pid_children`; the `lost` counter did not move. F29 changed only the
module's pid filters: `filter-test.sh` shows that a pid filter on a container
init's host pid leaves its records out, one on its in-namespace pid does not,
and a ppid filter on `containerd-shim` leaves out its children.
`clone3-test.sh` shows a child created with clone3 (glibc's `posix_spawn`)
recorded with `ns_syscall=435` and its `ns_clone_flags` (F9). `BUG_FIXES.md`
F1, F2, F7, F9 and F29 give what modules without those fixes record.

## Part B: the traces

SPADE runs in the VM as user `vagrant`, with the kernel module, and PostgreSQL,
Neo4j and Quickstep storages attached, so every query runs on all three
against the same records. SPADE leaves out its own user's activity, so the
scenarios run under `sudo` or in containers. `test/query/container/` holds the
scripts (its README lists what they need and what spoils a trace): `trace.sh`
records a trace and the kernel's ground truth, `replay.sh` stores a recorded
trace again from the reporter's saved input, `query.sh` runs the transformer
on every storage, and `check-labels.py` compares its labels with the ground
truth.

A trace is used only if it is complete: the kernel's `lost` counter did not
move, every event and record auditd logged reached the reporter (compared by
serial number and record type), no storage write failed, and the three
storages hold the same graph.

| Trace | Audit reporter arguments | Scenarios |
|---|---|---|
| 1 | `namespaces=true fileIO=true netIO=true IPC=true inode=true networkAddressTranslation=true` (what `cfg/spade.reporter.Audit-with-namespace-awareness.config` sets) | B1 to B7 |
| 2 | `namespaces=true`, so `netIO` is off | B8 to B11, B13 |
| 3 | as trace 1 | M |
| 4 | as trace 1 | S |

X stands for a container's PID namespace ID, Y for a second one.

- **B1.** `docker run --rm alpine sh -c 'sleep 1; true'`.
- **B2.** `docker exec` into a container `docker run -d alpine sleep 60`
  started during tracing.
- **B3.** A container started before tracing, whose shell forks a `sleep`
  every two seconds.
- **B4.** A PID namespace created inside a container: `docker run --rm
  --privileged alpine unshare -p -f -m --mount-proc sh -c 'sleep 1; true'`.
- **B5.** `sudo sh -c 'unshare --pid --fork --mount-proc sh -c "sleep 1; true"'`
  on the host.
- **B6.** Short containers one after another, each gone before the next
  starts, so the kernel reuses their namespace ID.
- **B7.** `wget` from an alpine container, with `netIO=true`.
- **B8.** bubblewrap: `sudo sh -c 'bwrap --unshare-pid --dev-bind / / sh -c
  "sleep 1; true"'`. Its PID 1 is its own reaper and never calls execve.
- **B9.** B7 again with `netIO=false`.
- **B10.** In an alpine container, a static glibc program starts `/bin/true`
  with `posix_spawn`, which glibc 2.39 does with clone3.
- **B11.** `sudo unshare --pid --fork sh -c '...'`, run directly by SPADE's
  user (F18).
- **B13.** From a root shell: (a) `unshare --pid sh -c 'sleep 2; true'`
  without `--fork`, so `sh` stays on the host and its children start in the
  new namespace; (b) `nsenter -t <init> -m sh -c 'true'` into a running
  container, a setns and then an execve (F17).
- **M.** DeathStarBench's media microservices: its 33 containers started with
  `docker compose up -d`, its movie and user data written through its own
  scripts, then 30 s of `wrk2` composing reviews at 20 requests a second.
  Docker restarts the services that crash, so the 33 containers run 46 times. auditd logs with `log_format = RAW`
  (`BUG_FIXES.md` N15). A sampler records which processes ran in each
  container's cgroup.
- **S.** Six of those containers (the DNS proxy, Jaeger, OpenResty,
  user-service, its MongoDB and its memcached) started at once with `docker
  compose up -d --no-deps`, and 100 users registered through OpenResty, as in
  M.

| Recording | Kernel module | Input | Graph (each storage) |
|---|---|---|---|
| `trace1e` | with F1 to F29 | 152,957 events, 364,303 records | 42,425 vertices, 136,393 edges |
| `trace2b` | without F29 | 37,192 events, 102,055 records | 10,827 vertices, 37,657 edges |
| `trace3` | without F18 and F24 to F29 | 2,489,681 events, 4,143,400 records | 754,107 vertices, 2,105,041 edges |
| `trace4` | without F29 | 120,466 events, 278,417 records | 40,056 vertices, 122,462 edges |

Traces 1, 2 and 4 passed the completeness check. In trace 3, auditd's queue to
its plugins dropped three records, two of them SYSCALL records
(`BUG_FIXES.md` N17); every event reached the reporter. The ground truth of
Part C is auditd's own log, which has them. The module fixes a recording
lacks do not matter for these traces: F18 only when SPADE's own user starts
containers through sudo, F24 to F26 are a counter, process names (which F4
handles in the reporter) and safety, and F29 only keeps SPADE's own processes
out of the module's records.

**Descriptors.** runc's `openat2` and `close_range` are recorded and handled
(F15): in `trace1e`, 2,051 `openat2` calls, 614 of them giving `open` edges
to runc's files, and 158 `close_range` calls. 1,437 `openat2`, 828 `openat`
and 263 `mknodat` calls of runc still name a directory descriptor the
reporter does not know: runc gets its procfs and overlay handles from
`fsmount`, a mount without a path, and the audit rules leave `fsopen`,
`fsmount` and `open_tree` out (`BUG_FIXES.md` N21). Those files are missing
from the graph; labels are not affected (`CLARITY_METHODS.md` L16).

## Part C: the transformer on the traces

The recordings of Part B are replayed into fresh storages with this build
(`replay.sh`, from the input the reporter read live), and `query.sh` runs
`$labeled = $base.transform(ContainerLabels, '')` on each storage and exports
the labeled vertices, every process vertex of the copy, and every network
socket with the processes and edges next to it.

**Ground truth.** The kernel module's `NEWPROCESS` records in auditd's log
(checked in Part A): every process created in the trace, with its host pid,
its pid in its own namespace and its PID namespace. A container run is a
record with ns pid 1 in a namespace other than the host's; runs of a reused
ID are told apart by time. Traces 3 and 4 add the cgroup sampler's processes.

The expectations were committed before the replays of traces 1, 2 and 4;
trace 3 is checked against the same ones:

| # | Expectation |
|---|---|
| C1 | One `container_id` per kernel run whose init's creation is in the trace, naming the run's namespace and the time and event id of that creation; the init is labeled. No `container_id` without a kernel run. |
| C2 | Every process version that the kernel recorded as created in a run is labeled with that run's `container_id`, and so is every sampled process (traces 3 and 4). |
| C3 | Nothing else is labeled: no host process (runc's stages, the shim, `unshare`, `bwrap` outside), no process of B3's container, which started before tracing, and no process with another run's label. |
| C4 | A labeled socket's processes all have its label and its `net namespace`. B7's and B9's sockets, used only by `wget` in its own network namespace, are labeled. For the other sockets next to container processes, why each is left is reported. |
| C5 | The same labeled vertices, with the same `container_id`s, on every storage. |
| C6 | A measurement: the transform's time on each storage. |

Per scenario: B1 one container per `docker run`, its init, `sh` and `sleep`
labeled and runc's stage 1 not; B2 the exec'd `sh` and `sleep` with the
`sleep 60` container's ID; B3 nothing; B4 X's processes with X's ID and Y's
with Y's; B5 the `unshare` child and its `sh`; B6 a different ID for each run
of the reused namespace; B8 bubblewrap's sandbox; B10 the clone3 child; B11
the container started through sudo; B13 the processes after the unshare or
setns and execve.

**Results** (2026-11-08). Each replay was complete: the three storages held
the recording's graph (Part B's table).

| | Trace 1 | Trace 2 | Trace 3 | Trace 4 |
|---|---|---|---|---|
| C1: kernel runs, matched by a `container_id` | 12, 12 | 6, 6 | 46, 46 | 6, 6 |
| C2: process versions labeled with their run; missing | 109; 0 | 45; 0 | 29,645; 0, and all 50 sampled processes in a container's PID namespace (the other 22 sampled are runc's stages on the host) | 167; 0, and all 10 sampled processes |
| C3: versions labeled that should not be | 0; B3's 40 versions unlabeled | 0 | 0 | 0 |
| C4: sockets labeled; breaking the rule; left next to a container process | 14 (B7); 0; 0 | 10 (B9); 0; 0 | 72,283 in 45 containers; 0; 0 | 165 in 5 containers; 0; 0 |
| C5: the same labels on every storage | yes | yes | yes | yes |
| C6: `transform` on PostgreSQL, Quickstep, Neo4j | 11 s, 12 s, 31 s | 3 s, 4 s, 13 s | 372 s, 292 s, 594 s | 10 s, 12 s, 29 s |

Exporting the graph and labeling it took 5 to 10 s on traces 1, 2 and 4, and
37 to 71 s on trace 3; the rest of C6 is QuickGrail storing the copy
(`CLARITY_METHODS.md` L15).

Per scenario, from C1 and C2: B1's container and B2's `sleep 60` container
are the two runs of 4026532418, each with its own ID, and B2's exec'd `sh`
and `sleep` have the second's; B4's X (4026532482, run 1) and the nested Y
(4026532542) each have their own; B5's namespace is labeled and the host's
`unshare` is not; B6's eight runs of 4026532482 have eight IDs; bubblewrap's
sandbox (B8, 4026532322) is labeled; B10's clone3 child, B11's container and
B13's processes are labeled with their runs. In M, the 46 runs of the 33
containers have 46 IDs, the two runs of 4026532470 included.

**Transforming again.** Trace 2 was transformed a second time on PostgreSQL,
after `$base` held the first transform's labeled copy (10,882 vertices,
38,731 edges): the same labeled vertices, `$labeled` again 10,827 vertices
and 37,657 edges, and `$base` unchanged.

**What this shows.** On four real traces and all three storages, the labels
match the kernel's record of every process created in every container run,
nothing else is labeled, the socket rule holds, and a second transform
changes nothing.
