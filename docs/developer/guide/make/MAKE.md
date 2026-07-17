# Make Guide

## Overview

SPADE uses an autoconf/automake build system. `configure.ac` and `Makefile.am` at the project root orchestrate all subpackage builds. Running `./configure` + `make` is the complete build.

The build has two responsibilities:

1. **Java compilation** — `subpackage/java/Makefile.am` invokes Maven (`mvn package`) to compile all Java sources and produce `lib/spade.jar`.
2. **Native subpackage builds** — each native subpackage under `subpackage/linux/` or `subpackage/mac/` has its own `configure.ac` and `Makefile.am` that build it independently.

`subpackage/android/` also exists but is **not** wired into the root `configure.ac`/`Makefile.am` (no `AC_CONFIG_SUBDIRS`, not in `SUBDIRS`); it must be configured and built on its own.

## Workflow

```sh
# First-time setup (or after editing any configure.ac or Makefile.am)
autoreconf -fi

# Configure — platform detected automatically; SPADE_ROOT is always the invocation directory
./configure

# Build
make

# Install to a specific location
make install prefix=/some/path

# Clean build artifacts
make clean

# Remove configure-generated files
make distclean

# Remove all autotools-generated files
make maintainer-clean
```

Build output lands directly in the project tree (`lib/`, `bin/`, etc.) via the `SPADE_*_DIR` variables — `make install` with `prefix`/`DESTDIR` is a separate, optional copy step for packaging elsewhere.

Every `configure.ac` disables maintainer mode by default (`AM_MAINTAINER_MODE([disable])`, see `CONVENTIONS.md`), so a plain `./configure` + `make` never tries to regenerate `configure`/`Makefile.in`/`aclocal.m4` on its own — `autoreconf -fi` is the only supported way to regenerate them, regardless of the autoconf/automake version installed.

## Configure Variables

See the full, authoritative list — including every subpackage's variables — with:

```sh
./configure --help=recursive
```

The variables most commonly passed on the command line:

| Variable                   | Default                                  | Description |
|----------------------------|-------------------------------------------|-------------|
| `KERNEL_MODULES`           | `false`                                   | Set to `true` to actually build the Linux kernel modules at make time |
| `KERNEL_MODULES_DEBUG`     | `false`                                    | Set to `true` to build debug kernel modules |
| `KDIR`                     | `/lib/modules/$(uname -r)/build`          | Kernel build directory |
| `MOD_DEFINES`              | (empty)                                    | Extra preprocessor defines for the kernel module build |
| `JAVA_DEBUG`               | (unset)                                    | Set to `1`/`true` to compile Java with full debug info |

Feature flags `--enable-fuse`, `--enable-llvm`, `--enable-openbsm`, and `--disable-kernel-modules` control which native subpackages get configured at all (see Build Structure below); pass them to the root `./configure` and they propagate to the platform subpackage.

`SPADE_ROOT` is always set to the directory `./configure` is invoked from — it is not overridable.

Example:

```sh
./configure --enable-fuse --enable-llvm KERNEL_MODULES=true KDIR=/usr/src/linux-headers-$(uname -r)
```

## Build Structure

The root `configure.ac` detects the platform, computes every `SPADE_*_DIR` path from `SPADE_ROOT`, and dispatches to subpackages:

