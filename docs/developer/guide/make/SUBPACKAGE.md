# How to Add a Subpackage

Use `subpackage/linux/fuse` as the reference implementation.

## Files to create

### `configure.ac`

Created by hand. Points `AC_CONFIG_AUX_DIR` and `AC_CONFIG_MACRO_DIRS` at the shared project-root directories. The depth determines the relative path:

| Depth | Example | Paths |
|-------|---------|-------|
| `subpackage/<name>/` | `subpackage/java/` | `../../build-aux`, `../../m4` |
| `subpackage/<platform>/<name>/` | `subpackage/linux/fuse/` | `../../../build-aux`, `../../../m4` |

`subpackage/linux/fuse/configure.ac`:

```autoconf
AC_PREREQ([2.71])
AC_INIT([spadeLinuxFUSE], [2.0])
AC_CONFIG_AUX_DIR([../../../build-aux])
AC_CONFIG_MACRO_DIRS([../../../m4])
AM_INIT_AUTOMAKE([1.16 -Wall -Werror foreign])

AC_PROG_CC

AC_PATH_PROG([JAVA], [java], [:])
if test "x${JAVA}" = "x:"; then
    AC_MSG_ERROR([java not found])
fi

JAVA_HOME=`"${JAVA}" -XshowSettings:all 2>&1 | awk '/java.home/{print $3}'`
if test "x${JAVA_HOME}" = "x"; then
    AC_MSG_ERROR([could not determine JAVA_HOME from java -XshowSettings:all])
fi
AC_SUBST([JAVA_HOME])

AC_PATH_PROG([PKG_CONFIG], [pkg-config], [:])
if test "x${PKG_CONFIG}" = "x:"; then
    AC_MSG_ERROR([pkg-config not found; it is required to locate fuse])
fi
if ! ${PKG_CONFIG} --exists fuse 2>/dev/null; then
    AC_MSG_ERROR([fuse not found via pkg-config])
fi
FUSE_CFLAGS=$(${PKG_CONFIG} fuse --cflags --libs)
AC_SUBST([FUSE_CFLAGS])

AC_ARG_VAR([SPADE_ROOT], [Path to SPADE root directory])
if test "x${SPADE_ROOT}" = "x"; then
    AC_MSG_ERROR([SPADE_ROOT is required; set it with ./configure SPADE_ROOT=<path>])
fi

AC_ARG_VAR([SPADE_FUSE_LIB_DIR], [Path to SPADE FUSE lib directory])
if test "x${SPADE_FUSE_LIB_DIR}" = "x"; then
    AC_MSG_ERROR([SPADE_FUSE_LIB_DIR is required; set it with ./configure SPADE_FUSE_LIB_DIR=<path>])
fi

AC_ARG_VAR([SPADE_JAVA_BUILD_NATIVE_INCLUDE_DIR], [Path to SPADE build native include directory])
if test "x${SPADE_JAVA_BUILD_NATIVE_INCLUDE_DIR}" = "x"; then
    AC_MSG_ERROR([SPADE_JAVA_BUILD_NATIVE_INCLUDE_DIR is required; set it with ./configure SPADE_JAVA_BUILD_NATIVE_INCLUDE_DIR=<path>])
fi

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
```

Key rules:
- Use `AC_PATH_PROG` for tools; warn if optional, error if required.
- Derive `JAVA_HOME` inline via `java -XshowSettings:all`; do not declare it as `AC_ARG_VAR` — it isn't passed down from a parent, so it's just a local `AC_SUBST`ed variable.
- Every variable the parent exports (e.g. `SPADE_ROOT`, `SPADE_FUSE_LIB_DIR`, `SPADE_JAVA_BUILD_NATIVE_INCLUDE_DIR`) is declared with `AC_ARG_VAR` and must error via `AC_MSG_ERROR` if unset — do not default it or re-derive it; the parent build script already computed it. `AC_ARG_VAR` performs the substitution itself, so don't also `AC_SUBST` these.
- Local, non-exported variables (like `FUSE_CFLAGS` above) don't need the `SPADE_` prefix, and do need an explicit `AC_SUBST` since they aren't declared via `AC_ARG_VAR`.
- After any change to `configure.ac` or `Makefile.am`, regenerate with `autoreconf -fi`.

### `Makefile.am`

Define these targets:

- `all-local` — build everything directly into the exported `SPADE_*_DIR` (e.g. `$(SPADE_FUSE_LIB_DIR)`), which already points into the live project tree; do not install anywhere.
- `install-exec-local` — copy built artifacts out using `$(DESTDIR)$(prefix)`.
- `uninstall-local` — reverse of `install-exec-local`.
- `clean-local` — remove only the generated files this subpackage produced, not the shared directory itself.
- `distclean-local` — call `clean-local`.
- `maintainer-clean-local` — call `distclean-local`, then remove autotools-generated files, sorted and one per line with a trailing backslash.

