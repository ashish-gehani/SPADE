# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.71])
AC_INIT([spadeAuditBridge], [1.0])
AC_CONFIG_AUX_DIR([../../../build-aux])
AC_CONFIG_MACRO_DIRS([../../../m4])
AM_INIT_AUTOMAKE([1.16 -Wall -Werror foreign])

AC_PROG_CC

AC_ARG_VAR([SPADE_ROOT], [Path to SPADE root directory])
if test "x${SPADE_ROOT}" = "x"; then
    AC_MSG_ERROR([SPADE_ROOT is required; set it with ./configure SPADE_ROOT=<path>])
fi
SPADE_SRC_DIR="${SPADE_ROOT}/src"
AC_SUBST([SPADE_SRC_DIR])

AC_ARG_VAR([AUDIT_BRIDGE_BUILD_DIR], [Directory for audit bridge build artifacts (default: <builddir>/build)])
if test "x${AUDIT_BRIDGE_BUILD_DIR}" = "x"; then
    AUDIT_BRIDGE_BUILD_DIR="${ac_pwd}/build"
fi
AC_SUBST([AUDIT_BRIDGE_BUILD_DIR])

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
