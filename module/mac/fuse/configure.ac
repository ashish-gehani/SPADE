# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.71])
AC_INIT([spadeMacFUSE], [1.0])
AC_CONFIG_AUX_DIR([../../../build-aux])
AC_CONFIG_MACRO_DIRS([../../../m4])
AM_INIT_AUTOMAKE([1.16 -Wall -Werror foreign])

AC_PROG_CC

AC_PATH_PROG([JAVAC], [javac], [:])
if test "x${JAVAC}" = "x:"; then
    AC_MSG_WARN([javac not found; macfuse build will not work])
fi

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
SPADE_SRC_DIR="${SPADE_ROOT}/src"
AC_SUBST([SPADE_SRC_DIR])
SPADE_BUILD_DIR="${SPADE_ROOT}/build"
AC_SUBST([SPADE_BUILD_DIR])

JAVA_HOME=`java -XshowSettings:all 2>&1 | awk '/java.home/{print $3}'`
if test "x${JAVA_HOME}" = "x"; then
    AC_MSG_ERROR([could not determine JAVA_HOME from java -XshowSettings:all])
fi
AC_SUBST([JAVA_HOME])

AC_ARG_VAR([JAVAC_HEADER_GEN_OPTIONS], [Options passed to javac for JNI header generation])
if test "x${JAVAC_HEADER_GEN_OPTIONS}" = "x"; then
    AC_MSG_ERROR([JAVAC_HEADER_GEN_OPTIONS is required; set it with ./configure JAVAC_HEADER_GEN_OPTIONS=<options>])
fi

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
