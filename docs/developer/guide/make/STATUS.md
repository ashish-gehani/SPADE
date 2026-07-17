# Status

## Configure variables

The full, current list of every configure variable — root and every subpackage — is authoritative in the `configure.ac` files themselves and is always available via:

```sh
./configure --help=recursive
```

This doc does not duplicate that list (it goes stale immediately); it only tracks where the interesting ones are *computed*, since `--help=recursive` shows what a variable is for but not where it's derived.

### Derived in the root `configure.ac`, exported to subpackages

All prefixed with `SPADE_` and computed from `SPADE_ROOT` (always the `./configure` invocation directory):

| Variable                                | Value                                          | Used by                              |
|------------------------------------------|-------------------------------------------------|---------------------------------------|
| `SPADE_ROOT`                              | `pwd`                                           | all                                    |
| `SPADE_ANDROID_BUILD_DIR`                 | `SPADE_ROOT/build`                              | subpackage/android                     |
| `SPADE_ANDROID_LIB_DIR`                   | `SPADE_ROOT/lib`                                | subpackage/android                     |
| `SPADE_AUDIT_BRIDGE_BIN_DIR`              | `SPADE_ROOT/bin`                                | subpackage/linux/audit_bridge          |
| `SPADE_FUSE_LIB_DIR`                      | `SPADE_ROOT/lib`                                | subpackage/linux/fuse, subpackage/mac/fuse |
| `SPADE_JAVA_BUILD_DIR`                    | `SPADE_ROOT/build`                              | subpackage/java, subpackage/android    |
| `SPADE_JAVA_BUILD_NATIVE_INCLUDE_DIR`     | `SPADE_JAVA_BUILD_DIR/native/include`           | subpackage/linux/fuse, subpackage/mac/fuse |
| `SPADE_JAVA_LIB_DIR`                      | `SPADE_ROOT/lib`                                | subpackage/java                        |
| `SPADE_JAVA_LOCAL_REPOSITORY_URL`         | `file://SPADE_JAVA_LIB_DIR`                     | subpackage/java                        |
| `SPADE_JAVA_SRC_DIR`                      | `SPADE_ROOT/src`                                | subpackage/java, and most native subpackages |
| `SPADE_JAVA_SRC_RESOURCE_DIR`             | `SPADE_JAVA_SRC_DIR/resources`                  | subpackage/java                        |
| `SPADE_KERNEL_MODULE_LIB_DIR`             | `SPADE_ROOT/lib/kernel-modules`                 | subpackage/linux/kernel_module         |
| `SPADE_LLVM_LIB_DIR`                      | `SPADE_ROOT/lib`                                | subpackage/linux/llvm, subpackage/mac/llvm |
| `SPADE_OPENBSM_BIN_DIR`                   | `SPADE_ROOT/bin`                                | subpackage/mac/openbsm                 |

### Derived within a subpackage's own `configure.ac`

Not exported further; local to that subpackage (no `SPADE_` prefix needed):

| Variable            | Derivation                              | Subpackage                          |
|---------------------|-------------------------------------------|--------------------------------------|
| `JAVA_HOME`         | `java -XshowSettings:all`                 | linux/fuse, mac/fuse                 |
| `FUSE_CFLAGS`       | `pkg-config fuse --cflags --libs`         | linux/fuse, mac/fuse                 |
| `LLVM_CXXFLAGS`     | `llvm-config --cxxflags`                  | linux/llvm, mac/llvm                 |
| `LIBBSM_LIBS`       | fixed `-lbsm` (parent already checked `AC_CHECK_LIB([bsm], ...)`) | mac/openbsm |
| `KERNEL_MODULE_TARGET` | `KERNEL_MODULES_DEBUG` (`debug`/`release`) | linux/kernel_module               |
| `KHEADERS_PATH`     | `KDIR`, else `/lib/modules/$(uname -r)/build` | linux/kernel_module               |
| `MAVEN_DEBUG_FLAGS` | `JAVA_DEBUG`                               | java                                  |

## Subpackage build outputs and install paths

Build output goes directly into the `SPADE_*_DIR` above (already inside the project tree); `make install` with `prefix`/`DESTDIR` is a separate, optional copy for packaging elsewhere.

| Subpackage                       | Produces                                           | Build dir (`$SPADE_ROOT/…`) | Installs to (`$SPADE_ROOT/…`, default `prefix`) |
|-----------------------------------|-----------------------------------------------------|-------------------------------|----------------------------------------------------|
| `subpackage/java`                 | `spade.jar`                                        | `lib/`                        | (via `mvn install`)                                 |
| `subpackage/android` *(not wired into root build — see MAKE.md)* | `android-spade.jar`          | `build/`                       | `lib/android-spade.jar`                             |
| `subpackage/linux/audit_bridge`   | `spadeAuditBridge`                                 | `bin/`                         | `bin/spadeAuditBridge`                              |
| `subpackage/linux/fuse`           | `libLinuxFUSE.so`                                  | `lib/`                         | `lib/libLinuxFUSE.so`                               |
| `subpackage/linux/llvm`           | `LLVMTrace.so`, `flush.bc`, `LibcWrapper.so`       | `lib/`                         | `lib/`                                              |
| `subpackage/linux/kernel_module`  | kernel `.ko` modules (only when `KERNEL_MODULES=true`) | `lib/kernel-modules/`      | `lib/kernel-modules/`                                |
| `subpackage/mac/fuse`             | `libMacFUSE.jnilib`                                | `lib/`                         | `lib/libMacFUSE.jnilib`                             |
| `subpackage/mac/llvm`             | `llvmTracer.dylib`, `llvmBridge.o`, `llvmClose.o` | `lib/`                         | `lib/`                                              |
| `subpackage/mac/openbsm`          | `spadeOpenBSM`                                     | `bin/`                         | `bin/spadeOpenBSM`                                  |
