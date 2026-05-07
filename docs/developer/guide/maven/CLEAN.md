# Clean Script Guide

## Overview

A `clean.sh` script removes the build artifacts produced by a module's `compile.sh`. It is invoked by the Ant buildfile's `clean` target, which Maven triggers during the `clean` phase.

Unlike the check script, the clean execution is **always unconditional** — it runs regardless of whether the build was previously activated. This ensures `mvn clean` reliably removes artifacts even when a `check.sh` would gate the build on the current machine.

## Exit Code

`clean.sh` exits non-zero on unexpected errors (e.g., unknown argument). Successful completion — including the case where there is nothing to remove — exits `0`.

This differs from `check.sh`, which always exits `0` and communicates its result through a status file.

## Conventions

### Arguments

Each argument names an artifact path to remove. Arguments map directly to the properties declared in the module's `pom.xml` and passed through `build.xml`.

- **Required arguments** are validated in `validate_args` with an explicit error message and `exit 1`.
- **Optional arguments** are not validated; `clean` checks whether they are set before removing.

Unknown arguments print an error and exit non-zero.

### Removal

Use `rm -f` for files and `rm -rf` for directories. Optional paths are guarded by a non-empty check before removal.

## Structure

```bash
#!/bin/bash

# globals
ARG_ONE=""
ARG_TWO=""


function print_help() {
    echo "Usage: $(basename "$0") --arg-one <path> [--arg-two <path>]"
    echo ""
    echo "Options:"
    echo "    --arg-one <path>   Path to the primary artifact to remove"
    echo "    --arg-two <path>   Path to the optional artifact to remove"
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
}

function clean() {
    rm -f "${ARG_ONE}"
    [[ -n "${ARG_TWO}" ]] && rm -f "${ARG_TWO}"
}

function main() {
    parse_args "$@"
    validate_args
    clean
}

main "$@"
```

If all arguments are optional, `validate_args` is a no-op stub:

```bash
function validate_args() {
    :
}
```

## Maven Integration

The `clean` target in `build.xml` calls `clean.sh`, passing the same artifact paths that `compile.sh` produced:

```xml
<target name="clean">
  <exec executable="${spade.bin.dir}/build/<platform>/<module>/clean.sh"
        dir="${spade.root}"
        failonerror="true">
    <arg value="--arg-one"/> <arg value="${module.artifact.path}"/>
  </exec>
</target>
```

The corresponding POM execution has no `<skip>` — it is always unconditional:

```xml
<execution>
  <id>clean-<module></id>
  <phase>clean</phase>
  <goals><goal>run</goal></goals>
  <configuration>
    <target>
      <ant antfile="${project.basedir}/build.xml" target="clean"/>
    </target>
  </configuration>
</execution>
```

## Adding a Module

### 1. Write `clean.sh`

Create `bin/build/<platform>/<module>/clean.sh`. Declare one global variable per artifact path. Implement `parse_args`, `validate_args`, `clean`, and `main` following the structure above. Make it executable:

```bash
chmod +x bin/build/<platform>/<module>/clean.sh
```

### 2. Add the `clean` target to `build.xml`

In `module/<platform>/<module>/build.xml`, add a `clean` target that calls `clean.sh` with the artifact paths drawn from POM properties:

```xml
<target name="clean">
  <exec executable="${spade.bin.dir}/build/<platform>/<module>/clean.sh"
        dir="${spade.root}"
        failonerror="true">
    <arg value="--arg-one"/> <arg value="${module.artifact.path}"/>
  </exec>
</target>
```

### 3. Wire the POM execution

In the module's `pom.xml`, add the unconditional `clean-*` execution to the top-level `<build>`. Do not place it inside a profile or add a `<skip>` element.
