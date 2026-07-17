# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.71])
AC_INIT([spadeLinuxFUSE], [2.0])
AC_CONFIG_AUX_DIR([../../../build-aux])
AC_CONFIG_MACRO_DIRS([../../../m4])
AM_INIT_AUTOMAKE([1.16 -Wall -Werror foreign])
AM_MAINTAINER_MODE([disable])

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
