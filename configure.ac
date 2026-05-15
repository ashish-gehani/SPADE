# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.73])
AC_INIT([spade], [1.0])
AC_CONFIG_AUX_DIR([build-aux])
AC_CONFIG_MACRO_DIRS([m4])
AM_INIT_AUTOMAKE([1.18 -Wall -Werror foreign])

AC_CANONICAL_HOST

AC_ARG_VAR([SPADE_ROOT], [Path to SPADE root directory (default: current directory)])
if test "x${SPADE_ROOT}" = "x"; then
    SPADE_ROOT=`pwd`
fi
export SPADE_ROOT

# Default install prefix to SPADE_ROOT so artifacts land in the project tree.
if test "x${prefix}" = "xNONE"; then
    prefix="${SPADE_ROOT}"
fi

JAVA_BUILD_DIR="${SPADE_ROOT}/lib"
ANDROID_BUILD_DIR="${SPADE_ROOT}/lib"
AUDIT_BRIDGE_BUILD_DIR="${SPADE_ROOT}/bin"
FUSE_BUILD_DIR="${SPADE_ROOT}/lib"
KERNEL_MODULE_BUILD_DIR="${SPADE_ROOT}/lib/kernel-modules"
LLVM_BUILD_DIR="${SPADE_ROOT}/lib"
OPENBSM_BUILD_DIR="${SPADE_ROOT}/bin"
export JAVA_BUILD_DIR ANDROID_BUILD_DIR AUDIT_BRIDGE_BUILD_DIR FUSE_BUILD_DIR KERNEL_MODULE_BUILD_DIR LLVM_BUILD_DIR OPENBSM_BUILD_DIR

AC_ARG_VAR([JAVAC_HEADER_GEN_OPTIONS], [Options passed to javac for JNI header generation (default: -Xlint:none -proc:none)])
if test "x${JAVAC_HEADER_GEN_OPTIONS}" = "x"; then
    JAVAC_HEADER_GEN_OPTIONS="-Xlint:none -proc:none"
fi

# Build the Java classpath and append it to JAVAC_HEADER_GEN_OPTIONS via an argfile.
AC_MSG_CHECKING([Java classpath])
java_cp=`"${SPADE_ROOT}/bin/classpath.sh"`
if test "x${java_cp}" = "x"; then
    AC_MSG_ERROR([failed to build Java classpath; ensure mvn is available and dependencies are resolved])
fi
AC_MSG_RESULT([ok])
printf -- '-cp %s\n' "${java_cp}" > "${SPADE_ROOT}/build.java-cp.argfile"
JAVAC_HEADER_GEN_OPTIONS="${JAVAC_HEADER_GEN_OPTIONS} @${SPADE_ROOT}/build.java-cp.argfile"
export JAVAC_HEADER_GEN_OPTIONS

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

AC_ARG_VAR([KERNEL_MODULES], [Set to "true" to enable building Linux kernel modules])
if test "x${KERNEL_MODULES}" = "xtrue"; then
    ac_configure_args="$ac_configure_args --enable-kernel-modules"
fi

AC_CONFIG_SUBDIRS([module/java])

if test "x${platform}" = "xmac"; then
    AC_CONFIG_SUBDIRS([module/mac])
elif test "x${platform}" = "xlinux"; then
    AC_CONFIG_SUBDIRS([module/linux])
fi

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
