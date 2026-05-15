# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.73])
AC_INIT([spadeJava], [1.0])
AC_CONFIG_AUX_DIR([../../build-aux])
AC_CONFIG_MACRO_DIRS([../../m4])
AM_INIT_AUTOMAKE([1.18 -Wall -Werror foreign])

AC_PATH_PROG([JAVA], [java], [:])
if test "x${JAVA}" = "x:"; then
    AC_MSG_ERROR([java not found])
fi

AC_PATH_PROG([JAVAC], [javac], [:])
if test "x${JAVAC}" = "x:"; then
    AC_MSG_ERROR([javac not found])
fi

javac_major=`${JAVAC} -version 2>&1 | sed 's/javac \([0-9]*\).*/\1/'`
if test -z "${javac_major}" || test "${javac_major}" -lt 21 2>/dev/null; then
    AC_MSG_ERROR([javac 21 or later is required (found: `${JAVAC} -version 2>&1`)])
fi

AC_PATH_PROG([JAR], [jar], [:])
if test "x${JAR}" = "x:"; then
    AC_MSG_ERROR([jar not found])
fi

AC_PATH_PROG([MVN], [mvn], [:])
if test "x${MVN}" = "x:"; then
    AC_MSG_ERROR([mvn not found])
fi

AC_ARG_VAR([SPADE_ROOT], [Path to SPADE root directory])
if test "x${SPADE_ROOT}" = "x"; then
    AC_MSG_ERROR([SPADE_ROOT is required; set it with ./configure SPADE_ROOT=<path>])
fi

AC_ARG_VAR([JAVA_DEBUG], [Build Java with Maven debug output (set to 1 or true)])
MAVEN_DEBUG_FLAGS=
if test "x${JAVA_DEBUG}" = "x1" || test "x${JAVA_DEBUG}" = "xtrue"; then
    MAVEN_DEBUG_FLAGS="-Dmaven.compiler.debug=true -Dmaven.compiler.debuglevel=lines,vars,source"
fi
AC_SUBST([MAVEN_DEBUG_FLAGS])

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
