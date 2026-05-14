# Status

## Configure variables

### User-facing (root configure.ac)

| Variable                   | Default                                                     | Used by                               |
|----------------------------|-------------------------------------------------------------|---------------------------------------|
| `SPADE_ROOT`               | `pwd`                                                       | all                                   |
| `JAVAC_HEADER_GEN_OPTIONS` | `-Xlint:none -proc:none @$SPADE_ROOT/build.java-cp.argfile` | linux/fuse, mac/fuse                  |
| `KERNEL_MODULES`           | (unset → kernel modules disabled)                           | linux/kernel_module (via --enable)    |
| `KERNEL_MODULES_DEBUG`     | `false`                                                     | linux/kernel_module                   |
| `KDIR`                     | `/lib/modules/$(uname -r)/build`                            | linux/kernel_module                   |
| `MOD_DEFINES`              | (empty)                                                     | linux/kernel_module                   |

`JAVA_HOME` is derived per-submodule via `java -XshowSettings:all`; it is not yet declared in root configure.ac (see action items).

### Derived (not user-facing)

| Variable               | Derived from           | Used by                                 |
|------------------------|------------------------|-----------------------------------------|
| `SPADE_SRC_DIR`        | `SPADE_ROOT/src`       | all submodules except java, android     |
| `SPADE_BUILD_DIR`      | `SPADE_ROOT/build`     | android, linux/fuse, mac/fuse           |
| `KERNEL_MODULE_TARGET` | `KERNEL_MODULES_DEBUG` | linux/kernel_module                     |
| `kernel_ver`           | `uname -r`             | linux/kernel_module                     |
| `kheaders_path`        | `KDIR` or `kernel_ver` | linux/kernel_module                     |
| `FUSE_CFLAGS`          | `pkg-config fuse`      | linux/fuse, mac/fuse                    |
| `LLVM_CXXFLAGS`        | `llvm-config`          | linux/llvm, mac/llvm                    |
| `LIBBSM_LIBS`          | `AC_CHECK_LIB bsm`     | mac/openbsm                             |

## Module build outputs and install paths

The root `configure.ac` defaults `prefix` to `SPADE_ROOT`, so `make install` places all artifacts directly into the project tree. Override with `--prefix=<path>` at configure time or `DESTDIR=<path>` at install time.

| Module                       | Produces                                           | Build dir | Installs to (`$SPADE_ROOT/…`) |
|------------------------------|----------------------------------------------------|-----------|-------------------------------|
| `module/java`                | `spade.jar`                                        | `build/`  | `lib/spade.jar`               |
| `module/android`             | `android-spade.jar`                                | `build/`  | `lib/android-spade.jar`       |
| `module/linux/audit_bridge`  | `spadeAuditBridge`                                 | `build/`  | `bin/spadeAuditBridge`        |
| `module/linux/fuse`          | `libLinuxFUSE.so`                                  | `build/`  | `lib/libLinuxFUSE.so`         |
| `module/linux/llvm`          | `LLVMTrace.so`, `flush.bc`, `LibcWrapper.so`       | `build/`  | `lib/`                        |
| `module/linux/kernel_module` | kernel `.ko` modules                               | `build/`  | `lib/kernel-modules/`         |
| `module/mac/fuse`            | `libMacFUSE.jnilib`                                | `build/`  | `lib/libMacFUSE.jnilib`       |
| `module/mac/llvm`            | `llvmTracer.dylib`, `llvmBridge.o`, `llvmClose.o` | `build/`  | `lib/`                        |
| `module/mac/openbsm`         | `spadeOpenBSM`                                     | `build/`  | `bin/spadeOpenBSM`            |

## Action items

1. Add `AC_ARG_VAR` for `JAVA_HOME` in root `configure.ac`; guard derivation in
   `linux/fuse` and `mac/fuse` with: `if test "x${JAVA_HOME}" = "x"`

2. Add `AC_SUBST([KDIR])` and `AC_SUBST([MOD_DEFINES])` in `linux/kernel_module/configure.ac`
   so `./configure KDIR=...` flows into the Makefile without needing `make KDIR=...`.
