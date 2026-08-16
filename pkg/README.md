# Packages

This directory contains SPADE's packages. General guidelines for a package:

## Structure

- Each package is a standalone autoconf/automake package (`AC_INIT`, version `2.0`), using the shared `build-aux`/`m4` dirs from further up the tree, with `AM_MAINTAINER_MODE([disable])`.
- Sources live under `src/`, kept self-contained per package (a symlink to `pkg/shared/` when the code is shared between packages), for self-contained packages and easier development environment configuration.
- Each package has its own `README.md` documenting itself and noting any deviations from these guidelines.

## Variables

- Variables needed from the parent build are expected explicitly via `AC_ARG_VAR` (fatal `AC_MSG_ERROR` if unset) rather than re-derived locally.
- Variables shared across the whole package are prefixed `SPADE_PACKAGE_`. When a variable's value differs per platform, it gets an OS segment in its name (e.g. `SPADE_PACKAGE_LINUX_*` / `SPADE_PACKAGE_MAC_*`).
- Variable naming reflects what it holds: `_DIR` for a directory, `_NAME` for a name (of a file or directory), `_FILE` for a full file path.

## Build artifacts

- Output artifacts are built from a directory + name pair (e.g. `SPADE_PACKAGE_LIB_DIR` + `SPADE_PACKAGE_*_NAME`) into an `OUTPUT_`-prefixed var, rather than passed in as a single precomputed path.
- `Makefile.am` follows the `all-local` / `clean-local` / `distclean-local` / `maintainer-clean-local` pattern; `distclean-local` and `maintainer-clean-local` are for developers, with `maintainer-clean-local` removing generated autotools artifacts.
- `all-local` builds the artifact locally and copies the result to the specified SPADE package location; it is also responsible for creating the local build directory. `clean-local` cleans both the local build location and the SPADE package location.

## Platform grouping

- Each platform (e.g. `linux/`, `mac/`) has its own `configure.ac`/`Makefile.am` that groups multiple packages together. This is where individual packages are enabled or disabled by default (e.g. via `AC_ARG_ENABLE`) before being configured as subdirs.
