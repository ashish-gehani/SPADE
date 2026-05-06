# Check Script Guide

## Overview

A `check.sh` script is the pre-compile gate for a module. Before Maven runs `compile.sh`, it runs `check.sh`. The exit code tells Maven whether to proceed.

This mirrors the role of a CI pre-check: if the environment does not satisfy the module's prerequisites, the build is skipped rather than attempted and failed.

## Exit Code

| Exit code | Meaning |
|---|---|
| `0` | Prerequisites met — Maven proceeds with compilation. |
| `1` | Prerequisites not met — Maven skips compilation. |
| `1` (fatal) | The check itself could not be performed — Maven stops immediately. |

The distinction between "not met" and "fatal" matters: a missing optional dependency is a soft skip; an environment that cannot even be interrogated is a hard failure.

## Argument: `--silent`

By default, `check.sh` prints a `checking <what>... <result>` line for each unmet prerequisite. Pass `--silent` to suppress that output. Fatal errors are always printed regardless of `--silent`.

```bash
check.sh           # prints checking messages for unmet prerequisites
check.sh --silent  # suppresses prerequisite checking messages
```

## Output Format

Messages follow the configure-script convention:

```
checking <what>... <result>
```

For example:

```
checking wsl... not supported
checking architecture... arm64 (only x86_64 supported)
checking distro... fedora (only ubuntu supported)
checking kernel version... 6.10 (must be between 5.4 and 6.8)
```

## Structure

Each individual check is a separate function that calls `checking` and returns `0` (pass) or `1` (fail). `check_prereqs` calls them all and accumulates the result so every condition is reported in a single run.

```bash
function checking() {
    if (( SILENT == 0 )); then
        echo "checking $1... $2"
    fi
}

function check_<name>() {
    if <prerequisite_not_met>; then
        checking "<what>" "<result>"
        return 1
    fi
    return 0
}

function check_prereqs() {
    local prereqs_err=0

    check_<name> || prereqs_err=1
    ...

    exit "${prereqs_err}"
}
```

For a check that cannot be performed at all (e.g. a required system file is absent), call `checking` then `exit 1` immediately rather than returning:

```bash
if <cannot_check>; then
    checking "<what>" "<result>"
    exit 1
fi
```
