# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2012 SRI International.
# 
# This file requires autoconf, libtool, autoconf-archive installed 

AC_INIT([amhello], [1.0], [ashish.gehani@csl.sri.com])
AC_PREREQ([2.65])
AC_PROG_CC

AC_CHECK_PROGS([JAVA], java, [:])
if test "$JAVA" = :; then
   AC_MSG_ERROR([This package needs java installed and available in your path])
fi
AC_CHECK_PROGS([JAVAC], [javac], [:])
if test "$JAVAC" = :; then
   AC_MSG_ERROR([This package needs java compiler installed and available in your path])
fi
AC_CHECK_PROGS([JAVAH], [javah], [:])
if test "$JAVAH" = :; then
   AC_MSG_ERROR([This package needs javah installed and available in your path])
fi

AC_PATH_PROG(ANDROID_SDK_TOOLS, adb )

AC_CONFIG_FILES([Makefile])
AC_OUTPUT

