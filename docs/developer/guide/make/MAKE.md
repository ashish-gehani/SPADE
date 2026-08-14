# Make Guide

## Overview

SPADE uses an autoconf/automake build system. `configure.ac` and `Makefile.am` at the project root orchestrate all subpackage builds. Running `./configure` + `make` is the complete build.

See [`PROJECT-STRUCTURE.md`](../PROJECT-STRUCTURE.md) for how the project is organized into a top-level package and subpackages, and [`subpackage/README.md`](../../../../subpackage/README.md) for the general guidelines every subpackage follows (structure, variable naming, build artifacts, platform grouping). This doc covers the top-level workflow only.

The top-level `configure.ac` computes and exports all variables needed by subpackages.

*(Top-level `configure.ac` specifics to be added here.)*

## Workflow

```sh
# First-time setup (or after editing any configure.ac or Makefile.am)
autoreconf -fi

# Configure — platform detected automatically
./configure

# Build
make

# Clean build artifacts
make clean

# Remove configure-generated files (for developers)
make distclean

# Remove all autotools-generated files (for developers)
make maintainer-clean
```

Every `configure.ac` disables maintainer mode by default (`AM_MAINTAINER_MODE([disable])`), so a plain `./configure` + `make` never tries to regenerate `configure`/`Makefile.in`/`aclocal.m4` on its own — `autoreconf -fi` is the only supported way to regenerate them.

## Configure Variables

See the full, authoritative list — including every subpackage's variables — with:

```sh
./configure --help=recursive
```

## Platform Detection

`configure.ac` uses `AC_CANONICAL_HOST`:

- `darwin*` → configures `subpackage/mac`
- `linux*`  → configures `subpackage/linux`
- anything else → configure error

Each platform's own `configure.ac`/`Makefile.am` groups and enables/disables its subpackages — see [`subpackage/README.md`](../../../../subpackage/README.md).

## Post-Build Steps

After all subpackage builds complete, the root `all-local` target runs `setup`:

1. `bin/manage-neo4j.sh install` — installs the Neo4j distribution into `lib/`.
2. `bin/keys/generatekeys.sh` — generates SSL keys into `cfg/keys/`.

## Clean

`make clean` removes:

- All subpackage build artifacts (via recursive `clean` into each subdir).
- Runtime directories: `cfg/ssl`, `log`, `tmp`.

## Adding a Subpackage

See [`SUBPACKAGE.md`](SUBPACKAGE.md) and [`subpackage/README.md`](../../../../subpackage/README.md) for the guidelines to follow.
