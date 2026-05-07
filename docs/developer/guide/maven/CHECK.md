# Check Script Guide

## Overview

A `check.sh` script is the pre-compile gate for a module. Before the Ant buildfile runs `compile.sh`, Maven runs `check.sh`. The result tells Maven whether to proceed with compilation.

If the environment does not satisfy the module's prerequisites, the build is skipped rather than attempted and failed.

## Result

The result is communicated via a status file or stdout — **not** the exit code. `check.sh` always exits `0` unless an unexpected error occurs in the script itself.

| Result value | Meaning |
|---|---|
| `continue` | Prerequisites met — Maven proceeds with compilation. |
| `skip` | Prerequisites not met — Maven skips compilation. |

The result is written as the last line of the status file (or stdout). Maven reads it via `<loadfile>` with a `<tailfilter>` to extract that last line.

## Arguments

| Argument | Description |
|---|---|
| `--status-file <path>` | Write output to a file instead of stdout. The last line is the result (`continue` or `skip`). |
| `--silent` | Suppress `checking` messages. Has no effect when `--status-file` is given, since messages go to the file. |
| `--help` | Print usage and exit. |

## Output Format

Messages follow the configure-script convention, written for each prerequisite checked:

```
checking <what>... <result>
```

For example:

```
checking os... Linux (only macOS supported)
checking gcc... not found
checking fuse... not found
skip
```

The final line is always the result.

## Maven Integration

The check is wired entirely in `pom.xml`. `build.xml` contains only the build targets (`compile`, `clean`, etc.) — it does not have a `check` target.

The check execution runs in Maven's `validate` phase, before `compile`. It calls `check.sh` directly, reads the status file, and — via `exportAntProperties` — communicates the skip decision to Maven as the property `<module>.skip`. The compile execution reads that property via `<skip>${<module>.skip}</skip>`.

`<module>.skip` must not be pre-defined as a Maven `<properties>` entry. Maven passes project properties to Ant as immutable user properties, so pre-defining it would prevent the `<condition>` from setting it. It is documented in `<properties>` as a comment instead. The two `<condition>` blocks ensure the property is always exported as an explicit `true` or `false`.

```xml
<!-- <module>.skip is exported by check-<module> (validate); true skips compile-<module> -->

<execution>
  <id>check-<module></id>
  <phase>validate</phase>
  <goals><goal>run</goal></goals>
  <configuration>
    <exportAntProperties>true</exportAntProperties>
    <target>
      <mkdir dir="${project.build.directory}"/>
      <exec executable="${spade.bin.dir}/<path>/check.sh"
            dir="${spade.root}">
        <arg value="--status-file"/> <arg value="${project.build.directory}/check.status"/>
      </exec>
      <loadfile property="check.result"
                srcFile="${project.build.directory}/check.status"
                failonerror="true">
        <filterchain>
          <tailfilter lines="1"/>
        </filterchain>
      </loadfile>
      <condition property="<module>.skip" value="true">
        <not><equals arg1="${check.result}" arg2="continue" trim="true"/></not>
      </condition>
      <condition property="<module>.skip" value="false">
        <equals arg1="${check.result}" arg2="continue" trim="true"/>
      </condition>
    </target>
  </configuration>
</execution>

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

The status file (`target/check.status`) is kept after the build for inspection.

## Adding a Module

This section walks through adding a new module that uses a `check.sh` gate. The `mac/fuse` module is used as a concrete example throughout.

### 1. Write `check.sh`

Create `bin/<platform>/<module>/check.sh`. Source the utility files that cover the prerequisites you need to check (see [Shared Utilities](#shared-utilities-binutilcheck)), implement the checks, and wire everything through `check_prereqs` and `main`. See [Structure](#structure) for the full template.

```
bin/mac/fuse/check.sh
```

Make the script executable:

```bash
chmod +x bin/mac/fuse/check.sh
```

### 2. Create `compile.sh` and `clean.sh`

Create `bin/<platform>/<module>/compile.sh` and `bin/<platform>/<module>/clean.sh`. These are the scripts that `build.xml` delegates to — they contain the actual build and clean logic. Make them executable:

```bash
chmod +x bin/mac/fuse/compile.sh bin/mac/fuse/clean.sh
```

### 3. Create `build.xml`

Create `module/<platform>/<module>/build.xml`. Include only `compile` and `clean` targets. Do not add a `check` target — the check is invoked directly from `pom.xml`.

```
module/mac/fuse/build.xml
```

```xml
<?xml version="1.0" encoding="UTF-8"?>

<project name="spade-mac-fuse">

  <target name="compile">
    <exec executable="${spade.bin.dir}/mac/fuse/compile.sh"
          dir="${spade.root}"
          failonerror="true">
      <!-- module-specific arguments -->
    </exec>
  </target>

  <target name="clean">
    <exec executable="${spade.bin.dir}/mac/fuse/clean.sh"
          dir="${spade.root}"
          failonerror="true">
      <!-- module-specific arguments -->
    </exec>
  </target>

</project>
```

### 4. Create `pom.xml`

Create `module/<platform>/<module>/pom.xml`. Choose a skip property name scoped to the module (e.g. `fuse.mac.skip`) and wire up the three executions following the [Maven Integration](#maven-integration) pattern. Document the skip property as a comment in `<properties>`.

```
module/mac/fuse/pom.xml
```

```xml
<?xml version="1.0" encoding="UTF-8"?>

