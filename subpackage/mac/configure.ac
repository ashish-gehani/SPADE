# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.71])
AC_INIT([spadeMac], [2.0])
AC_CONFIG_AUX_DIR([../../build-aux])
AC_CONFIG_MACRO_DIRS([../../m4])
AM_INIT_AUTOMAKE([1.16 -Wall -Werror foreign])
AM_MAINTAINER_MODE([disable])

# Must be called unconditionally so automake's AMDEP infrastructure is
# initialized at the top level, not inside a conditional block (e.g. the
# AC_CHECK_LIB call in the openbsm_bridge section would otherwise pull AC_PROG_CC
# in conditionally, leaving AMDEP undefined when openbsm_bridge is disabled).
AC_PROG_CC

AC_PATH_PROG([PKG_CONFIG], [pkg-config], [:])

# --- fuse detection ---

AC_ARG_ENABLE([fuse],
    [AS_HELP_STRING([--enable-fuse], [build Mac FUSE reporter (default: no)])],
    [enable_fuse=${enableval}],
    [enable_fuse=no])
have_fuse=no
if test "x${enable_fuse}" = "xyes"; then
    if test "x${PKG_CONFIG}" != "x:"; then
        if ${PKG_CONFIG} --exists fuse 2>/dev/null; then
            have_fuse=yes
        else
            AC_MSG_ERROR([fuse not found via pkg-config])
        fi
    else
        AC_MSG_ERROR([pkg-config not found; it is required to locate fuse])
    fi
fi
AM_CONDITIONAL([HAVE_FUSE], [test "x${have_fuse}" = "xyes"])

# --- llvm detection ---

AC_ARG_ENABLE([llvm],
    [AS_HELP_STRING([--enable-llvm], [build Mac LLVM reporters (default: no)])],
    [enable_llvm=${enableval}],
    [enable_llvm=no])
have_llvm=no
if test "x${enable_llvm}" = "xyes"; then
    AC_PATH_PROG([LLVM_CONFIG], [llvm-config], [:])
    if test "x${LLVM_CONFIG}" != "x:"; then
        have_llvm=yes
    else
        AC_MSG_ERROR([llvm-config not found])
    fi
fi
AM_CONDITIONAL([HAVE_LLVM], [test "x${have_llvm}" = "xyes"])

# --- openbsm_bridge detection ---

AC_ARG_ENABLE([openbsm-bridge],
    [AS_HELP_STRING([--enable-openbsm-bridge], [build OpenBSM reporter (default: no)])],
    [enable_openbsm_bridge=${enableval}],
    [enable_openbsm_bridge=no])
have_openbsm_bridge=no
if test "x${enable_openbsm_bridge}" = "xyes"; then
    AC_CHECK_LIB([bsm], [au_open],
        [have_openbsm_bridge=yes],
        [have_openbsm_bridge=no])
    if test "x${have_openbsm_bridge}" = "xno"; then
        AC_MSG_ERROR([libbsm not found])
    fi
fi
AM_CONDITIONAL([HAVE_OPENBSM_BRIDGE], [test "x${have_openbsm_bridge}" = "xyes"])

if test "x${have_fuse}" = "xyes"; then
    AC_CONFIG_SUBDIRS([fuse])
fi
if test "x${have_llvm}" = "xyes"; then
    AC_CONFIG_SUBDIRS([llvm])
fi
if test "x${have_openbsm_bridge}" = "xyes"; then
    AC_CONFIG_SUBDIRS([openbsm_bridge])
fi

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
