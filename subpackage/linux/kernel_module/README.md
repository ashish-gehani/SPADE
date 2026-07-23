# kernel_module Build

Documents how this directory (`subpackage/linux/kernel_module`) is configured
and built. Covers `configure.ac`, `Makefile.am`, and `Kbuild` only.

## Directory layout

```
kernel_module/
├── configure.ac        # autoconf input for this subpackage
├── Makefile.am          # automake input; drives Kbuild via a recursive make
├── Kbuild               # the actual kernel module build (kbuild + user-space test bins)
├── audit/               # kernel module sources (per-module object groups)
├── bin/                 # helper/test shell scripts (misc, module mgmt, activity tests)
├── test/kernel/audit/   # sources for the spade_audit_test kbuild module
└── test/user/{src,bin}/ # user-space test helper binaries (ns, socket, ubsi)
```

## `configure.ac`

Standalone autoconf package (`AC_INIT([spadeLinuxKernelModule], ...)`), using
shared `build-aux`/`m4` dirs from further up the tree.

- Requires two variables to be passed in at configure time (fatal
  `AC_MSG_ERROR` if unset):
  - `SPADE_PACKAGE_LIB_DIR` — path to install packaged kernel modules under.
  - `SPADE_PACKAGE_KERNEL_MODULE_DIR_NAME` — subdirectory name under that lib dir.
- `KERNEL_MODULES_DEBUG=true` selects `KERNEL_MODULE_TARGET=debug`, otherwise
  `release`. Substituted into `Makefile.am` as `@KERNEL_MODULE_TARGET@`.
- Host checks are **warnings only**, never fatal, because this subpackage is
  always configured regardless of whether the host can actually build kernel
  modules:
  - kernel version must be in `[5.4, 6.17]`
  - architecture must be `x86_64`
  - distro (`/etc/os-release` `ID=`) must be `ubuntu`
- `KERNEL_MODULES` (default `false`) is substituted as-is into `Makefile.am`
  and controls whether the build is actually *attempted* at `make` time — this
  is the real build gate, independent of the host warnings above.
- Only generates `Makefile` from `Makefile.am` (`AC_CONFIG_FILES([Makefile])`).

## `Makefile.am`

Thin automake wrapper; the real work is delegated to `Kbuild` via recursive
`make -f Kbuild`.

- `OUTPUT_SPADE_PACKAGE_KERNEL_MODULE_DIR` = `$(SPADE_PACKAGE_LIB_DIR)/$(SPADE_PACKAGE_KERNEL_MODULE_DIR_NAME)`
  — where built `.ko` files ultimately get installed.
