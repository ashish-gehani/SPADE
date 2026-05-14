# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.73])
AC_INIT([spadeModule], [1.0])
AC_CONFIG_AUX_DIR([build-aux])
AC_CONFIG_MACRO_DIRS([m4])
AM_INIT_AUTOMAKE([1.18 -Wall -Werror foreign])

AC_CANONICAL_HOST

AC_ARG_VAR([SPADE_ROOT], [Path to SPADE root directory (default: current directory)])
if test "x${SPADE_ROOT}" = "x"; then
    SPADE_ROOT=`pwd`
fi
export SPADE_ROOT

case "${host_os}" in
    darwin*)
        platform=mac
        ;;
    linux*)
        platform=linux
        ;;
    *)
        AC_MSG_ERROR([Unsupported platform: ${host_os}])
        ;;
esac

AM_CONDITIONAL([PLATFORM_MAC],   [test "x${platform}" = "xmac"])
AM_CONDITIONAL([PLATFORM_LINUX], [test "x${platform}" = "xlinux"])

AC_CONFIG_SUBDIRS([module/java])

if test "x${platform}" = "xmac"; then
    AC_CONFIG_SUBDIRS([module/mac])
elif test "x${platform}" = "xlinux"; then
    AC_CONFIG_SUBDIRS([module/linux])
fi

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
