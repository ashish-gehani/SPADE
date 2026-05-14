# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.73])
AC_INIT([spadeLinuxLLVM], [1.0])
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

AC_ARG_VAR([SPADE_ROOT], [Path to SPADE root directory])
if test "x${SPADE_ROOT}" = "x"; then
    AC_MSG_ERROR([SPADE_ROOT is required; set it with ./configure SPADE_ROOT=<path>])
fi
SPADE_SRC_DIR="${SPADE_ROOT}/src"
AC_SUBST([SPADE_SRC_DIR])

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