<project xmlns="http://maven.apache.org/POM/4.0.0" ...>

  <parent>
    <groupId>io.github.spade</groupId>
    <artifactId>spade-mac</artifactId>
    <version>${revision}</version>
    <relativePath>../pom.xml</relativePath>
  </parent>

  <artifactId>spade-mac-fuse</artifactId>
  <packaging>pom</packaging>

  <name>spade-mac-fuse</name>
  <description>MacFUSE reporter build for SPADE.</description>

  <properties>
    <!-- module-specific properties -->
    <!-- <fuse.mac.skip>false</fuse.mac.skip> exported by check-macfuse (validate); true skips compile-macfuse -->
  </properties>

  <build>
    <plugins>
      <plugin>
        <artifactId>maven-antrun-plugin</artifactId>
        <executions>
          <!-- check, compile, clean executions — see Maven Integration -->
        </executions>
      </plugin>
    </plugins>
  </build>

</project>
```

### 5. Register in the parent `pom.xml`

Add the new module to the `<modules>` list of its parent `pom.xml`:

```xml
<modules>
  <module>fuse/pom.xml</module>
</modules>
```

## Shared Utilities: `bin/util/check`

`bin/util/check` provides the building blocks for every `check.sh`. It is organized into files by topic — each file is sourced independently:

| File | Description |
|---|---|
| `check` | Core globals, argument parsing, status writing, and `util_check_finalize`. Source this when no topic-specific file fits. |
| `os` | OS detection. Sources `check`. |
| `distro` | Linux distro detection. Sources `check`. |
| `compiler` | Compiler detection. Sources `check`. |
| `pkg` | Build-tool detection (`pkg-config`, `make`, `dx`). Sources `check`. |

Because each topic file already sources `check`, sourcing multiple topic files does not duplicate the core.

### `check` — Core

| Name | Type | Description |
|---|---|---|
| `UTIL_CHECK_SILENT` | global | `0` by default; set to `1` by `--silent`. |
| `UTIL_CHECK_STATUS_FILE` | global | Empty by default; set to the file path by `--status-file`. |
| `util_check_parse_args` | function | Parses `--silent`, `--status-file`, and `--help`. |
| `util_check_validate_args` | function | No-op stub; override per script if needed. |
| `util_check_print_help` | function | Prints usage for the common arguments and exits. |
| `util_check_checking` | function | Prints `checking <what>... <result>` to the file or stdout. |
| `util_check_init_status_file` | function | Creates the status file's parent directory and clears the file. |
| `util_check_write_status` | function | Appends the result value to the file or prints it to stdout. |
| `util_check_write_status_continue` | function | Writes `continue`. |
| `util_check_write_status_skip` | function | Writes `skip`. |
| `util_check_finalize` | function | Writes `continue` or `skip` based on a `<skip>` flag (0/1) and exits. |

### `os`

| Name | Type | Description |
|---|---|---|
| `util_check_os_is_darwin` | function | Checks that the OS is macOS; prints a `checking os...` message either way. |
| `util_check_os_is_linux` | function | Checks that the OS is Linux; prints a `checking os...` message either way. |

### `distro`

| Name | Type | Description |
|---|---|---|
| `util_check_distro_is_ubuntu` | function | Checks that `/etc/os-release` identifies the distro as Ubuntu; prints a `checking distro...` message either way. |

### `compiler`

| Name | Type | Description |
|---|---|---|
| `util_check_compiler_has_gcc` | function | Checks that `gcc` is on `PATH`; prints a `checking gcc...` message either way. |
| `util_check_compiler_has_clang` | function | Checks that `clang` is on `PATH`; prints a `checking clang...` message either way. |
| `util_check_compiler_has_clang_plus_plus` | function | Checks that `clang++` is on `PATH`; prints a `checking clang++...` message either way. |

### `pkg`

| Name | Type | Description |
|---|---|---|
| `util_check_pkg_has_pkg_config` | function | Checks that `pkg-config` is on `PATH`; prints a `checking pkg-config...` message either way. |
| `util_check_pkg_has_make` | function | Checks that `make` is on `PATH`; prints a `checking make...` message either way. |
| `util_check_pkg_has_dx` | function | Checks that `dx` is on `PATH`; prints a `checking dx...` message either way. |

## Structure

```bash
source "$(dirname "$0")/../../util/check/os"
source "$(dirname "$0")/../../util/check/compiler"
source "$(dirname "$0")/../../util/check/pkg"

function check_<name>() {
    if <prerequisite_met>; then
        util_check_checking "<what>" "<result>"
        return 0
    fi
    util_check_checking "<what>" "not found"
    return 1
}

function check_prereqs() {
    local check_skip=0
    util_check_init_status_file

    util_check_os_is_darwin
    if (( $? != 0 )); then
        check_skip=1
    fi

    check_<name>
    if (( $? != 0 )); then
        check_skip=1
    fi

    util_check_finalize "${check_skip}"
}

function main() {
    util_check_parse_args "$@"
    util_check_validate_args
    check_prereqs
}

main "$@"
```
