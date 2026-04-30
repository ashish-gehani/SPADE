# Make Guide

## Overview

`Makefile.in` is the primary build file for SPADE. It is processed by `configure` (autoconf) to produce `Makefile`, substituting toolchain paths such as `@JAVAC@`, `@CC@`, and `@JAR@`.

The Makefile has two responsibilities:

1. **Java compilation** — compiles all Java sources directly using `javac`.
2. **Subcomponent coordination** — delegates native and platform-specific builds to scripts under `bin/`. The Makefile owns the variable definitions; scripts own the build logic.

This separation keeps scripts independently runnable and testable, while the Makefile serves as the single source of truth for paths and configuration.

## Variable Layout

Variables are grouped by concern at the top of the file.

```make
# toolchain — substituted by configure
JAVAC = @JAVAC@
CC    = @CC@
JAR   = @JAR@

# build paths
SPADE_ROOT      = $(shell pwd)
SPADE_BUILD_DIR = $(SPADE_ROOT)/build
SPADE_SRC_DIR   = $(SPADE_ROOT)/src
...

# per-subcomponent variables
FUSE_LINUX_LIB_PATH      = $(SPADE_LIB_DIR)/libLinuxFUSE.so
FUSE_LINUX_LIB_JAVA_SRC  = $(SPADE_SRC_DIR)/spade/reporter/LinuxFUSE.java
...
```

Each subcomponent has its own named block. Variables are passed verbatim to the subcomponent's script as named arguments (see [Delegating to Scripts](#delegating-to-scripts)).

## Java Compilation

Java sources are compiled per-package by invoking `javac` directly. `JAVAC_CP` is built by `bin/classpath.sh` at make time.

```make
JAVAC_CP      = $(shell ./bin/classpath.sh)
JAVAC_OPTIONS = $(EXTRA_JAVAC_OPTIONS) -Xlint:none -proc:none -cp '$(JAVAC_CP)'
```

Override `EXTRA_JAVAC_OPTIONS` on the command line to inject extra flags:

```sh
make EXTRA_JAVAC_OPTIONS="-g"
```

The `build-java` target compiles each package group in dependency order: `core` first, then `clients`, `utilities`, `storages`, `screens`, `filters`, `query`, `analyzers`, `transformers`, and `reporters`.

## OS Detection

The OS is detected at runtime using `uname`. Detected value drives which subcomponents are included in the default `build` target.

```make
OS_NAME = $(shell uname)

ifeq ($(OS_NAME), Linux)
    OS_BUILD = build-linuxaudit
    ifeq (0, $(shell pkg-config fuse; echo $$?))
        OS_BUILD += build-linuxfuse
    endif
endif

ifeq ($(OS_NAME), Darwin)
    OS_BUILD = build-openbsm
    ifeq (0, $(shell pkg-config fuse; echo $$?))
        OS_BUILD += build-macfuse
    endif
endif
```

FUSE subcomponents are added only when `pkg-config fuse` succeeds.

## Delegating to Scripts

Every native subcomponent has a dedicated build script under `bin/`. The Makefile rule passes all required inputs as named arguments:

```make
build-openbsm:
    bin/mac/openbsm/build.sh \
        --c-src  $(OPENBSM_MAC_C_SRC) \
        --output $(OPENBSM_MAC_OUTPUT)
```

Scripts accept named arguments and validate them internally (see the [Bash Guide](BASH.md)). The Makefile provides all values — scripts must not hardcode paths.

## Optional Subcomponents

Some subcomponents are off by default and must be opted into explicitly.

**Kernel modules** — activated by passing `KERNEL_MODULES=true`:

```sh
make KERNEL_MODULES=true
make KERNEL_MODULES=true KERNEL_MODULES_DEBUG=true
```

The Makefile uses a conditional include to wire the kernel build into `build-linuxaudit`:

```make
ifeq ($(KERNEL_MODULES), true)
build-linuxaudit: build-linuxaudit-kernel
endif
```

## Adding a Subcomponent

1. Add a variable block for the new subcomponent's inputs:

   ```make
   # my subcomponent
   MY_INPUT  = $(SPADE_SRC_DIR)/spade/reporter/MyReporter.java
   MY_OUTPUT = $(SPADE_LIB_DIR)/libMyReporter.so
   ```

2. Add `build-` and `clean-` rules that delegate to scripts:

   ```make
   build-mycomponent:
       bin/<platform>/mycomponent/build.sh \
           --input  $(MY_INPUT) \
           --output $(MY_OUTPUT)

   clean-mycomponent:
       @bin/<platform>/mycomponent/clean.sh \
           --output $(MY_OUTPUT)
   ```

3. Wire the build rule into `OS_BUILD` (if OS-conditional) or into `build` directly (if always built).

4. Add `clean-mycomponent` to the `clean` target's dependency list.

## Clean

The `clean` target removes compiled Java classes, `lib/spade.jar`, SSL config, logs, and `tmp/`. It also invokes each subcomponent's clean rule:

```make
clean: clean-macfuse clean-linuxfuse android-clean clean-linuxaudit clean-openbsm
    @rm -rf $(SPADE_BUILD_DIR)
    @rm -rf $(SPADE_JAR)
    ...
```

Subcomponent clean rules are unconditional — they run regardless of whether the corresponding build was activated. Scripts must handle the case where outputs do not exist.
