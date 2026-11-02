# ns pid checks for the audit kernel module

Checks the namespace records the audit module writes (`ns_NEWPROCESS`,
`ns_SETNS`, `ns_UNSHARE`) against the kernel's own view in `/proc`, and
compares a module built without a change to one built with it. Written to
validate three fixes:

- `ns_ns_pid` must be the process's PID in its own PID namespace (`9fd54e0c`).
- unshare and setns calls from inside a non-host PID namespace must be
  recorded, with the caller's labels (`d445c7cc`).
- `ns_inum_pid_children` must name the namespace the caller's children go
  into, also right after unshare(CLONE_NEWPID), and an unreadable namespace
  is written as -1 (`2728115f`).
- a process created with clone3 must get a record, with its flags in
  `ns_clone_flags` (`cea5d23c`).

Records are read from auditd's log, not through SPADE, so the module is the
only thing that differs between the two runs. A sampler reads every process's
`NSpid` and namespace inodes from `/proc` every 50 ms, and each record is
judged against it.

## Results

Ubuntu 24.04.3, Linux 6.8.0-146-generic, VirtualBox, Docker 29.0.0 with runc
1.3.3. The audit `lost` counter did not change in any run.

### `ns_ns_pid` and the unshare/setns hooks (2026-11-01)

*base* was built from `prov-query` `6b744f7c`, *fixed* from the same tree with
`9fd54e0c` and `d445c7cc`. The analyzer did not judge `ns_inum_pid_children`
yet.

| | base | fixed |
|---|---|---|
| Namespace records | 2211 | 2237 |
| Wrong | 25 | 0 |
| Unverified (exited before the sampler saw it) | 1 | 1 |

- All 25 wrong base records are `NEWPROCESS` records holding the child's PID
  in its creator's namespace: 18 container inits (`runc:[2:INIT]`), 4 `sh`
  and 3 `sleep`. For example, a container init got 17552 instead of 1.
- Without `d445c7cc`, `unshare` and `nsenter` run from inside a non-host PID
  namespace wrote no `UNSHARE` or `SETNS` record. With it, each wrote one,
  with its own-namespace PID.
- `filter-test.sh`: in both builds, the `pids` and `ppids` filters match host
  PIDs and never in-namespace PIDs.
- `clone3-test.sh`: a child created with `clone3` (glibc's `posix_spawn`) gets
  no `NEWPROCESS` record, because the module hooks `clone`, `fork` and
  `vfork` only. runc 1.3.3 uses `clone`, so container processes are
  recorded.

### `ns_inum_pid_children` (2026-11-02)

`km-ab-build.sh d36e055a 2728115f`: *base* without and *fixed* with
`2728115f`. Verdicts on `ns_inum_pid_children`:

| Operation | base: OK / wrong / unverified | fixed: OK / wrong / unverified |
|---|---|---|
| `UNSHARE` | 0 / 7 / 3 | 5 / 0 / 5 |
| `NEWPROCESS` | 230 / 0 / 9 | 233 / 0 / 7 |
| `SETNS` | 2 / 0 / 28 | 2 / 0 / 28 |

- In base, every unshare(CLONE_NEWPID) record had `4294967295`, "unreadable"
  printed unsigned, for a caller that was alive: the `unshare` processes of
  S2 to S5 and runc in S6. In fixed, the same records name the new namespace,
  and for the `unshare` processes it is the namespace `/proc` later showed as
  their `pid_for_children`.
- The unverified `UNSHARE` records are runc's: the callers live too briefly
  for the 50 ms sampler. In fixed they name the container's namespace.
- No namespace record in fixed holds `4294967295`. Its 8 records with -1 are
  all `NEWPROCESS` records in which the mount, network, IPC and cgroup
  namespaces are -1 as well, as for a child that has already exited. Base had
  9 `NEWPROCESS` records with `4294967295`.
- `ns_ns_pid` and `ns_inum_pid` were right in every judged record of both
  builds; both include the two fixes above.

### clone3 (2026-11-02)

`clone3-test.sh` with modules built from `5f496a00` (base) and `cea5d23c`
(fixed):

| Build | `posix_spawn` child, created with clone3 | `fork` child, created with clone |
|---|---|---|
| base | no record | recorded, `ns_syscall=56` |
| fixed | recorded, `ns_syscall=435`, `ns_clone_flags=0x100004111` | recorded, `ns_syscall=56` |

strace showed `clone3({flags=CLONE_VM|CLONE_VFORK|CLONE_CLEAR_SIGHAND,
exit_signal=SIGCHLD, ...})`: 0x100004111 is those flags with SIGCHLD (0x11) in
the low byte. The in-kernel tests (`spade_audit_test`) passed on the fixed
build: 132 global and 44 message tests, 2 more of each than base.

## Requirements

A Linux machine you can load kernel modules on, with `sudo`, auditd running
and writing `/var/log/audit/audit.log`, headers for the running kernel, Docker
able to pull `alpine`, Python 3, util-linux (`unshare`, `nsenter`), `pgrep`
and, for `clone3-test.sh`, `strace`. Run it in a disposable VM: the scripts
load and unload the module, raise the audit backlog limit until the next
reboot, and start and remove containers named `nsab-target` and
`nsab-filter`. No other SPADE module may be loaded.

## Running

```
./km-ab-build.sh <base commit> <fixed commit>    # e.g. 6b744f7c d445c7cc
./ns-ab-run.sh base
./ns-ab-run.sh fixed
./ns-ab-analyze.py ~/ns-ab/base ~/ns-ab/fixed > report.md
./filter-test.sh
./clone3-test.sh
```

`KM_ROOT` (default `~/km-ab`) is where the two module builds go, and
`NSAB_WORK` (default `~/ns-ab`) is where runs write their output: the
module's slice of the audit log, the sampler's ground truth and a run log.
Each run checks the kernel's audit `lost` counter before and after; if it
changes, records were dropped and a missing record proves nothing.

## Files

| File | Does |
|---|---|
| `km-ab-build.sh` | Builds the module from a `git archive` of each commit, against the running kernel |
| `ns-sampler.py` | Records every process's `NSpid`, PID namespace and `pid_for_children` from `/proc` every 50 ms |
| `ns-scenarios.sh` | The scenarios: host fork, unshare, children inside a namespace, nsenter, unshare and nsenter from inside a namespace, `docker run`, `docker exec` |
| `ns-ab-run.sh` | Loads one module variant, runs the sampler and the scenarios, unloads it, cuts its slice of the audit log |
| `ns-ab-analyze.py` | Judges every namespace record against the ground truth and writes a Markdown report comparing the two variants |
| `filter-test.sh` | Checks that the module's `pids` and `ppids` filters still match host PIDs, in both variants |
| `clone3-test.sh` | Whether a process created with `clone3` (glibc's `posix_spawn`) gets a creation record, and with which flags |
