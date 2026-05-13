# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.73])
AC_INIT([spadeLinuxKernelModule], [1.0])
AC_CONFIG_AUX_DIR([build-aux])
AM_INIT_AUTOMAKE([1.18 -Wall -Werror foreign])

AC_ARG_VAR([SPADE_SRC_DIR], [Path to SPADE source directory containing spade/reporter/audit/kernel-modules])
if test "x${SPADE_SRC_DIR}" = "x"; then
    AC_MSG_ERROR([SPADE_SRC_DIR is required; set it with ./configure SPADE_SRC_DIR=<path>])
fi

AC_ARG_VAR([KERNEL_MODULES_DEBUG], [Set to "true" to build debug kernel modules (default: false)])
if test "x${KERNEL_MODULES_DEBUG}" = "xtrue"; then
    KERNEL_MODULE_TARGET=debug
else
    KERNEL_MODULE_TARGET=release
fi
AC_SUBST([KERNEL_MODULE_TARGET])

AC_ARG_VAR([KDIR], [Kernel build directory (default: /lib/modules/$(uname -r)/build)])
AC_ARG_VAR([MOD_DEFINES], [Additional preprocessor defines passed to the kernel module build])

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
