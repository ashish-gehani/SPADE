# Audit reporter: multi-arch support

Notes on what has to change before the Audit reporter can process audit logs from architectures other than x86_64 (starting with arm64/aarch64).

## Current state

- [`arch.Type`](Type.java) is the supported archs:

  | `arch.Type` | `arch=` value in audit SYSCALL records | `uname -m` |
  |---|---|---|
  | `X86_64` | `c000003e` | `x86_64` |
  | `ARM64` | `c00000b7` | `aarch64` |

- [`SYSCALL.getSyscall(arch.Type arch, int syscallNum)`](../SYSCALL.java) maps a system call number to a `SYSCALL` for an arch. An unknown number returns `SYSCALL.UNSUPPORTED`.
- The arm64 table only contains `SYSCALL` values that exist on arm64. arm64 has no `open`, `creat`, `fork`, `vfork`, `pipe`, `dup2`, `link`, `unlink`, `rename`, `symlink`, `chmod` or `mknod`.
- [`arch.Stopgap`](Stopgap.java) is a temporary home for arch-specific code until it is properly refactored. An instance is created in one of two modes, which fix the arch for all records:
  - `getInstanceForLiveMode()`: the arch of this system, from `uname -m`. Throws on ARM64: live audit is not supported there until the audit rules (1), kernel modules (2), and audit bridge (3) are fixed.
  - `getInstanceForPlaybackMode(auditLogPaths[, all])`: the `arch=` field of the first SYSCALL record in the first audit log (or, with `all`, in the audit logs in order).

  `Audit` creates one at launch with `getInstanceForInput(map, input)` (LIVE: live mode; FILE: the input logs, newest first; DIRECTORY: the directory's files, oldest first) and keeps it in a field. In playback, the Audit config key `archScanAllLogs` (default `false`) is passed as `all`: `false` scans only the first log, `true` scans the logs in that order until a SYSCALL record is found.
- [`Audit`](../../Audit.java) gets every system call through `archStopgap.getSyscallFromLinuxAuditRecord(eventData)`, so all three record sources below use the arch fixed at launch.

## Where system call numbers enter the reporter

All sources put the number in the record's key-values. Only SYSCALL records carry an `arch`, which is why `Stopgap` fixes one arch for all records at launch:

| Caller | Event source | `arch` available? |
|---|---|---|
| `handleSyscallEvent` | Audit `type=SYSCALL` records | Yes. [`AuditEventReader.parseSyscallRecord`](../AuditEventReader.java) keeps every `key=value` pair in the record, so `eventData.get("arch")` returns the raw hex value. |
| `handleSyscallEvent` | UBSI-intercepted records (`type=USER ubsi_intercepted="syscall=…"`), which are also tagged as SYSCALL | No. The sub-record has no `arch` field. |
| `handleKernelModuleEvent` | Kernel module records (`netio_intercepted="syscall=…"`) | No. The record has no `arch` field. The numbers come from `__NR_*`, so they are always in the host's native numbering. |
| None (not mapped to a `SYSCALL` today) | Kernel module namespace records (`ns_syscall=… ns_subtype=ns_namespaces …`) | No. The record has no `arch` field, and the number is in `ns_syscall`, not `syscall`. `Stopgap.getSyscallFromLinuxAuditNamespaceRecord` is there for when it is needed. |

## TODO

Live audit on ARM64 is blocked (`Stopgap.getInstanceForLiveMode()` throws), so the work is split into what ARM playback needs now and what can wait until live ARM is turned back on. Section 4 (choosing the arch) is done apart from its open playback questions.

What's left on the Java side for ARM playback, in order:

1. Coverage gaps (section 6): add the missing system calls to `SYSCALL`, both arch tables, and their handlers. This matters most, because real aarch64 logs contain them: on arm64, processes are created only through `clone` or `clone3`, so without `clone3` many process and thread creations become `UNSUPPORTED`.
2. System call semantics (section 5): check the handlers. arm64 logs contain only the `*at` forms (`openat`, `linkat`, …), so none of them can depend on falling through to an x86-only sibling.
3. Playback edge cases (section 4): decide or document them.
4. Verification (section 7). This aarch64 VM can record a log with plain `auditd`, without SPADE's live mode.

Not Java, but it affects ARM playback too: the SPADE audit bridge (section 3) processes the input logs in playback mode as well, so its hardcoded x86_64 numbers would misread ARM logs (UBSI units, process exit) even though Java maps every system call correctly.

Deferred until live ARM is turned back on:

- Audit rules (section 1): build the system call lists per arch in `AuditControlManager`.
- Kernel modules (section 2).
- Remove the ARM64 check in `Stopgap.getInstanceForLiveMode()`, together with the bridge (section 3) and kernel module fixes.

### 1. Audit rules ([`AuditControlManager`](../AuditControlManager.java)) — live only, deferred

- [ ] The rules use `-F arch=b64` with system calls named explicitly. Some of those names don't exist on aarch64 (`open`, `creat`, `fork`, `vfork`, `pipe`, `dup2`, `link`, `unlink`, `rename`, `symlink`, `chmod`, `mknod`). `auditctl` rejects a rule naming a system call the arch doesn't have, so rule installation is expected to fail on ARM. Build the system call lists per arch. The arch is available from `archStopgap.getArchType()`, but has to be passed into `AuditControlManager.set(...)`.
- [ ] `sendmmsg` and `recvmmsg` exist on both arches. No change is needed, but they belong in the per-arch lists.
- [ ] `arch=b64` excludes 32-bit (compat) processes on both arches. Decide whether that's acceptable. If compat processes are supported, `SYSCALL.getSyscall` also needs `40000003` (i386) and `40000028` (arm).

### 2. Kernel modules ([`pkg/linux/kernel_modules`](../../../../../../../../linux/kernel_modules)) — live only, deferred

- [ ] Hooks return x86-only symbol names (`__x64_sys_*`, e.g. `audit/kernel/function/sys_kill/hook.c`). On arm64 the symbols are `__arm64_sys_*`. Choose the prefix per arch at build time.
- [ ] `audit/kernel/function/number.c` uses `__NR_fork` and `__NR_vfork`, which arm64 doesn't define, so the module doesn't compile there. Leave the `sys_fork` and `sys_vfork` hooks out on arm64.
- [x] Kernel module records have no arch (see above). The Java side uses the arch fixed at launch (see 4).

### 3. Audit bridge ([`spadeAuditBridge.c`](../../../../../../../../linux/audit_bridge/src/spadeAuditBridge.c))

The bridge processes the input in both live and playback mode, so this affects ARM playback too.

- [ ] The bridge compares raw x86_64 system call numbers in `syscall_handler`, `non_UBSI_event`, and `get_succ`. On arm64 most of them are different system calls (56 is `openat`, 57 is `close`, 59 is `pipe2`, 62 is `lseek`), so process tracking and UBSI units would be silently wrong:

  | System call | x86_64 | arm64 | Used for |
  |---|---|---|---|
  | `clone` | 56 | 220 | Child process/thread start; `a2 > 0` is treated as thread creation |
  | `fork` | 57 | none | Child process start |
  | `vfork` | 58 | none | Child process start |
  | `execve` | 59 | 221 | Process end and start time reset |
  | `execveat` | 322 | 281 | Process end |
  | `exit` | 60 | 93 | Process end; always successful (`get_succ`) |
  | `exit_group` | 231 | 94 | Process group end; always successful (`get_succ`) |
  | `kill` | 62 | 129 | UBSI unit events (`a0` is a UBSI marker) and killed process end |
  | `rt_sigaction` | 13 | 134 | Signal handlers (signals don't end the process) |

- [ ] Needs more thought before replacing the numbers (e.g. with an `arch_stopgap.c` holding a per-arch table, the counterpart of `Stopgap`):
  - `fork()` on arm64 is `clone(SIGCHLD, …)`, and on x86_64 glibc implements `fork()` with `clone` too. The `clone` with `a2 > 0` check for thread creation relies on the `parent_tid` argument, not on `CLONE_THREAD` in the flags (`a0`). Check that it still tells threads and processes apart on arm64.
  - `clone3` (435 on both) is not handled at all. Its arguments are in a struct, so the flags are not in the audit record.
  - Where the arch comes from: the first SYSCALL record's `arch=` field, or a new command-line option set by Java from `Stopgap`. UBSI-intercepted records have no `arch` field, so they need an arch that is already known.
  - `spadeAuditBridge.c` defines its own `bool` (`typedef int bool`), so new code can't include `<stdbool.h>`.
  - [`Makefile.am`](../../../../../../../../linux/audit_bridge/Makefile.am) compiles only `spadeAuditBridge.c`. A new source file has to be added there.
- [x] UBSI-intercepted records have no `arch` field. The Java side uses the arch fixed at launch (see 4).

### 4. Java: choosing the arch ([`Audit`](../../Audit.java), [`Stopgap`](Stopgap.java))

- [x] Fix one arch at launch: `uname -m` for live mode, the audit log for playback (so a log recorded on another machine uses its own arch).
- [x] Use it for all records, including the ones without `arch` (UBSI and kernel module records).
- [x] `handleSyscallEvent` and `handleKernelModuleEvent` call `archStopgap.getSyscallFromLinuxAuditRecord(eventData)`. `SYSCALL.get64BitSyscall` and `Audit.getSyscall` are removed.
- [x] An unsupported arch fails launch, instead of every event silently returning `UNSUPPORTED`.
- [ ] DIRECTORY mode ignores `inputDirTime`, so the first file scanned may be one the bridge skips.
- [ ] DIRECTORY mode fails launch if the directory is empty, e.g. when the bridge is meant to wait for logs.
- [ ] With `archScanAllLogs=false`, FILE mode only scans the newest log, so launch fails if it has no SYSCALL record yet (e.g. just rotated). Set `archScanAllLogs=true` to fall back to older logs.

### 5. Java: system call semantics

- [ ] Check every handler for x86_64-specific argument layouts. The known difference is `clone`: x86_64 is `(flags, stack, parent_tid, child_tid, tls)` and arm64 is `(flags, stack, parent_tid, tls, child_tid)`. Reading only `a0` (flags) is unaffected.
- [ ] [`LinuxConstants`](../LinuxConstants.java) values come from `cfg/spade.reporter.audit.LinuxConstants.config`. The values currently loaded (`O_RDONLY`…`O_APPEND`, `MAP_ANONYMOUS`, `F_*`, `EINPROGRESS`, `SOCK_*`) are the same on arm64. If `O_DIRECTORY`, `O_NOFOLLOW`, `O_DIRECT` or `O_LARGEFILE` are ever added, they differ by arch and need a per-arch config.
- [ ] `Audit` and its helpers refer to x86-only `SYSCALL` values (`OPEN`, `FORK`, `PIPE`, …) in 21 places. These are unreachable on ARM, which is fine, but confirm that no `*at` variant (`openat`, `linkat`, …) depends on falling through to its x86 sibling.

### 6. Coverage gaps (matter more on ARM)

- [ ] `clone3` (435 on both). glibc 2.34+ uses it to create threads. On arm64, `clone`/`clone3` are the only ways to create a process.
- [ ] `renameat2` (x86_64 316, arm64 276), used by coreutils `mv`.
- [ ] `openat2` (437 on both), `execveat` (x86_64 322, arm64 281; handled by the bridge but not by Java), `preadv2`/`pwritev2`.
- [ ] `sendmmsg`/`recvmmsg` are in the audit rules but not in `SYSCALL`. The `RECVMMSG` case in `Audit.handleKernelModuleEvent` is commented out.

### 7. Verification

- [ ] Capture a real aarch64 audit log (e.g. on an ARM Vagrant box) and replay it through the reporter.
- [ ] Unit tests for `SYSCALL.getSyscall` (every table entry round-trips, and an unknown number returns `UNSUPPORTED`), `arch.Type`, and `Helper.getAuditLogsArch` / `listAuditLogsInDir`.
- [ ] Check `getSyscallARM64` against `ausyscall aarch64 --dump`.
