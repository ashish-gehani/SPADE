# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.73])
AC_INIT([spadeModule], [1.0])
AC_CONFIG_AUX_DIR([build-aux])
AC_CONFIG_MACRO_DIRS([m4])
AM_INIT_AUTOMAKE([1.18 -Wall -Werror foreign])

AC_CANONICAL_HOST

AC_ARG_VAR([SPADE_SRC_DIR], [Path to SPADE source directory])

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

if test "x${platform}" = "xmac"; then
    AC_CONFIG_SUBDIRS([mac])
elif test "x${platform}" = "xlinux"; then
    AC_CONFIG_SUBDIRS([linux])
fi

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
