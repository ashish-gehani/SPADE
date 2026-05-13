# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.73])
AC_INIT([spadeLinuxFUSE], [1.0])
AC_CONFIG_AUX_DIR([../build-aux])
AC_CONFIG_MACRO_DIRS([../m4])
AM_INIT_AUTOMAKE([1.18 -Wall -Werror foreign])

AC_PROG_CC

AC_PATH_PROG([JAVAC], [javac], [:])
if test "x${JAVAC}" = "x:"; then
    AC_MSG_WARN([javac not found; linuxfuse build will not work])
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

AC_ARG_VAR([SPADE_SRC_DIR], [Path to SPADE source directory containing spade/reporter/LinuxFUSE.java and libLinuxFUSE.c])
if test "x${SPADE_SRC_DIR}" = "x"; then
    AC_MSG_ERROR([SPADE_SRC_DIR is required; set it with ./configure SPADE_SRC_DIR=<path>])
fi

AC_ARG_VAR([SPADE_BUILD_DIR], [Path to SPADE build directory; used as javac classpath])
if test "x${SPADE_BUILD_DIR}" = "x"; then
    AC_MSG_ERROR([SPADE_BUILD_DIR is required; set it with ./configure SPADE_BUILD_DIR=<path>])
fi

AC_ARG_VAR([JAVA_HOME], [Path to JDK home directory; include/ and include/linux/ are used for JNI headers])
if test "x${JAVA_HOME}" = "x"; then
    AC_MSG_ERROR([JAVA_HOME is required; set it with ./configure JAVA_HOME=<path>])
fi

AC_ARG_VAR([JAVAC_OPTIONS], [Extra options passed to javac])

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