`subpackage/linux/fuse/Makefile.am`:

```makefile
FUSE_BINARY         = libLinuxFUSE.so
FUSE_C_SRC          = $(SPADE_ROOT)/src/spade/reporter/libLinuxFUSE.c
FUSE_INSTALL_LIBDIR = $(DESTDIR)$(prefix)/lib

all-local:
	@$(MKDIR_P) "$(SPADE_FUSE_LIB_DIR)"
	$(CC) -fPIC -shared \
		-Wl,-soname,$(FUSE_BINARY) \
		-I"$(JAVA_HOME)/include" \
		-I"$(JAVA_HOME)/include/linux" \
		-I"$(SPADE_JAVA_BUILD_NATIVE_INCLUDE_DIR)" \
		-Wall \
		"$(FUSE_C_SRC)" \
		$(FUSE_CFLAGS) \
		-o "$(SPADE_FUSE_LIB_DIR)/$(FUSE_BINARY)"

install-exec-local:
	$(MKDIR_P) "$(FUSE_INSTALL_LIBDIR)"
	$(INSTALL_DATA) "$(SPADE_FUSE_LIB_DIR)/$(FUSE_BINARY)" \
		"$(FUSE_INSTALL_LIBDIR)/$(FUSE_BINARY)"

uninstall-local:
	rm -f "$(FUSE_INSTALL_LIBDIR)/$(FUSE_BINARY)"

clean-local:
	rm -f "$(SPADE_FUSE_LIB_DIR)/$(FUSE_BINARY)"

distclean-local: clean-local

maintainer-clean-local: distclean-local
	rm -rf aclocal.m4 \
	       autom4te.cache \
	       build-aux \
	       configure \
	       Makefile.in
```

`SPADE_FUSE_LIB_DIR` is the same directory the build writes to and `clean-local` cleans — there is no separate local `build/` staging directory; the parent-derived `SPADE_*_DIR` already points into the project tree.

JNI headers (`SPADE_JAVA_BUILD_NATIVE_INCLUDE_DIR`) are produced by `mvn package` in `subpackage/java` and must exist before the fuse subpackage is built — the root build order guarantees this via `SUBDIRS`.

Use `$(INSTALL_PROGRAM)` for standalone executables and plugin shared libraries; use `$(INSTALL_DATA)` for JNI shared libraries (`.so`, `.jnilib`) loaded by the JVM and for data files.

### `.gitignore`

`.gitignore` lives at the platform level (`subpackage/linux/.gitignore`, `subpackage/mac/.gitignore`), not per-subpackage.

## Autogenerated files — do not edit

Produced by `autoreconf -fi`; never edit directly:

- `autom4te.cache/`
- `aclocal.m4`
- `configure`
- `configure~`
- `Makefile.in`

`build-aux/` is shared at the project root and referenced via the relative path in `AC_CONFIG_AUX_DIR`. The authoritative sources are `configure.ac` and `Makefile.am`.

## Registering the subpackage

After creating the files, register in the parent platform configure and Makefile:

**`subpackage/linux/configure.ac`** — add detection and conditional `AC_CONFIG_SUBDIRS`:

```autoconf
AC_PATH_PROG([PKG_CONFIG], [pkg-config], [:])
have_fuse=no
if test "x${PKG_CONFIG}" != "x:"; then
    if ${PKG_CONFIG} --exists fuse 2>/dev/null; then
        have_fuse=yes
    else
        AC_MSG_WARN([fuse not found via pkg-config; skipping fuse subpackage])
    fi
else
    AC_MSG_WARN([pkg-config not found; skipping fuse subpackage])
fi
AM_CONDITIONAL([HAVE_FUSE], [test "x${have_fuse}" = "xyes"])

if test "x${have_fuse}" = "xyes"; then
    AC_CONFIG_SUBDIRS([fuse])
fi
```

**`subpackage/linux/Makefile.am`** — add to `DIST_SUBDIRS` and conditionally to `SUBDIRS`:

```makefile
if HAVE_FUSE
DIST_SUBDIRS += fuse
SUBDIRS += fuse
endif
```

Then run `autoreconf -fi` in the subpackage directory, in `subpackage/linux/`, and at the project root.

## Workflow

```sh
# First-time setup (or after editing configure.ac / Makefile.am)
autoreconf -fi

# Configure — SPADE_ROOT and other vars propagate from the root configure
./configure SPADE_ROOT=/path/to/spade

# Build locally
make

# Install to a specific location
make install prefix=/some/path

# Clean up
make clean              # remove build artifacts
make distclean          # clean + remove configure-generated files
make maintainer-clean   # distclean + remove autotools-generated files
```

In normal development, the root `./configure` + `make` drives everything and each subpackage's configure is called automatically via `AC_CONFIG_SUBDIRS`. Use `./configure --help=recursive` at the root to see every variable every subpackage accepts, rather than re-documenting them per parent/child.