- `all-local`: only runs if `KERNEL_MODULES = true` (substituted from
  `configure.ac`'s `KERNEL_MODULES` var, default `false`). When true, runs:
  1. `make -f Kbuild PACKAGE_DIR=<out-dir> @KERNEL_MODULE_TARGET@` (`debug` or `release`)
  2. `make -f Kbuild PACKAGE_DIR=<out-dir> package`

  When false, prints an informational skip message — this is how the
  subpackage can always be configured but conditionally skip building actual
  kernel modules (e.g. on unsupported hosts, matching the warnings-not-errors
  design in `configure.ac`).
- `clean-local` / `distclean-local` / `maintainer-clean-local`: delegate to
  `make -f Kbuild clean`, with `maintainer-clean-local` additionally removing
  generated autotools artifacts (`aclocal.m4`, `autom4te.cache`, `build-aux`,
  `configure`, `Makefile.in`).

## `Kbuild`

Standalone GNU Makefile that wraps the Linux `kbuild` system. Not itself a
`Kbuild` file consumed by the kernel's build (despite the name) — it's a
regular Makefile invoked directly (`make -f Kbuild ...`) that in turn invokes
the kernel's kbuild via `$(MAKE) -C $(KDIR) M=$(CURDIR) ... modules`.

### Inputs / variables

- `KDIR` (default `/lib/modules/$(uname -r)/build`) — kernel headers/build tree.
- `PACKAGE_DIR` — **required**, errors out if unset; destination for `make package`.
- `KERNEL_CFLAGS` / `USER_CFLAGS` — extra flags for kernel-module and user-space
  builds respectively.
- `BUILD_DIR` (default `build`) — local staging dir for build outputs before packaging.

### Build hash

- Computes `GEN_BUILD_HASH`: sha256 over the sorted contents of every `.c`/`.h`
  file under `audit/` (excluding `build_hash.c` itself), used to fingerprint the
  build.
- `hash-update` target regenerates `audit/build_hash.c` from
  `audit/build_hash.template` (substituting `@GENERATED_BUILD_HASH@`) only when
  the hash changed since the cached value in `audit/.build_hash_cache`.
- `hash-print` just echoes the current computed hash.

### Modules built

Three kbuild modules, each an `obj-m` target assembled from object-file groups
defined earlier in the file (`ARG_OBJS`, `CONTEXT_OBJS`, `GLOBAL_OBJS`,
`KERNEL_OBJS`, `MSG_OBJS`, `PARAM_OBJS`, `STATE_OBJS`, `TYPE_OBJS`, `UTIL_OBJS`):

| Module | Purpose | Object groups |
|---|---|---|
| `spade_audit` | main auditing module | ARG, BUILD_HASH, CONTEXT, EXPORTED, GLOBAL, KERNEL, MSG, STATE, TYPE, UTIL + `audit/audit.o` |
| `spade_audit_controller` | userspace control interface | ARG, BUILD_HASH, PARAM, TYPE, UTIL + `audit/controller/controller.o` |
| `spade_audit_test` | in-kernel test module | ARG, BUILD_HASH, CONTEXT, GLOBAL, KERNEL, MSG, PARAM, STATE, TYPE, UTIL + `test/kernel/audit/*.o` |

`KERNEL_OBJS` further includes, per hooked syscall in `HOOKED_FUNCTION_NAMES`
(accept, accept4, bind, clone, connect, fork, kill, recvfrom, recvmsg, sendmsg,
sendto, setns, unshare, vfork): `action.o`, `hook.o`, `op.o`, plus an
`action/audit.o` for every hooked function and `action/harden.o` only for
`sys_kill` (`HOOKED_FUNCTION_ACTION_HARDEN_NAMES`).

### User-space test binaries

Built via pattern rules from `test/user/src/{ns,socket/net,socket/unix,ubsi}/*.c`
into `test/user/bin/...` using plain `gcc $(USER_CFLAGS)`. Not part of any
kbuild module.

### Targets

- `all` (default): `hash-update`, then `test-user`, then invokes kbuild
  (`modules`), then moves module output artifacts (`MOD_OUT_FILES`: `.ko`,
  `.mod`, `.mod.c`, `.o`, `Module.symvers`, `modules.order`, and their `.cmd`
  files) into `$(BUILD_DIR)`.
- `debug`: `all` with `-DENABLE_DEBUG_LOG` (`KERNEL_CFLAGS`) and `-g`
  (`ccflags-y`, `USER_CFLAGS`).
- `release`: alias for `all`.
- `test-user`: builds only the user-space test binaries.
- `package`: copies `spade_audit.ko` and `spade_audit_controller.ko` (note: not
  `spade_audit_test.ko`) from `$(BUILD_DIR)` to `$(PACKAGE_DIR)`; fails if either
  is missing.
- `clean`: removes the generated build-hash source/cache, module output files
  (both in-place and under `$(BUILD_DIR)`), user-space test binaries, and the
  packaged `.ko` files in `$(PACKAGE_DIR)`. Does **not** invoke the kernel
  build system's own clean (left as a `TODO` in a comment).
- `check` / `check-full`: run `bin/test/spade_audit_test/run.sh` (dry-run vs
  full) and, for `check-full` only, additionally
  `bin/test/spade_audit/run.sh test watch_audited_user`.
- `help`: prints target/variable summary.

### Notable details

- `ccflags-y` adds `-Wno-attributes -Wno-unused-variable`; the attributes
  suppression is specifically to work around a `mul_u64_u64_div_u64` attribute
  mismatch on kernel 6.8.
- The `all` target prints a warning at the end referencing a known kbuild
  workaround (xcellerator/linux_kernel_hacking issue #3) attributed to hkerma.
- `clean` is explicitly *not* wired to `$(MAKE) -C $(KDIR) M=$(CURDIR) clean`.

## TODO (maintainer notes)

This subpackage deviates from the general subpackage build guidelines in
several ways that should be revisited:

- **Missing `src` directory** — other subpackages keep sources under a `src/`
  directory; here `audit/` and `test/` sit directly at the subpackage root.
- **`Kbuild` usage is not fully understood yet** — the interaction between
  this hand-written `Kbuild` file and the kernel's own kbuild invocation
  (`$(MAKE) -C $(KDIR) M=$(CURDIR) ... modules`) needs a closer read; in
  particular it is unclear whether all conventions expected by kbuild (as
  opposed to a plain recursive Makefile) are being followed correctly.
- **One `Kbuild` building three separate kernel modules** — convention is
  usually one `Kbuild`/`obj-m` per module; here `spade_audit`,
  `spade_audit_controller`, and `spade_audit_test` are all assembled from
  shared object-file groups in a single `Kbuild`, rather than each having its
  own build unit.
- **Separate `make`-based build for tests** rather than integrating
  `spade_audit_test` and the user-space test binaries into the same flow as
  the main modules — worth reconsidering whether tests should be structured
  differently relative to the main build.
- **Configure-time warnings instead of hard failures** for kernel version and
  architecture checks (`configure.ac`) — unusual relative to other
  subpackages, which typically fail hard on unsupported environments; the
  intent here (always configure regardless of host support, gate the actual
  build via `KERNEL_MODULES` at make time) should be confirmed as the desired
  long-term behavior or reconciled with the general convention.
- **Make-time `KERNEL_MODULES` variable as the real build gate** — whether to
  build at all is decided not at configure time but by the `KERNEL_MODULES`
  variable substituted into `Makefile.am` (default `false`, checked in
  `all-local`). This split — configure always succeeds/warns, but the actual
  build is silently skipped unless `KERNEL_MODULES=true` is set — is
  non-standard relative to other subpackages and should be reconciled or at
  least clearly justified.
