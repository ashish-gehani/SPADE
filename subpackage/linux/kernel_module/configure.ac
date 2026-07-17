# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# The kernel version/arch/distro/headers checks below are warnings, not
# fatal errors, because this subpackage is always configured regardless of
# whether the host actually supports kernel modules. Whether the build is
# actually attempted is controlled at make time via KERNEL_MODULES=true.

AC_PREREQ([2.71])
AC_INIT([spadeLinuxKernelModule], [2.0])
AC_CONFIG_AUX_DIR([../../../build-aux])
AC_CONFIG_MACRO_DIRS([../../../m4])
AM_INIT_AUTOMAKE([1.16 -Wall -Werror foreign])
AM_MAINTAINER_MODE([disable])

AC_ARG_VAR([SPADE_JAVA_SRC_DIR], [Path to SPADE src directory])
if test "x${SPADE_JAVA_SRC_DIR}" = "x"; then
    AC_MSG_ERROR([SPADE_JAVA_SRC_DIR is required; set it with ./configure SPADE_JAVA_SRC_DIR=<path>])
fi

AC_ARG_VAR([SPADE_KERNEL_MODULE_LIB_DIR], [Path to SPADE kernel module lib directory])
if test "x${SPADE_KERNEL_MODULE_LIB_DIR}" = "x"; then
    AC_MSG_ERROR([SPADE_KERNEL_MODULE_LIB_DIR is required; set it with ./configure SPADE_KERNEL_MODULE_LIB_DIR=<path>])
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

kernel_ver=`uname -r`
kernel_major=`echo "${kernel_ver}" | cut -d. -f1`
kernel_minor=`echo "${kernel_ver}" | cut -d. -f2`
kver_in_range=no
if test "${kernel_major}" -eq 5 2>/dev/null && test "${kernel_minor}" -ge 4 2>/dev/null; then
    kver_in_range=yes
elif test "${kernel_major}" -eq 6 2>/dev/null && test "${kernel_minor}" -le 17 2>/dev/null; then
    kver_in_range=yes
fi
if test "x${kver_in_range}" = "xno"; then
    AC_MSG_WARN([kernel ${kernel_major}.${kernel_minor} is outside supported range @<:@5.4, 6.17@:>@])
fi

arch=`uname -m`
if test "x${arch}" != "xx86_64"; then
    AC_MSG_WARN([architecture ${arch} is not supported; x86_64 is required])
fi

distro=`grep '^ID=' /etc/os-release 2>/dev/null | cut -d= -f2 | tr -d '"'`
if test "x${distro}" != "xubuntu"; then
    AC_MSG_WARN([OS distribution ${distro} is not supported; ubuntu is required])
fi

if test "x${KDIR}" != "x"; then
    kheaders_path="${KDIR}"
else
    kheaders_path="/lib/modules/${kernel_ver}/build"
fi
kheaders_available=no
if test -d "${kheaders_path}"; then
    kheaders_available=yes
else
    AC_MSG_WARN([kernel headers not found at ${kheaders_path}])
fi
KHEADERS_PATH="${kheaders_path}"
AC_SUBST([KHEADERS_PATH])

# Special case: Control kernel module build at make time.
KERNEL_MODULES="${KERNEL_MODULES:-false}"
AC_SUBST([KERNEL_MODULES])

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
