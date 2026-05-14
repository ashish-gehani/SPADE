# Make Guide

## Overview

SPADE uses an autoconf/automake build system. `configure.ac` and `Makefile.am` at the project root orchestrate all submodule builds. Running `./configure` + `make` is the complete build.

The build has two responsibilities:

1. **Java compilation** — `module/java/Makefile.am` invokes Maven (`mvn compile`) to compile all Java sources and produce `lib/spade.jar`.
2. **Native module builds** — each native submodule under `module/linux/` or `module/mac/` has its own `configure.ac` and `Makefile.am` that build it independently.

## Workflow

```sh
# First-time setup (or after editing any configure.ac or Makefile.am)
autoreconf -fi

# Configure — platform detected automatically; SPADE_ROOT defaults to pwd
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

## Configure Variables

All variables can be passed on the `./configure` command line.

| Variable                   | Default                                           | Description |
|----------------------------|---------------------------------------------------|-------------|
| `SPADE_ROOT`               | `pwd`                                             | Project root; all derived paths are based on this |
| `JAVAC_HEADER_GEN_OPTIONS` | `-Xlint:none -proc:none @build.java-cp.argfile`   | Options passed to `javac` for JNI header generation |
| `KERNEL_MODULES`           | (unset)                                           | Set to `true` to enable Linux kernel module build |
| `JAVA_HOME`                | derived via `java -XshowSettings:all`             | JDK home; override if auto-detection fails |
| `KERNEL_MODULES_DEBUG`     | `false`                                           | Set to `true` to build debug kernel modules |
| `KDIR`                     | `/lib/modules/$(uname -r)/build`                  | Kernel build directory |
| `MOD_DEFINES`              | (empty)                                           | Extra preprocessor defines for the kernel module build |

Example:

```sh
./configure KERNEL_MODULES=true KDIR=/usr/src/linux-headers-$(uname -r)
```

## Build Structure

The root `configure.ac` detects the platform and dispatches to two top-level subdirs:

```
configure.ac              platform detection (AC_CANONICAL_HOST), SPADE_ROOT default,
                          Java classpath via bin/classpath.sh → build.java-cp.argfile,
                          KERNEL_MODULES → --enable-kernel-modules passthrough
Makefile.am               SUBDIRS: module/java + module/mac or module/linux
                          all-local:   bin/manage-neo4j.sh install, bin/keys/generatekeys.sh
                          clean-local: removes cfg/ssl, log, tmp

module/java/              Java build (always included)
  configure.ac            checks java, javac (≥21), jar, mvn; requires SPADE_ROOT
  Makefile.am             mvn compile → build/spade.jar; install → lib/spade.jar

module/linux/             Linux native modules (on Linux)
  configure.ac            detects fuse, llvm; --enable-kernel-modules
  Makefile.am             SUBDIRS: audit_bridge + conditionally kernel_module, fuse, llvm
  audit_bridge/           builds spadeAuditBridge (always)
  fuse/                   builds libLinuxFUSE.so (when fuse detected)
  llvm/                   builds LLVMTrace.so, flush.bc, LibcWrapper.so (when llvm detected)
  kernel_module/          builds kernel modules (when KERNEL_MODULES=true + gating checks)

module/mac/               macOS native modules (on macOS)
  configure.ac            detects fuse, llvm, libbsm
  Makefile.am             SUBDIRS: conditionally fuse, llvm, openbsm
  fuse/                   builds libMacFUSE.jnilib (when fuse detected)
  llvm/                   builds llvmTracer.dylib, llvmBridge.o, llvmClose.o (when llvm detected)
  openbsm/                builds spadeOpenBSM (when libbsm detected)
```

## Java Build

`module/java/Makefile.am` drives the Java build. At configure time, `bin/classpath.sh` resolves the full Maven dependency classpath and writes it to `build.java-cp.argfile`. This file is appended to `JAVAC_HEADER_GEN_OPTIONS` and exported to all submodules that need JNI header generation (`linux/fuse`, `mac/fuse`).

## Platform Detection

`configure.ac` uses `AC_CANONICAL_HOST`:

- `darwin*` → configures `module/mac`
- `linux*`  → configures `module/linux`
- anything else → configure error

## Kernel Modules

Disabled by default. Enable at configure time:

```sh
./configure KERNEL_MODULES=true
```

The kernel module build additionally requires all of:

- Ubuntu
- x86\_64 architecture
- Kernel version in \[5.4, 6.17\]
- Kernel headers at `KDIR`

Configure warns if any condition is not met. `make` errors if `KERNEL_MODULES=true` but requirements are unmet.

## Post-Build Steps

After all submodule builds complete, the root `all-local` target runs:

1. `bin/manage-neo4j.sh install` — installs the Neo4j distribution into `lib/`.
2. `bin/keys/generatekeys.sh` — generates SSL keys into `cfg/keys/`.

## Clean

`make clean` removes:

- All submodule build artifacts (via recursive `clean` into each subdir).
- Runtime directories: `cfg/ssl`, `log`, `tmp`.

Maven's own build directory (`module/java/build/`) is removed by `mvn clean`, which `module/java/Makefile.am` calls in its `clean-local`.

## Adding a Submodule

1. Create `module/<platform>/<name>/configure.ac` and `Makefile.am` following the patterns in `module/HOWTO.md`.
2. Add detection logic and a conditional `AC_CONFIG_SUBDIRS` in `module/<platform>/configure.ac`.
3. Add `AM_CONDITIONAL` and update `DIST_SUBDIRS`/`SUBDIRS` in `module/<platform>/Makefile.am`.
4. Run `autoreconf -fi` in the submodule directory, then each parent up to the root.
