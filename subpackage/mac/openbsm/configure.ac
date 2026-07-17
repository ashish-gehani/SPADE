# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.71])
AC_INIT([spadeOpenBSM], [2.0])
AC_CONFIG_AUX_DIR([../../../build-aux])
AC_CONFIG_MACRO_DIRS([../../../m4])
AM_INIT_AUTOMAKE([1.16 -Wall -Werror foreign])

AC_PROG_CC

# libbsm availability is checked by the parent configure.ac, which only
# configures this subpackage when libbsm is found.
LIBBSM_LIBS="-lbsm"
AC_SUBST([LIBBSM_LIBS])

AC_ARG_VAR([SPADE_JAVA_SRC_DIR], [Path to SPADE src directory])
if test "x${SPADE_JAVA_SRC_DIR}" = "x"; then
    AC_MSG_ERROR([SPADE_JAVA_SRC_DIR is required; set it with ./configure SPADE_JAVA_SRC_DIR=<path>])
fi

AC_ARG_VAR([SPADE_OPENBSM_BIN_DIR], [Path to SPADE OpenBSM bin directory])
if test "x${SPADE_OPENBSM_BIN_DIR}" = "x"; then
    AC_MSG_ERROR([SPADE_OPENBSM_BIN_DIR is required; set it with ./configure SPADE_OPENBSM_BIN_DIR=<path>])
fi

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
