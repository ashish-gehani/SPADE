# Bash Guide

## Header

Every script must begin with the shebang followed by the license header. Use the current year in the copyright line.

```bash
#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.
```

## Indentation

Use 4-space indentation. Do not use tabs.

## Script Structure

Every script must follow this structure:

1. Shebang and license header
2. Blank line
3. `source` any required files
4. Two blank lines
5. Global variable declarations
6. Two blank lines
7. Define `print_help`
8. Define `parse_args`
9. Define `validate_args`
10. Define `main`
11. Call `main "$@"`

```bash
#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"


# constants
FIXED_VAL="something"

# globals
FOO=""
BAR=""


print_help() {
    ...
}

parse_args() {
    ...
}

validate_args() {
    ...
}

main() {
    parse_args "$@"
    validate_args
    do_foo
    do_bar
}

main "$@"
```

## Help

Define a `print_help` function that prints usage and exits. Call it when `--help` is passed or when required arguments are missing.

```bash
print_help() {
    echo "Usage: $(basename "$0") --foo <path> --bar <path>"
    echo ""
    echo "Options:"
    echo "    --foo <path>    Description of foo"
    echo "    --bar <path>    Description of bar"
    echo "    --help          Show this message and exit"
    exit 0
}
```

Handle `--help` inside `parse_args`:

```bash
parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --help) print_help ;;
            ...
        esac
    done
}
```

## Argument Parsing

Scripts must accept named arguments (e.g. `--foo bar`). Parse them in a dedicated `parse_args` function using a `while`/`case` loop. Unknown arguments are an error.

```bash
parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --foo) FOO="$2"; shift 2 ;;
            --bar) BAR="$2"; shift 2 ;;
            *) echo "Unknown argument: $1"; exit 1 ;;
        esac
    done
}
```

## Argument Validation

Validate arguments in a separate `validate_args` function. Do not mix validation into `parse_args`.

```bash
validate_args() {
    if [[ ! -x "${FOO}" ]]; then
        echo "Error: --foo '${FOO}' is not executable"
        exit 1
    fi
}
```

## Main Function

`main` should only orchestrate: call `parse_args`, `validate_args`, and named functions for each logical step. Do not put inline logic or commands directly in `main`.

```bash
# correct
main() {
    parse_args "$@"
    validate_args
    compile
    package
}

# incorrect
main() {
    parse_args "$@"
    validate_args
    mkdir -p "${BUILD}"
    javac -cp "${CP}" src/*.java
    dx --dex --output=out.jar build/
}
```

## Global Variables

Top-level variables are either constants or globals. Constants are fixed values that never change. Globals are set by `parse_args` and updated at runtime. Constants come first, each group marked with a comment. Two blank lines on each side of the block.

```bash
# constants
DEVICE_SDCARD="/sdcard"
DEVICE_SPADE_DIR="${DEVICE_SDCARD}/spade"

# globals
FOO=""
BAR=""
```

If the script is intended to be sourced by other scripts, prefix all top-level variables with a unique identifier (e.g. the script name in uppercase with underscores) to avoid collisions.

```bash
# in build.sh
BUILD_FOO=""
BUILD_BAR=""

# in env.sh
ENV_JAVA_HOME=""
ENV_SPADE_ROOT=""
```

## Local Variables

Declare all variables used only within a single function with `local`. Only variables shared across multiple functions should be global (see Global Variables above).

```bash
my_func() {
    local result
    result="$(some_command)"
    echo "${result}"
}
```

## Conditionals

Use explicit `if` blocks for all conditional logic. Do not use `||` or `&&` as inline control flow substitutes.

```bash
# correct
result="$(some_command)"
if [[ $? -ne 0 ]]; then
    echo "Error: some_command failed"
    exit 1
fi

# incorrect
some_command || { echo "Error: some_command failed"; exit 1; }
some_command && do_next
```

## Quoting

Double-quote all variable expansions to prevent word splitting and glob expansion. Exceptions are intentional glob expansions and arithmetic contexts.

```bash
# correct
echo "${MY_VAR}"
cp "${SRC}" "${DEST}"
${JAVAC} ${JAVAC_OPTIONS} -cp "${JAVAC_CP}" ...   # JAVAC_OPTIONS intentionally unquoted to allow splitting

# incorrect
echo $MY_VAR
cp $SRC $DEST
```

Always quote arguments passed to external commands when they may contain spaces or special characters.
