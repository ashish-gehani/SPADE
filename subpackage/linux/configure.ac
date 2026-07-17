# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.71])
AC_INIT([spadeLinux], [2.0])
AC_CONFIG_AUX_DIR([../../build-aux])
AC_CONFIG_MACRO_DIRS([../../m4])
AM_INIT_AUTOMAKE([1.16 -Wall -Werror foreign])

AC_PATH_PROG([PKG_CONFIG], [pkg-config], [:])

# --- fuse detection ---

AC_ARG_ENABLE([fuse],
    [AS_HELP_STRING([--enable-fuse], [build Linux FUSE reporter (default: no)])],
    [enable_fuse=${enableval}],
    [enable_fuse=no])
have_fuse=no
if test "x${enable_fuse}" = "xyes"; then
    if test "x${PKG_CONFIG}" != "x:"; then
        if ${PKG_CONFIG} --exists fuse 2>/dev/null; then
            have_fuse=yes
        else
            AC_MSG_ERROR([fuse not found via pkg-config])
        fi
    else
        AC_MSG_ERROR([pkg-config not found; it is required to locate fuse])
    fi
fi
AM_CONDITIONAL([HAVE_FUSE], [test "x${have_fuse}" = "xyes"])

# --- llvm detection ---

AC_ARG_ENABLE([llvm],
    [AS_HELP_STRING([--enable-llvm], [build Linux LLVM reporters (default: no)])],
    [enable_llvm=${enableval}],
    [enable_llvm=no])
have_llvm=no
if test "x${enable_llvm}" = "xyes"; then
    AC_PATH_PROG([LLVM_CONFIG], [llvm-config], [:])
    if test "x${LLVM_CONFIG}" != "x:"; then
        have_llvm=yes
    else
        AC_MSG_ERROR([llvm-config not found])
    fi
fi
AM_CONDITIONAL([HAVE_LLVM], [test "x${have_llvm}" = "xyes"])

# --- kernel_module-specific variables ---

AC_ARG_ENABLE([kernel-modules],
    [AS_HELP_STRING([--disable-kernel-modules], [skip Linux kernel modules build (enabled by default)])],
    [enable_kernel_modules=${enableval}],
    [enable_kernel_modules=yes])
AM_CONDITIONAL([CONFIGURE_KERNEL_MODULES], [test "x${enable_kernel_modules}" = "xyes"])

# ---

AC_CONFIG_SUBDIRS([audit_bridge])

if test "x${have_llvm}" = "xyes"; then
    AC_CONFIG_SUBDIRS([llvm])
fi
if test "x${have_fuse}" = "xyes"; then
    AC_CONFIG_SUBDIRS([fuse])
fi
if test "x${enable_kernel_modules}" = "xyes"; then
    AC_CONFIG_SUBDIRS([kernel_module])
fi

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
