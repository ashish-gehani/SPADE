# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.71])
AC_INIT([spadeAuditBridge], [2.0])
AC_CONFIG_AUX_DIR([../../../build-aux])
AC_CONFIG_MACRO_DIRS([../../../m4])
AM_INIT_AUTOMAKE([1.16 -Wall -Werror foreign])
AM_MAINTAINER_MODE([disable])

AC_PROG_CC

AC_ARG_VAR([SPADE_PACKAGE_BIN_DIR], [Path to SPADE top-level path for binary output directory])
if test "x${SPADE_PACKAGE_BIN_DIR}" = "x"; then
    AC_MSG_ERROR([SPADE_PACKAGE_BIN_DIR is required; set it with ./configure SPADE_PACKAGE_BIN_DIR=<path>])
fi

AC_ARG_VAR([SPADE_PACKAGE_AUDIT_BRIDGE_BIN_NAME], [Name of SPADE top-level binary output file])
if test "x${SPADE_PACKAGE_AUDIT_BRIDGE_BIN_NAME}" = "x"; then
    AC_MSG_ERROR([SPADE_PACKAGE_AUDIT_BRIDGE_BIN_NAME is required; set it with ./configure SPADE_PACKAGE_AUDIT_BRIDGE_BIN_NAME=<path>])
fi

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
