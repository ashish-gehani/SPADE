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

### Module-specific (submodule configure.ac)

| Variable     | Default              | Module          | Effect                                                                          |
|--------------|----------------------|-----------------|---------------------------------------------------------------------------------|
| `JAVA_DEBUG` | (unset → no effect)  | `module/java`   | Set to `1` or `true` to compile Java classes with full debug info (`-g`)        |

### Derived (not user-facing)

| Variable                  | Value                          | Set in              | Used by                             |
|---------------------------|--------------------------------|---------------------|-------------------------------------|
| `JAVA_BUILD_DIR`          | `SPADE_ROOT/lib`               | root configure.ac   | module/java                         |
| `ANDROID_BUILD_DIR`       | `SPADE_ROOT/lib`               | root configure.ac   | module/android                      |
| `AUDIT_BRIDGE_BUILD_DIR`  | `SPADE_ROOT/bin`               | root configure.ac   | module/linux/audit_bridge           |
| `FUSE_BUILD_DIR`          | `SPADE_ROOT/lib`               | root configure.ac   | module/linux/fuse, module/mac/fuse  |
| `KERNEL_MODULE_BUILD_DIR` | `SPADE_ROOT/lib/kernel-modules`| root configure.ac   | module/linux/kernel_module          |
| `LLVM_BUILD_DIR`          | `SPADE_ROOT/lib`               | root configure.ac   | module/linux/llvm, module/mac/llvm  |
| `OPENBSM_BUILD_DIR`       | `SPADE_ROOT/bin`               | root configure.ac   | module/mac/openbsm                  |
| `SPADE_SRC_DIR`           | `SPADE_ROOT/src`               | submodule configure | all submodules except java, android |
| `KERNEL_MODULE_TARGET`    | `KERNEL_MODULES_DEBUG`         | submodule configure | linux/kernel_module                 |
| `kernel_ver`              | `uname -r`                     | submodule configure | linux/kernel_module                 |
| `kheaders_path`           | `KDIR` or `kernel_ver`         | submodule configure | linux/kernel_module                 |
| `FUSE_CFLAGS`             | `pkg-config fuse`              | submodule configure | linux/fuse, mac/fuse                |
| `LLVM_CXXFLAGS`           | `llvm-config`                  | submodule configure | linux/llvm, mac/llvm                |
| `LIBBSM_LIBS`             | `AC_CHECK_LIB bsm`             | submodule configure | mac/openbsm                         |
| `MAVEN_DEBUG_FLAGS`       | `JAVA_DEBUG`                   | submodule configure | module/java                         |

## Module build outputs and install paths

The root `configure.ac` defaults `prefix` to `SPADE_ROOT`, so `make install` places all artifacts directly into the project tree. Override with `--prefix=<path>` at configure time or `DESTDIR=<path>` at install time.

| Module                       | Produces                                           | Build dir (`$SPADE_ROOT/…`) | Installs to (`$SPADE_ROOT/…`) |
|------------------------------|----------------------------------------------------|-----------------------------|-------------------------------|
| `module/java`                | `spade.jar`                                        | `lib/`                      | `lib/spade.jar`               |
| `module/android`             | `android-spade.jar`                                | `lib/`                      | `lib/android-spade.jar`       |
| `module/linux/audit_bridge`  | `spadeAuditBridge`                                 | `bin/`                      | `bin/spadeAuditBridge`        |
| `module/linux/fuse`          | `libLinuxFUSE.so`                                  | `lib/`                      | `lib/libLinuxFUSE.so`         |
| `module/linux/llvm`          | `LLVMTrace.so`, `flush.bc`, `LibcWrapper.so`       | `lib/`                      | `lib/`                        |
| `module/linux/kernel_module` | kernel `.ko` modules                               | `lib/kernel-modules/`       | `lib/kernel-modules/`         |
| `module/mac/fuse`            | `libMacFUSE.jnilib`                                | `lib/`                      | `lib/libMacFUSE.jnilib`       |
| `module/mac/llvm`            | `llvmTracer.dylib`, `llvmBridge.o`, `llvmClose.o` | `lib/`                      | `lib/`                        |
| `module/mac/openbsm`         | `spadeOpenBSM`                                     | `bin/`                      | `bin/spadeOpenBSM`            |

## Action items

1. Add `AC_ARG_VAR` for `JAVA_HOME` in root `configure.ac`; guard derivation in
   `linux/fuse` and `mac/fuse` with: `if test "x${JAVA_HOME}" = "x"`

2. Add `AC_SUBST([KDIR])` and `AC_SUBST([MOD_DEFINES])` in `linux/kernel_module/configure.ac`
   so `./configure KDIR=...` flows into the Makefile without needing `make KDIR=...`.
