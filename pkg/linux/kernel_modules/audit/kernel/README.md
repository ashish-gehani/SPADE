# audit/kernel

Kernel-side implementation of the SPADE audit module: ftrace-hooks a fixed set of kernel functions
(currently all syscalls, though the function-number scheme reserves room for non-syscall functions too),
builds provenance actions (audit logging, and for `sys_kill` some hardening) from each hooked call, and
sets up the supporting namespace/netfilter state the rest of the module depends on.

Code is split by architecture under `arch/`: arch-independent logic lives in `arch/common` and is built
for every arch; genuinely arch-specific logic (raw `pt_regs`/syscall-ABI access) lives in each arch's
own directory as a strong override of a weak stub declared in `arch/common`. This split is still in
progress for `arm64`.

## Directory structure

```
kernel/
├── Kbuild                          # top-level Kbuild: includes the arch-matching Kbuild + common's
└── arch/
    ├── common/                     # arch-independent code, built for every arch
    │   ├── function/                # per-syscall hooking/action wiring
    │   │   ├── {hook,op,action,number}.{c,h}, arg.h, result.h   # shared types + dispatch by syscall number
    │   │   └── sys_<name>/           # one directory per hooked syscall
    │   │       ├── hook.{c,h}          # hook context build/validate (the raw trampoline stays per-arch)
    │   │       ├── op.{c,h}            # lazily-built kernel_function_op getter
    │   │       ├── action.{c,h}        # this syscall's action list getter
    │   │       ├── action/audit.{c,h}  # the audit-log action itself
    │   │       └── arg.h, result.h     # syscall-specific arg/result types
    │   ├── helper/                  # small kernel-version/API helpers (kallsyms, audit log, namespace, network, sock, task)
    │   ├── namespace/                # namespace state helpers
    │   ├── netfilter/                # netfilter state helpers
    │   └── setup/                    # module init/teardown wiring
    │       ├── function/ftrace/        # ftrace hook install/uninstall machinery
    │       ├── namespace/
    │       └── netfilter/
    ├── x86_64/                     # x86_64 strong overrides of arch/common's weak stubs
    │   ├── function/
    │   │   ├── number.c, op.c        # syscall-number table + op-list assembly (still x86_64-only)
    │   │   └── sys_<name>/hook.c      # the actual _hook/_orig trampoline (pt_regs/ABI access)
    │   └── setup/function/ftrace/    # ftrace_thunk.c (writes regs->ip)
    └── arm64/                      # arm64 strong overrides — currently incomplete
        └── setup/function/ftrace/    # ftrace_thunk.c (writes regs->pc)
```

## Kbuild variable convention

`Kbuild` object-list and function-name-list variables are prefixed by scope, mirroring the C symbol
convention below: `ARCH_ARM64_*` and `ARCH_X86_64_*` for each arch's own `Kbuild`, `ARCH_COMMON_*` for
`arch/common/Kbuild`. Within a given scope, variables build up from the bottom:

- `ARCH_<ARCH>_HOOKED_FUNCTIONS_NAMES` (each arch `Kbuild`) / `ARCH_COMMON_FUNCTION_NAMES`
  (`arch/common/Kbuild`) — the list of syscall names handled in that scope.
- `ARCH_<ARCH>_FUNCTION_HOOK_OBJS` — per-function `hook.o`, generated from the names list above via
  `foreach` (arch `Kbuild`s only — `hook.c` is always arch-specific, common has no equivalent).
- `ARCH_<ARCH>_FUNCTION_OBJS` / `ARCH_COMMON_FUNCTION_OBJS` — everything under that scope's `function/`
  subtree.
- `ARCH_<ARCH>_OBJS` / `ARCH_COMMON_OBJS` — the scope's complete object list (`function/` plus `setup/`,
  and for common also `helper/`/`namespace/`/`netfilter/`).

### What the top-level Kbuild expects from each child Kbuild

`audit/kernel/Kbuild` resolves `KERNEL_ARCH_DIR` (`arm64` or `x86_64`) and includes exactly two child
files: `arch/$(KERNEL_ARCH_DIR)/Kbuild` and `arch/common/Kbuild`. Its contract with them is deliberately
thin — each child owns *which functions/actions exist* internally, and only hands the parent a finished
object list:

