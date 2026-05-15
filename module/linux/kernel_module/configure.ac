# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.71])
AC_INIT([spadeLinuxKernelModule], [1.0])
AC_CONFIG_AUX_DIR([../../../build-aux])
AC_CONFIG_MACRO_DIRS([../../../m4])
AM_INIT_AUTOMAKE([1.16 -Wall -Werror foreign])

AC_ARG_VAR([SPADE_ROOT], [Path to SPADE root directory])
if test "x${SPADE_ROOT}" = "x"; then
    AC_MSG_ERROR([SPADE_ROOT is required; set it with ./configure SPADE_ROOT=<path>])
fi
SPADE_SRC_DIR="${SPADE_ROOT}/src"
AC_SUBST([SPADE_SRC_DIR])

AC_ARG_VAR([KERNEL_MODULES_DEBUG], [Set to "true" to build debug kernel modules (default: false)])
if test "x${KERNEL_MODULES_DEBUG}" = "xtrue"; then
    KERNEL_MODULE_TARGET=debug
else
    KERNEL_MODULE_TARGET=release
fi
AC_SUBST([KERNEL_MODULE_TARGET])

AC_ARG_VAR([KDIR], [Kernel build directory (default: /lib/modules/$(uname -r)/build)])
AC_ARG_VAR([MOD_DEFINES], [Additional preprocessor defines passed to the kernel module build])
AC_ARG_VAR([KERNEL_MODULE_BUILD_DIR], [Directory for kernel module build artifacts (default: <builddir>/build)])
if test "x${KERNEL_MODULE_BUILD_DIR}" = "x"; then
    KERNEL_MODULE_BUILD_DIR="${ac_pwd}/build"
fi
AC_SUBST([KERNEL_MODULE_BUILD_DIR])

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
AC_SUBST([kernel_ver])

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
AC_SUBST([kheaders_path])

kernel_module_supported=no
if test "x${kver_in_range}" = "xyes" && test "x${arch}" = "xx86_64" && test "x${kheaders_available}" = "xyes" && test "x${distro}" = "xubuntu"; then
    kernel_module_supported=yes
fi
AM_CONDITIONAL([KERNEL_MODULE_SUPPORTED], [test "x${kernel_module_supported}" = "xyes"])

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
