# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2012 SRI International.
# 
# This file requires autoconf, libtool, autoconf-archive installed 

AC_INIT([spade], [2.0], [spade@csl.sri.com])
AC_PREREQ([2.65])
AC_PROG_CC

AC_CHECK_PROGS([JAVA], java, [:])
if test "$JAVA" = :; then
   AC_MSG_ERROR([This package needs java installed and available in your path])
fi
AC_CHECK_PROGS([JAVAC], [javac], [:])
if test "$JAVAC" = :; then
   AC_MSG_ERROR([This package needs JDK installed and available in your path. You can get it from http://www.oracle.com/technetwork/java/javase/downloads/index.html])
fi
if test "`$JAVAC -version 2>&1 | grep '1.8\|1.9\|10.0'`" = ""; then
  AC_MSG_ERROR([The java compiler version must be 1.8 or 1.9 or 10.0])
fi

AC_CHECK_PROGS([JAR], [jar], [:])
if test "$JAR" = :; then
   AC_MSG_ERROR([This package needs jar utility installed and available in your path])
fi

AC_CHECK_PROGS([ADB], [adb], [:])
if test "$ADB" != :; then
   ADB_PATH=`which adb`
   ANDROID_SDK_TOOLS=`dirname $ADB_PATH`/
else
   ANDROID_SDK_TOOLS=""
fi
AC_SUBST([ANDROID_SDK_TOOLS])

AC_CHECK_PROGS([DX], [dx], [:])
if test "$DX" != :; then
   DX_PATH=`which dx`
   ANDROID_BUILD_TOOLS=`dirname $DX_PATH`/
else
   ANDROID_BUILD_TOOLS=""
fi
AC_SUBST([ANDROID_BUILD_TOOLS])


DUSER=spade
AC_SUBST([DUSER])

AC_CONFIG_FILES([Makefile])
AC_OUTPUT