- Every `arch/<arch>/Kbuild` must define `ARCH_<ARCH>_OBJS` (arch name upper-cased, e.g. `ARCH_ARM64_OBJS`,
  `ARCH_X86_64_OBJS`) — the complete object list for that architecture.
- `arch/common/Kbuild` must define `ARCH_COMMON_OBJS` — the complete arch-independent object list.

`KERNEL_OBJS` is then just the selected arch's `ARCH_<ARCH>_OBJS` plus `ARCH_COMMON_OBJS` — nothing else
crosses the boundary. In particular, the `*_HOOKED_FUNCTIONS_NAMES`/`*_FUNCTION_NAMES` lists and the
intermediate `*_FUNCTION_OBJS`/`*_FUNCTION_HOOK_OBJS` breakdowns are purely internal to how each child
assembles its own `_OBJS` variable; the top-level `Kbuild` never reads them directly. This also means
adding a new arch directory requires wiring its `ARCH_<ARCH>_OBJS` into the top-level `Kbuild`'s
arch-selection branch by hand — Make has no way to derive the upper-cased variable name from the
lower-case directory name automatically.

## Naming convention

A symbol's prefix is determined by its linkage and where it's defined, not by what it does:

1. **File-local** (`static`) symbols: no required prefix, name them however reads best.
2. **Strong global symbols in `arch/common/**`**: prefix `kernel_arch_common_`.
3. **Weak global symbols in `arch/common/**`** (an overridable stub, e.g. via `__weak`): prefix
   `kernel_arch_common_overridable_`.
4. **Strong global symbols in `arch/<arch>/**`**:
   - If it does **not** override a weak `arch/common` declaration: prefix `kernel_arch_<arch>_`
     (e.g. `kernel_arch_x86_64_`, `kernel_arch_arm64_`).
   - If it **does** override a weak `arch/common` declaration: it must keep that declaration's exact
     `kernel_arch_common_overridable_...` name — a `__weak`/strong override pair only works if both
     sides share the identical symbol name, so the arch-specific definition is named as though it
     lives in `common`, even though it's physically defined under `arch/<arch>/`. Rule 4's own
     `kernel_arch_<arch>_` prefix is only for arch-strong globals with no common weak counterpart at
     all.

Every prefix above starts with `kernel_` because everything here already lives under `kernel/` — the
convention only adds scope information (`arch_common_`, `arch_common_overridable_`, or `arch_<arch>_`)
right after that existing `kernel_`, it never removes it. The rest of the descriptive name — which
already encodes its subdirectory and file, e.g. `helper_kernel_...` for something in `helper/kernel.c`
— stays exactly as-is after the inserted scope segment.

Example: the strong `arch/common/helper/kernel.c` function that used to be named
`kernel_helper_kernel_get_kallsyms_func` got `arch_common_` inserted right after `kernel_`, becoming
`kernel_arch_common_helper_kernel_get_kallsyms_func`.

### Global variables

The same rules and prefixes apply to non-function global symbols (structs, arrays, enums, etc.) defined
under `kernel/` — a strong `arch/common` global variable gets `kernel_arch_common_`, a strong
`arch/<arch>` global variable gets `kernel_arch_<arch>_`, and so on, following the identical reasoning
above.

Rule 3 (weak, `kernel_arch_common_overridable_`) does not apply to global variables — there should never
be a weak global variable. If a value needs to vary per-arch, expose it through an (overridable) getter
function instead of a weak variable definition.

### Exceptions

- File-local (`static`) symbols are exempt per rule 1 — e.g. `KERNEL_FUNCTION_OP_LIST`, `ftrace_hooks`,
  and every per-syscall `KERNEL_FUNCTION_SYS_*_OP`/`_HOOK`/`_ACTION_LIST` struct global.
- The vendored `fh_*` functions in `setup/function/ftrace/ftrace_helper.{c,h}` and `fh_ftrace_thunk`
  (see `setup/function/ftrace/attribution.md`) are exempt, to keep that code diffable against its
  upstream source.

