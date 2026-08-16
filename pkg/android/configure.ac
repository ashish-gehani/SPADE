# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.71])
AC_INIT([spadeAndroid], [2.0])
AC_CONFIG_AUX_DIR([../../build-aux])
AC_CONFIG_MACRO_DIRS([../../m4])
AM_INIT_AUTOMAKE([1.16 -Wall -Werror foreign])
AM_MAINTAINER_MODE([disable])

# Check for dx (Android SDK build tools) — for building android code.
AC_PATH_PROG([DX], [dx], [:])
if test "x${DX}" = "x:"; then
    AC_MSG_WARN([dx not found; android code will not be generated])
fi

AC_ARG_VAR([SPADE_PACKAGE_LIB_DIR], [Path to SPADE top-level (i.e. package) lib directory])
if test "x${SPADE_PACKAGE_LIB_DIR}" = "x"; then
    AC_MSG_ERROR([SPADE_PACKAGE_LIB_DIR is required; set it with ./configure SPADE_PACKAGE_LIB_DIR=<path>])
fi

AC_ARG_VAR([SPADE_PACKAGE_SPADE_JAR_FILE], [Path to SPADE top-level jar file created by java package])
if test "x${SPADE_PACKAGE_SPADE_JAR_FILE}" = "x"; then
    AC_MSG_ERROR([SPADE_PACKAGE_SPADE_JAR_FILE is required; set it with ./configure SPADE_PACKAGE_SPADE_JAR_FILE=<path>])
fi

AC_ARG_VAR([SPADE_PACKAGE_ANDROID_JAR_NAME], [Name of SPADE top-level android jar])
if test "x${SPADE_PACKAGE_ANDROID_JAR_NAME}" = "x"; then
    AC_MSG_ERROR([SPADE_PACKAGE_ANDROID_JAR_NAME is required; set it with ./configure SPADE_PACKAGE_ANDROID_JAR_NAME=<name>])
fi

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
