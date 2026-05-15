# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.71])
AC_INIT([spadeOpenBSM], [1.0])
AC_CONFIG_AUX_DIR([../../../build-aux])
AC_CONFIG_MACRO_DIRS([../../../m4])
AM_INIT_AUTOMAKE([1.16 -Wall -Werror foreign])

AC_PROG_CC

# Check for libbsm
AC_CHECK_LIB([bsm], [au_open],
    [HAVE_LIBBSM=yes],
    [HAVE_LIBBSM=no])

if test "x$HAVE_LIBBSM" = "xyes"; then
    AC_DEFINE([HAVE_LIBBSM], [1], [Define if libbsm is available])
    LIBBSM_LIBS="-lbsm"
else
    AC_MSG_WARN([libbsm not found; OpenBSM reporter will not be built])
    LIBBSM_LIBS=""
fi

AM_CONDITIONAL([HAVE_LIBBSM], [test "x$HAVE_LIBBSM" = "xyes"])
AC_SUBST([LIBBSM_LIBS])

AC_ARG_VAR([SPADE_ROOT], [Path to SPADE root directory])
if test "x${SPADE_ROOT}" = "x"; then
    AC_MSG_ERROR([SPADE_ROOT is required; set it with ./configure SPADE_ROOT=<path>])
fi
SPADE_SRC_DIR="${SPADE_ROOT}/src"
AC_SUBST([SPADE_SRC_DIR])

AC_ARG_VAR([OPENBSM_BUILD_DIR], [Directory for OpenBSM build artifacts (default: <builddir>/build)])
if test "x${OPENBSM_BUILD_DIR}" = "x"; then
    OPENBSM_BUILD_DIR="${ac_pwd}/build"
fi
AC_SUBST([OPENBSM_BUILD_DIR])

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