## Guarding overridable (weak) symbols

A `kernel_arch_common_overridable_...` symbol (rule 3 above) can be the weak stub itself if no arch
links a strong override — its result is never guaranteed non-`NULL`, and neither are any function
pointers reached through it. Every function pointer sourced from one must be checked non-`NULL`
immediately before it is called, not assumed present just because the surrounding struct is non-`NULL`.

A single validity check performed right before a call (or a small group of calls) in the same guarded
scope satisfies this — it doesn't need to be re-checked at every individual call site as long as nothing
comes between the check and the call. Two existing examples:
- `kernel_arch_common_function_op_is_valid()` checks `op->hook` and each of its
  `get_hook_func`/`get_name`/`get_num`/`get_orig_func_ptr` fields; `op_get_by_func_num()` calls it
  immediately before invoking `op->hook->get_num()`.
- `_init_ftrace_hooks()` (`ftrace.c`) explicitly checks `hook`/`get_name`/`get_hook_func`/
  `get_orig_func_ptr` for `NULL` immediately before calling each of them.

This only applies to function pointers actually reached through an overridable symbol's result — a
plain error-code return (e.g. `kernel_arch_common_overridable_function_op_get_list()`'s `int`) just
needs its error checked like any other fallible call; there's no pointer to guard before calling
anything.

## TODO

- `common/function/sys_<name>/hook.c`'s `*_hook_context_post_is_valid()` (all 14 syscalls) requires
  `ctx->func_res->success` to consider the post-execution context valid. Revisit whether a *failed* syscall
  should still count as valid for post-actions to run against, or whether failure should route through a
  different/no-op path instead.
- `arch/common/setup/function/ftrace/ftrace_thunk.h`, `arch/x86_64/.../ftrace_thunk.c`, and
  `arch/arm64/.../ftrace_thunk.c` each carry an identical stub comment above `fh_ftrace_thunk()` pointing at
  <https://elixir.bootlin.com/linux/v5.11-rc1/A/ident/ftrace_regs> as a placeholder — find and link the actual
  kernel docs for `ftrace_regs` / `KERNEL_HELPER_KERNEL_FTRACE_THUNK_HAS_FTRACE_REGS` and replace the placeholder in
  all three files.
- `arch/common/netfilter/netfilter.c`'s `get_conntrack_info()` (the `KERNEL_HELPER_KERNEL_VERSION_GTE_4_11_0`
  branch) and `arch/common/helper/sock.c`'s socket-name lookup (the `KERNEL_HELPER_KERNEL_VERSION_GTE_4_17_0`
  branch) both have unresolved version-gate concerns flagged at the `#if` — revisit whether those version
  thresholds and the pre-/post-gate logic are actually correct.
- Verify `"__arm64_sys_<name>"` is the correct exported symbol name on target arm64 kernels for each of the 12
  hooked syscalls' `arch/arm64/function/sys_<name>/hook.c` (confirm via e.g.
  `grep __arm64_sys_<name> /proc/kallsyms` or `nm vmlinux`): `sys_accept`, `sys_accept4`, `sys_bind`,
  `sys_clone`, `sys_connect`, `sys_kill`, `sys_recvfrom`, `sys_recvmsg`, `sys_sendmsg`, `sys_sendto`,
  `sys_setns`, `sys_unshare`. This now matters in practice: each `hook.c`'s `#ifdef CONFIG_ARCH_HAS_SYSCALL_WRAPPER`
  branch (the kernel's own Kconfig symbol for the `pt_regs`-argument syscall convention, replacing a
  previous hand-rolled x86_64-only version-gate macro that was always false on arm64) is expected to
  actually be taken on modern arm64 kernels, where it wasn't being exercised before.
- Maybe: give each per-syscall (and other) directory its own `Kbuild`/Makefile instead of the current pattern
  of one `Kbuild` per arch dir listing every subdirectory's objects — would localize each directory's object
  list next to its own sources, at the cost of more files and `include` plumbing. Worth weighing once more
  syscalls are moved and the current `*_OBJS` lists get long.
