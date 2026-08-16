# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.71])
AC_INIT([spadeLinuxLLVM], [2.0])
AC_CONFIG_AUX_DIR([../../../build-aux])
AC_CONFIG_MACRO_DIRS([../../../m4])
AM_INIT_AUTOMAKE([1.16 -Wall -Werror foreign])
AM_MAINTAINER_MODE([disable])

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

###

AC_ARG_VAR([SPADE_PACKAGE_LIB_DIR], [Path to SPADE top-level (i.e. package) lib directory])
if test "x${SPADE_PACKAGE_LIB_DIR}" = "x"; then
    AC_MSG_ERROR([SPADE_PACKAGE_LIB_DIR is required; set it with ./configure SPADE_PACKAGE_LIB_DIR=<path>])
fi

AC_ARG_VAR([SPADE_PACKAGE_LINUX_LLVM_TRACE_LIB_NAME], [Name of SPADE top-level (i.e. package) Linux llvm trace shared library])
if test "x${SPADE_PACKAGE_LINUX_LLVM_TRACE_LIB_NAME}" = "x"; then
    AC_MSG_ERROR([SPADE_PACKAGE_LINUX_LLVM_TRACE_LIB_NAME is required; set it with ./configure SPADE_PACKAGE_LINUX_LLVM_TRACE_LIB_NAME=<name>])
fi

AC_ARG_VAR([SPADE_PACKAGE_LLVM_FLUSH_BC_NAME], [Name of SPADE top-level (i.e. package) llvm bitcode object])
if test "x${SPADE_PACKAGE_LLVM_FLUSH_BC_NAME}" = "x"; then
    AC_MSG_ERROR([SPADE_PACKAGE_LLVM_FLUSH_BC_NAME is required; set it with ./configure SPADE_PACKAGE_LLVM_FLUSH_BC_NAME=<name>])
fi

AC_ARG_VAR([SPADE_PACKAGE_LLVM_LIBC_WRAPPER_LIB_NAME], [Name of SPADE top-level (i.e. package) llvm libc wrapper library])
if test "x${SPADE_PACKAGE_LLVM_LIBC_WRAPPER_LIB_NAME}" = "x"; then
    AC_MSG_ERROR([SPADE_PACKAGE_LLVM_LIBC_WRAPPER_LIB_NAME is required; set it with ./configure SPADE_PACKAGE_LLVM_LIBC_WRAPPER_LIB_NAME=<name>])
fi

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
