# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.73])
AC_INIT([spadeMacLLVM], [1.0])
AC_CONFIG_AUX_DIR([../build-aux])
AC_CONFIG_MACRO_DIRS([../m4])
AM_INIT_AUTOMAKE([1.18 -Wall -Werror foreign])

AC_PATH_PROG([LLVM_CONFIG], [llvm-config], [:])
if test "x${LLVM_CONFIG}" = "x:"; then
    AC_MSG_ERROR([llvm-config not found; it is required])
fi

LLVM_BINDIR=$(${LLVM_CONFIG} --bindir)

AC_PATH_PROG([CLANG], [clang], [:], [${LLVM_BINDIR}${PATH_SEPARATOR}${PATH}])
if test "x${CLANG}" = "x:"; then
    AC_MSG_ERROR([clang not found])
fi

AC_PATH_PROG([CLANGXX], [clang++], [:], [${LLVM_BINDIR}${PATH_SEPARATOR}${PATH}])
if test "x${CLANGXX}" = "x:"; then
    AC_MSG_ERROR([clang++ not found])
fi

LLVM_CXXFLAGS=$(${LLVM_CONFIG} --cxxflags)
AC_SUBST([LLVM_CXXFLAGS])

AC_ARG_VAR([SPADE_SRC_DIR], [Path to SPADE source directory containing spade/reporter/llvmTracer.cpp, llvmBridge.c, and llvmClose.c])
if test "x${SPADE_SRC_DIR}" = "x"; then
    AC_MSG_ERROR([SPADE_SRC_DIR is required; set it with ./configure SPADE_SRC_DIR=<path>])
fi

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
