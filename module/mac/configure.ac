# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.73])
AC_INIT([spadeMac], [1.0])
AC_CONFIG_AUX_DIR([../../build-aux])
AC_CONFIG_MACRO_DIRS([../../m4])
AM_INIT_AUTOMAKE([1.18 -Wall -Werror foreign])

# --- fuse detection ---

AC_PATH_PROG([PKG_CONFIG], [pkg-config], [:])
have_fuse=no
if test "x${PKG_CONFIG}" != "x:"; then
    if ${PKG_CONFIG} --exists fuse 2>/dev/null; then
        have_fuse=yes
    else
        AC_MSG_WARN([fuse not found via pkg-config; skipping fuse submodule])
    fi
else
    AC_MSG_WARN([pkg-config not found; skipping fuse submodule])
fi
AM_CONDITIONAL([HAVE_FUSE], [test "x${have_fuse}" = "xyes"])

# --- llvm detection ---

AC_PATH_PROG([LLVM_CONFIG], [llvm-config], [:])
have_llvm=no
if test "x${LLVM_CONFIG}" != "x:"; then
    have_llvm=yes
else
    AC_MSG_WARN([llvm-config not found; skipping llvm submodule])
fi
AM_CONDITIONAL([HAVE_LLVM], [test "x${have_llvm}" = "xyes"])

# --- openbsm detection ---

AC_CHECK_LIB([bsm], [au_open],
    [have_openbsm=yes],
    [have_openbsm=no])
if test "x${have_openbsm}" = "xno"; then
    AC_MSG_WARN([libbsm not found; skipping openbsm submodule])
fi
AM_CONDITIONAL([HAVE_OPENBSM], [test "x${have_openbsm}" = "xyes"])

if test "x${have_fuse}" = "xyes"; then
    AC_CONFIG_SUBDIRS([fuse])
fi
if test "x${have_llvm}" = "xyes"; then
    AC_CONFIG_SUBDIRS([llvm])
fi
if test "x${have_openbsm}" = "xyes"; then
    AC_CONFIG_SUBDIRS([openbsm])
fi

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
