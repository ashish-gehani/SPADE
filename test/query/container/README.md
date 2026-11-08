## Container label checks on real traces

Scripts that record real provenance traces of containers with SPADE and check
the `ContainerLabels` transformer's labels against the kernel's own view. The
scenarios, what each should show, and the results are in
`pkg/java/src/main/java/spade/query/quickgrail/TRACE_VALIDATION.md`, Parts B
and C; the fixes the traces needed are in `BUG_FIXES.md` beside it.

__Requirements__:

* A disposable Linux VM (the checks were run on Ubuntu 24.04 with Linux 6.8):
  the scripts load SPADE's kernel modules, change audit and socket settings
  for the duration of a trace, and start and remove containers.
* SPADE built with `make KERNEL_MODULES=true`, then `bin/allowAuditAccess`
  (again after every build: `make` replaces `bin/spadeAuditBridge` without its
  setuid bit), auditd with its af_unix plugin, PostgreSQL with SPADE's
  database, and Quickstep (`bin/installQuickstep`).
* Docker with the `alpine` image, `bwrap`, `gcc` with static glibc, Python 3.
* For traces 3 and 4, DeathStarBench's `mediaMicroservices` in
  `~/DeathStarBench`, with its containers created and stopped; for trace 3
  also its Python environment `myenv`, and `wrk2` built.
* Run the scripts as the user SPADE runs as. The audit rules leave that
  user out, so the scenarios run under `sudo` or inside containers.

__Steps__:

* `./trace.sh 1|2|3|4 [NAME]` records one trace into `~/stage-b/trace<N>/`,
  or `trace<NAME>/` (e.g. `1b`, to keep an earlier recording): trace 1 is
  B1-B7, trace 2 is B8-B11 and B13 with `netIO` off, trace 3 is the media
  microservices, and trace 4 is six of their containers with a small
  workload (S), which takes minutes where trace 3 takes hours. It starts a fresh SPADE, PostgreSQL, Neo4j and Quickstep
  storage, records the kernel's ground truth in `truth.txt`, and ends with a
  completeness check: a trace is used only if the line reads
  `COMPLETENESS: ok`. Traces 3 and 4 change auditd's settings for their duration
  (see below) and puts them back at the end, also when it stops early; it
  also writes `monitor.log`, auditd's CPU time and socket once a second.
* `./query.sh <N>` runs the `ContainerLabels` transformer on all three
  storages (`$labeled = $base.transform(ContainerLabels, '')`) and exports
  the labeled vertices, every process vertex of the labeled copy, and every
  network socket with its neighbours as `.dot` files, timing each command
  (`times.tsv`). Query a trace before recording the next: the storages are
  reset when a trace starts. `STORAGES` picks the storages (trace 3:
  `STORAGES=PostgreSQL`), and `QUERY_TIMEOUT` limits each command (900 s by
  default).
* `./replay.sh <N> [NAME]` stores a recorded trace again, into fresh storages,
  from the input the reporter read live (`spade-input.log`), as `trace<N>r`
  (or `trace<NAME>`), with the trace's ground truth linked next to it. It
  checks reporter, storage and transformer changes on a trace without
  recording it again; query and check the replay by its name
  (`./query.sh 4r`). It passes `handleLocalEndpoints` as the live trace had
  it, since playback without F16 may drop every network record of the kernel
  module (BUG_FIXES.md N20); `GIVE_HANDLE=0` leaves it out, to check F16.
* `./check-labels.py [dir]` compares the labels with the ground truth and
  across storages: the kernel module's records of every process it created,
  in auditd's log, and for traces 3 and 4 the cgroup sampler's processes,
  since Docker restarts crashed services faster than it can be asked about
  them.

__Files__:

| File | Does |
|---|---|
| `trace.sh` | Records a trace; see the comments for every setting it changes and why |
| `query.sh` | Runs the transformer on every storage and exports the labels |
| `timed-client.py` | Used by `query.sh`: sends the queries to `bin/spade query` one at a time and times each (`times.tsv`) |
| `replay.sh` | Stores a recorded trace again from the reporter's saved input, for checking fixes without recording again |
| `check-labels.py` | C1-C6 (TRACE_VALIDATION.md, Part C): one container per kernel run, every process the kernel created in a run (and every sampled one) labeled with it and nothing else, the socket rule, storage agreement, the transform's time |
| `dotsum.py` | Summarizes a `.dot` export; used by the checkers |
| `fdcheck.py` | B12: the reporter's warnings about descriptors it does not know, by process and syscall |
| `nofd.py` | B12: which of those descriptors were created by a recorded syscall (a gap in SPADE) and which by one the rules leave out, and what the reporter logged at the creation |
| `f23check.sh` | F23: replays a trace's input into Quickstep alone, at its default batch interval, and asks for the counts as soon as the input is read |
| `f17check.py` | B13: whether every execve after an unshare or setns gives a version with the namespace step's labels (in PostgreSQL) |
| `compare-input.py` | Compares auditd's log with everything the Audit reporter read, by serial number and, within each event, by record type |
| `cgroup-sampler.py` | Traces 3 and 4's ground truth: which processes ran in which container, from `/proc/<pid>/cgroup` |
| `spawn-child.c` | B10: starts a child with `posix_spawn`, which static glibc does with clone3 |
| `af-unix-capture.py` | Diagnostic only: a second reader of auditd's af_unix socket (the plugin serves one client, so not during a trace) |

__Things that silently spoil a trace__ (each is handled by `trace.sh`):

* SPADE replays its saved configuration (`cfg/spade.client.Control.config`)
  at start, `reset=true` included.
* auditd's af_unix plugin drops events its client cannot take at once; raise
  `net.core.wmem_default` before the bridge connects.
* The audit backlog limit (8192 on Ubuntu) overflows during container starts.
* When auditd falls behind for 0.1 s, the kernel drops the records it holds
  without counting them as lost, unless it was booted with `audit=1`; only
  the bridge's count of missing serial numbers shows it. auditd's netlink
  socket takes `net.core.rmem_default` as its buffer when auditd starts, so
  trace 3 restarts auditd with 512 MB.
* With `log_format = ENRICHED` (Ubuntu's default), auditd looks up every
  unknown uid of every record through NSS, which made it too slow for the
  media microservices, whose services run as uids the host does not know.
  Trace 3 is recorded with `log_format = RAW`.
* auditd drops single records on their way to the plugin when its queue to
  the plugins stays full ("queue to plugins is full - dropping event", only
  the first five reported). The event's serial number stays, so only a
  comparison record by record (`compare-input.py`) finds them.
* udev's and PostgreSQL's own activity floods the audit stream; they are left
  out with `ignoreProcesses` and `ignoreParentProcesses`.
* Without BUG_FIXES.md F18, a process that `sudo` forks for SPADE's own user
  is not recorded as created; run such commands through `sudo sh -c '...'`.
* Removing the main kernel module (`rmmod spade_audit`) crashes every thread
  then in one of its hooked system calls (BUG_FIXES.md N22); the Quickstep
  server can lose its accept thread that way, and the Quickstep storage then
  waits for ever. Never remove it while processes run; reboot to load a
  rebuilt module (the reporter warns when the loaded one is another build).
  `trace.sh` and `replay.sh` check that every storage answers with the same
  counts.
* Do not edit `trace.sh` while it runs: bash reads a script as it goes.
