# Compile Script Guide

> No non-Java modules use Maven currently. This guide is preserved for when native modules are added back.

## Overview

A `compile.sh` script builds the artifacts for a module. It is invoked by the Ant buildfile's `compile` target, which Maven triggers during the `compile` phase — but only when the module's `check.sh` did not skip it.

## Exit Code

`compile.sh` exits non-zero on any error: unknown arguments, missing required arguments, failed validation, or build failure. Successful completion exits `0`.

## Conventions

### Design principle

`compile.sh` has one responsibility: build. It writes all output — final artifacts and intermediates — into `${project.build.directory}` (Maven's per-module `target/` directory). It does not install, copy, or notify.

- **Final artifacts** (binaries, shared libraries, jars) are placed in `target/` by `compile.sh`. Maven's `package` execution in the POM copies them to their final location (`lib/`, `bin/`, etc.). This keeps the script focused on building and lets Maven own the placement step.
- **Intermediate files** (JNI headers, object files, bitcode) also go into `target/`, not into the source tree. Maven's clean plugin removes `target/` automatically, so intermediates are never left behind and never pollute the source.

Post-build notices (e.g. `chown`/`chmod` instructions for setuid binaries) belong in the POM's `package` execution, not in `compile.sh`, so they reference the final installed path.

### Arguments

Each argument names an input path, output path, or build flag. Arguments map directly to the properties declared in the module's `pom.xml` and passed through `build.xml`.

All arguments are required unless documented otherwise. Required arguments are validated in `validate_args` with an explicit error message and `exit 1`. Optional arguments (e.g. `--javac-options`) are accepted but not validated — the build function uses a safe default when they are empty.

Unknown arguments print an error and exit non-zero.

### Validation

`validate_args` always runs and always checks at minimum that required arguments are non-empty. It may also validate argument types:

```bash
if [[ ! -d "${SPADE_BUILD}" ]]; then
    echo "Error: --spade-build '${SPADE_BUILD}' is not a directory"
    exit 1
fi
```

### Constants

Hardcoded values (e.g. a pinned toolchain path) go in a `# constants` block above the globals, not as arguments:

```bash
# constants
LLVM_PATH="/var/clang+llvm-3.0-x86_64-apple-darwin11"

# globals
OUTPUT=
```

### Build functions

The build logic lives in one or more named functions called from `main`. Multi-step builds split into separate functions (e.g. `compile_java` then `build_native`). Keep each function focused on one build step.

### Output path and build directory

The POM declares two properties for each output artifact:

```xml
<artifact.path>${spade.lib.dir}/libFoo.so</artifact.path>
<artifact.path.build>${project.build.directory}/libFoo.so</artifact.path.build>
```

`build.xml` passes `${artifact.path.build}` to `compile.sh`. The POM's `install-*` execution at `package` copies from `${artifact.path.build}` to `${artifact.path}`. `clean.sh` only needs to remove `${artifact.path}` — Maven's clean plugin handles `target/`.

When the build tool has its own install step (e.g. a Makefile with a separate `install` target), point that tool's install destination at `${project.build.directory}`. The tool's build step populates the build dir; its install step copies into it. The POM then copies to the final location at `package`. `clean.sh` still receives the final location so the build tool's clean step removes installed artifacts there.

### JNI header directory

Pass `${project.build.directory}` as the `javac -h` output directory. Since the header is no longer co-located with the C source, add it explicitly to the compiler's include path:

```bash
gcc ... -I"${NATIVE_HEADER_DIR}" ...
```

### Post-build notices

Post-build notices belong in the POM's `install-*` execution, not in `compile.sh`:

```xml
<echo message="-----> IMPORTANT: sudo chown root ${artifact.path}"/>
<echo message="-----> IMPORTANT: sudo chmod ug+s ${artifact.path}"/>
```

## Structure

```bash
#!/bin/bash

# constants (only if needed for hardcoded toolchain paths etc.)
# TOOL_PATH="/path/to/tool"

# globals
ARG_ONE=""
ARG_TWO=""


function print_help() {
    echo "Usage: $(basename "$0") --arg-one <path> --arg-two <path>"
    echo ""
    echo "Options:"
    echo "    --arg-one <path>   Description"
    echo "    --arg-two <path>   Description"
    echo "    --help             Show this message and exit"
    exit 0
}

function parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --arg-one) ARG_ONE="$2"; shift 2 ;;
            --arg-two) ARG_TWO="$2"; shift 2 ;;
            --help) print_help ;;
            *) echo "Unknown argument: $1"; exit 1 ;;
        esac
    done
}

function validate_args() {
    if [[ -z "${ARG_ONE}" ]]; then
        echo "Error: --arg-one is required"
        exit 1
    fi
    if [[ -z "${ARG_TWO}" ]]; then
        echo "Error: --arg-two is required"
        exit 1
    fi
}

function build() {
    # compilation logic
}

function main() {
    parse_args "$@"
    validate_args
    build
}

main "$@"
```

## Maven Integration

The `compile` target in `build.xml` calls `compile.sh`, passing POM properties as named arguments:

```xml
<target name="compile">
  <exec executable="${spade.bin.dir}/build/<platform>/<module>/compile.sh"
        dir="${spade.root}"
        failonerror="true">
    <arg value="--arg-one"/> <arg value="${module.arg.one}"/>
    <arg value="--arg-two"/> <arg value="${module.arg.two}"/>
  </exec>
</target>
```

The corresponding POM execution reads the skip property set by the check execution:

```xml
<execution>
  <id>compile-<module></id>
  <phase>compile</phase>
  <goals><goal>run</goal></goals>
  <configuration>
    <skip>${<module>.skip}</skip>
    <target>
      <ant antfile="${project.basedir}/build.xml" target="compile"/>
    </target>
  </configuration>
</execution>
```

## Adding a Module

### 1. Write `compile.sh`

Create `bin/build/<platform>/<module>/compile.sh`. Declare one global variable per argument. Implement `parse_args`, `validate_args`, one or more build functions, and `main` following the structure above. Make it executable:

```bash
chmod +x bin/build/<platform>/<module>/compile.sh
```

### 2. Add the `compile` target to `build.xml`

In `module/<platform>/<module>/build.xml`, add a `compile` target that calls `compile.sh` with the artifact paths drawn from POM properties:

```xml
<target name="compile">
  <exec executable="${spade.bin.dir}/build/<platform>/<module>/compile.sh"
        dir="${spade.root}"
        failonerror="true">
    <arg value="--arg-one"/> <arg value="${module.arg.one}"/>
  </exec>
</target>
```

### 3. Wire the POM execution

In the module's `pom.xml`, add the `compile-*` execution inside `<build>`. It must read the skip property exported by the `check-*` execution via `<skip>${<module>.skip}</skip>`. See [CHECK.md](CHECK.md) for the full check/compile/clean POM pattern.
