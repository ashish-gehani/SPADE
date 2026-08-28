# audit/kernel

Kernel-side implementation of the SPADE audit module: ftrace-hooks a fixed set of kernel functions
(currently all syscalls, though the function-number scheme reserves room for non-syscall functions too),
builds provenance actions (audit logging, and for `sys_kill` some hardening) from each hooked call, and
sets up the supporting namespace/netfilter state the rest of the module depends on.

Code is split by architecture under `arch/`: arch-independent logic lives in `arch/common` and is built
for every arch; genuinely arch-specific logic (raw `pt_regs`/syscall-ABI access) lives in each arch's
own directory as a strong override of a weak stub declared in `arch/common`. This split is still in
progress for aarch64.

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
    └── aarch64/                    # aarch64 strong overrides — currently incomplete
        └── setup/function/ftrace/    # ftrace_thunk.c (writes regs->pc)
```

## Naming convention

A symbol's prefix is determined by its linkage and where it's defined, not by what it does:

1. **File-local** (`static`) symbols: no required prefix, name them however reads best.
2. **Strong global symbols in `arch/common/**`**: prefix `kernel_arch_common_`.
3. **Weak global symbols in `arch/common/**`** (an overridable stub, e.g. via `__weak`): prefix
   `kernel_arch_common_overridable_`.
4. **Strong global symbols in `arch/<arch>/**`**:
   - If it does **not** override a weak `arch/common` declaration: prefix `kernel_arch_<arch>_`
     (e.g. `kernel_arch_x86_64_`, `kernel_arch_aarch64_`).
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
