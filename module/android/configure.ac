# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.73])
AC_INIT([spadeAndroid], [1.0])
AC_CONFIG_AUX_DIR([build-aux])
AM_INIT_AUTOMAKE([1.18 -Wall -Werror foreign])

# Check for dx (Android SDK build tools) — used in android-build target
AC_PATH_PROG([DX], [dx], [:])
if test "x${DX}" = "x:"; then
    AC_MSG_WARN([dx not found; android-build target will not work])
fi

AC_ARG_VAR([SPADE_ROOT], [Path to SPADE root directory])
if test "x${SPADE_ROOT}" = "x"; then
    AC_MSG_ERROR([SPADE_ROOT is required; set it with ./configure SPADE_ROOT=<path>])
fi
SPADE_BUILD_DIR="${SPADE_ROOT}/build"
AC_SUBST([SPADE_BUILD_DIR])

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