```
configure.ac               platform detection (AC_CANONICAL_HOST); computes SPADE_ROOT and all
                           SPADE_*_DIR paths; exports them (SPADE_ prefixed) to subpackages
Makefile.am                SUBDIRS: subpackage/java + subpackage/mac or subpackage/linux
                           all-local:   setup → bin/manage-neo4j.sh install, bin/keys/generatekeys.sh
                           clean-local: removes cfg/ssl, log, tmp

subpackage/java/           Java build (always configured)
  configure.ac             checks java, javac (= 21), jar, mvn; requires SPADE_JAVA_* vars from root
  Makefile.am               mvn package/install/clean via -Dspade.* properties → build/spade.jar

subpackage/android/        Android build (present, but not configured by the root build — see above)
  configure.ac             checks dx (warns if missing); requires SPADE_ANDROID_*, SPADE_JAVA_BUILD_DIR
  Makefile.am               dx --dex → android-spade.jar

subpackage/linux/          Linux native subpackages (on Linux)
  configure.ac             --enable-fuse, --enable-llvm, --enable-kernel-modules (default: yes)/--disable-kernel-modules
  Makefile.am               SUBDIRS: audit_bridge + conditionally kernel_module, fuse, llvm
  audit_bridge/             builds spadeAuditBridge (always)
  fuse/                     builds libLinuxFUSE.so (when --enable-fuse)
  llvm/                     builds LLVMTrace.so, flush.bc, LibcWrapper.so (when --enable-llvm)
  kernel_module/            always configured; make-time gated by KERNEL_MODULES=true + host checks

subpackage/mac/            macOS native subpackages (on macOS)
  configure.ac             --enable-fuse, --enable-llvm, --enable-openbsm
  Makefile.am               SUBDIRS: conditionally fuse, llvm, openbsm
  fuse/                     builds libMacFUSE.jnilib (when --enable-fuse)
  llvm/                     builds llvmTracer.dylib, llvmBridge.o, llvmClose.o (when --enable-llvm)
  openbsm/                  builds spadeOpenBSM (when --enable-openbsm)
```

## Java Build

`subpackage/java/Makefile.am` drives the Java build. `all-local` calls `mvn package` with `-Dspade.*` properties (source/resource/build/lib dirs, native include dir, local repository URL) derived from the `SPADE_JAVA_*` variables exported by the root. This compiles all Java sources and writes native JNI headers to `$(SPADE_JAVA_BUILD_NATIVE_INCLUDE_DIR)`. Native subpackages (`linux/fuse`, `mac/fuse`) include that directory directly; they do not invoke `javac` themselves.

## Platform Detection

`configure.ac` uses `AC_CANONICAL_HOST`:

- `darwin*` → configures `subpackage/mac`
- `linux*`  → configures `subpackage/linux`
- anything else → configure error

## Kernel Modules

The `kernel_module` subpackage is always configured (unless `--disable-kernel-modules` is passed), but the actual module build is skipped at make time unless `KERNEL_MODULES=true`:

```sh
./configure
make KERNEL_MODULES=true
# or: ./configure KERNEL_MODULES=true && make
```

The kernel module build additionally expects all of:

- Ubuntu
- x86\_64 architecture
- Kernel version in \[5.4, 6.17\]
- Kernel headers at `KDIR` (default `/lib/modules/$(uname -r)/build`)

`subpackage/linux/kernel_module/configure.ac` warns (does not error) if any condition is unmet, since the subpackage is configured regardless of whether the host actually supports kernel modules.

## Post-Build Steps

After all subpackage builds complete, the root `all-local` target runs `setup`:

1. `bin/manage-neo4j.sh install` — installs the Neo4j distribution into `lib/`.
2. `bin/keys/generatekeys.sh` — generates SSL keys into `cfg/keys/`.

## Clean

`make clean` removes:

- All subpackage build artifacts (via recursive `clean` into each subdir).
- Runtime directories: `cfg/ssl`, `log`, `tmp`.

Maven's own build directory is removed by `mvn clean`, which `subpackage/java/Makefile.am`'s `clean-local` calls.

## Adding a Subpackage

1. Create `subpackage/<platform>/<name>/configure.ac` and `Makefile.am` following the patterns in `docs/developer/guide/make/SUBPACKAGE.md`.
2. Add detection logic and a conditional `AC_CONFIG_SUBDIRS` in `subpackage/<platform>/configure.ac`.
3. Add `AM_CONDITIONAL` and update `DIST_SUBDIRS`/`SUBDIRS` in `subpackage/<platform>/Makefile.am`.
4. Run `autoreconf -fi` in the subpackage directory, then each parent up to the root.
