# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.71])
AC_INIT([spadeAuditBridge], [2.0])
AC_CONFIG_AUX_DIR([../../../build-aux])
AC_CONFIG_MACRO_DIRS([../../../m4])
AM_INIT_AUTOMAKE([1.16 -Wall -Werror foreign])
AM_MAINTAINER_MODE([disable])

AC_PROG_CC

AC_ARG_VAR([SPADE_JAVA_SRC_DIR], [Path to SPADE src directory])
if test "x${SPADE_JAVA_SRC_DIR}" = "x"; then
    AC_MSG_ERROR([SPADE_JAVA_SRC_DIR is required; set it with ./configure SPADE_JAVA_SRC_DIR=<path>])
fi

AC_ARG_VAR([SPADE_AUDIT_BRIDGE_BIN_DIR], [Path to SPADE audit bridge bin directory])
if test "x${SPADE_AUDIT_BRIDGE_BIN_DIR}" = "x"; then
    AC_MSG_ERROR([SPADE_AUDIT_BRIDGE_BIN_DIR is required; set it with ./configure SPADE_AUDIT_BRIDGE_BIN_DIR=<path>])
fi

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
