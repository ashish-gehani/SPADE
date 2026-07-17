# Conventions

## Variable naming

Variables exported from a parent `configure.ac` down to its subpackages are prefixed with `SPADE_` (e.g. `SPADE_JAVA_SRC_DIR`, `SPADE_FUSE_LIB_DIR`). Variables that are local to a subpackage's own build script — used only within that subpackage and never passed further down — do not need the prefix. Add the prefix to a local variable only if it starts being exported to a subpackage below it.

## Passing variables into a subpackage

A subpackage declares every variable it expects from its parent with `AC_ARG_VAR` and errors out if it isn't set, instead of re-deriving or defaulting it:

```autoconf
AC_ARG_VAR([SPADE_FUSE_LIB_DIR], [Path to SPADE FUSE lib directory])
if test "x${SPADE_FUSE_LIB_DIR}" = "x"; then
    AC_MSG_ERROR([SPADE_FUSE_LIB_DIR is required; set it with ./configure SPADE_FUSE_LIB_DIR=<path>])
fi
```

Do not re-derive in a subpackage anything the parent build script can already derive and export — build directories, in particular, are computed once in the root `configure.ac` from `SPADE_ROOT` and exported down; there is no separate "BUILD rule" that derives them per subpackage. When updating a subpackage's build script, check the root `configure.ac` to see how its inputs are actually derived and exported now.

`AC_ARG_VAR` already performs the substitution for you — don't also call `AC_SUBST` on a variable declared with `AC_ARG_VAR`.

To see the full set of configure arguments across the whole tree (root + all subpackages), use:

```sh
./configure --help=recursive
```

rather than redefining or duplicating the same variable's help text in both a parent and its child.

## Build directories

All subpackage build output goes into the SPADE project tree (e.g. `lib/`, `bin/`). These directories are derived from `SPADE_ROOT` in the root `configure.ac` and exported to subpackages — no subpackage defines its own local build path.

## clean-local

`clean-local` must delete only the generated files inside the build directory, not the build directory itself. Build directory variables (e.g. `SPADE_JAVA_BUILD_DIR`, `SPADE_FUSE_LIB_DIR`) point into the live project tree and are shared with other subpackages, so removing the directory would destroy paths this subpackage did not create.

## maintainer-clean-local

List the removed file names sorted alphabetically, one per line, continued with a trailing backslash:

```makefile
maintainer-clean-local: distclean-local
	rm -rf aclocal.m4 \
	       autom4te.cache \
	       build-aux \
	       configure \
	       configure~ \
	       Makefile.in
```

## Versioning

Every `configure.ac` — root and subpackages alike — declares SPADE version `2.0`, e.g. `AC_INIT([spadeLinuxFUSE], [2.0])`.

## Directory layout

Native/platform build trees live under `subpackage/` (e.g. `subpackage/linux/audit_bridge`), not `module/`.
