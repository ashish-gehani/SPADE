# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.71])
AC_INIT([spade], [2.0])
AC_CONFIG_AUX_DIR([build-aux])
AC_CONFIG_MACRO_DIRS([m4])
AM_INIT_AUTOMAKE([1.16 -Wall -Werror foreign])

# Needed to determine the current host type.
AC_CANONICAL_HOST

# Standard SPADE locations. Needed by subpackages.
# Vars needed by subpackages are prefixed with 'SPADE_' for clarity.
SPADE_ROOT=`pwd`
SPADE_ANDROID_BUILD_DIR="${SPADE_ROOT}/build"
SPADE_ANDROID_LIB_DIR="${SPADE_ROOT}/lib"
SPADE_AUDIT_BRIDGE_BIN_DIR="${SPADE_ROOT}/bin"
SPADE_FUSE_LIB_DIR="${SPADE_ROOT}/lib"
SPADE_JAVA_BUILD_DIR="${SPADE_ROOT}/build"
SPADE_JAVA_BUILD_NATIVE_INCLUDE_DIR="${SPADE_JAVA_BUILD_DIR}/native/include"
SPADE_JAVA_LIB_DIR="${SPADE_ROOT}/lib"
SPADE_JAVA_LOCAL_REPOSITORY_URL="file://${SPADE_JAVA_LIB_DIR}"
SPADE_JAVA_SRC_DIR="${SPADE_ROOT}/src"
SPADE_JAVA_SRC_RESOURCE_DIR="${SPADE_JAVA_SRC_DIR}/resources"
SPADE_KERNEL_MODULE_LIB_DIR="${SPADE_ROOT}/lib/kernel-modules"
SPADE_LLVM_LIB_DIR="${SPADE_ROOT}/lib"
SPADE_OPENBSM_BIN_DIR="${SPADE_ROOT}/bin"

# Substituted so it's available in this package's own Makefile.
AC_SUBST([SPADE_ROOT])

# Exported to make them available to subpackages.
export SPADE_ROOT
export SPADE_ANDROID_BUILD_DIR
export SPADE_ANDROID_LIB_DIR
export SPADE_AUDIT_BRIDGE_BIN_DIR
export SPADE_FUSE_LIB_DIR
export SPADE_JAVA_BUILD_DIR
export SPADE_JAVA_BUILD_NATIVE_INCLUDE_DIR
export SPADE_JAVA_LIB_DIR
export SPADE_JAVA_LOCAL_REPOSITORY_URL
export SPADE_JAVA_SRC_DIR
export SPADE_JAVA_SRC_RESOURCE_DIR
export SPADE_KERNEL_MODULE_LIB_DIR
export SPADE_LLVM_LIB_DIR
export SPADE_OPENBSM_BIN_DIR

case "${host_os}" in
    darwin*)
        spade_host_os=mac
        ;;
    linux*)
        spade_host_os=linux
        ;;
    *)
        AC_MSG_ERROR([Unsupported host OS: ${host_os}])
        ;;
esac

AM_CONDITIONAL([HOST_OS_MAC],   [test "x${spade_host_os}" = "xmac"])
AM_CONDITIONAL([HOST_OS_LINUX], [test "x${spade_host_os}" = "xlinux"])

# Always configure java subpackage.
AC_CONFIG_SUBDIRS([subpackage/java])

# Platform specific subpackages.
if test "x${spade_host_os}" = "xmac"; then
    AC_CONFIG_SUBDIRS([subpackage/mac])
elif test "x${spade_host_os}" = "xlinux"; then
    AC_CONFIG_SUBDIRS([subpackage/linux])
fi

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
