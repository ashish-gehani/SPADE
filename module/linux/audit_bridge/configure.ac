# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.73])
AC_INIT([spadeAuditBridge], [1.0])
AC_CONFIG_AUX_DIR([../build-aux])
AC_CONFIG_MACRO_DIRS([../m4])
AM_INIT_AUTOMAKE([1.18 -Wall -Werror foreign])

AC_PROG_CC

AC_ARG_VAR([SPADE_SRC_DIR], [Path to SPADE source directory containing spade/reporter/spadeAuditBridge.c])
if test "x${SPADE_SRC_DIR}" = "x"; then
    AC_MSG_ERROR([SPADE_SRC_DIR is required; set it with ./configure SPADE_SRC_DIR=<path>])
fi

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
