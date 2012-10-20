# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2012 SRI International.
# 
# This file requires autoconf, libtool, autoconf-archive installed 

AC_INIT([spade], [1.0], [spade@csl.sri.com])
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
if test "`$JAVAC -version 2>&1 | grep '1.6'`" = ""; then
  if test "`$JAVAC -version 2>&1 | grep '1.7'`" = ""; then
    AC_MSG_ERROR([The java compiler version must be 1.6 or 1.7])
  fi
fi
if test "`$JAVAC -version 2>&1 | grep 'gcj'`" != ""; then
   AC_MSG_ERROR([OpenJDK Java Compiler is known to have compilation issues. Please use Oracle's JDK for compilation from http://www.oracle.com/technetwork/java/javase/downloads/index.html])
fi

AC_CHECK_PROGS([JAVAH], [javah], [:])
if test "$JAVAH" = :; then
   AC_MSG_ERROR([This package needs javah installed and available in your path])
fi

AC_PATH_PROG(ANDROID_SDK_TOOLS, adb )

DUSER=spade
AC_SUBST([DUSER])

AC_CONFIG_FILES([Makefile])
AC_OUTPUT

