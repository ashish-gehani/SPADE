# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

AC_PREREQ([2.71])
AC_INIT([spade], [2.0])
AC_CONFIG_AUX_DIR([build-aux])
AC_CONFIG_MACRO_DIRS([m4])
AM_INIT_AUTOMAKE([1.16 -Wall -Werror foreign])
AM_MAINTAINER_MODE([disable])

# Needed to determine the current host type.
AC_CANONICAL_HOST

# Standard SPADE locations. Needed by packages.
# Vars needed by packages are prefixed with 'SPADE_' for clarity.
SPADE_ROOT=`pwd`

SPADE_PACKAGE_JAR_NAME="spade.jar"
SPADE_PACKAGE_ANDROID_JAR_NAME="android-spade.jar"
SPADE_PACKAGE_AUDIT_BRIDGE_BIN_NAME="spadeAuditBridge"
SPADE_PACKAGE_OPENBSM_BRIDGE_BIN_NAME="spadeOpenBSM"
SPADE_PACKAGE_LINUX_FUSE_SHARED_LIB_NAME="libLinuxFUSE.so"
SPADE_PACKAGE_MAC_FUSE_SHARED_LIB_NAME="libMacFUSE.jnilib"
SPADE_PACKAGE_KERNEL_MODULE_DIR_NAME="kernel-modules"
SPADE_PACKAGE_LINUX_LLVM_TRACE_LIB_NAME="LLVMTrace.so"
SPADE_PACKAGE_MAC_LLVM_TRACE_LIB_NAME="llvmTracer.dylib"
SPADE_PACKAGE_LLVM_FLUSH_BC_NAME="flush.bc"
SPADE_PACKAGE_LLVM_LIBC_WRAPPER_LIB_NAME="LibcWrapper.so"

# SPADE package level directory for generated artifacts (libraries, jars, and etc.)
SPADE_PACKAGE_LIB_DIR="${SPADE_ROOT}/lib"
SPADE_PACKAGE_SPADE_JAR_FILE="${SPADE_PACKAGE_LIB_DIR}/${SPADE_PACKAGE_JAR_NAME}"

# SPADE package level include directories for generated headers
SPADE_PACKAGE_HEADER_INCLUDE_DIR="${SPADE_ROOT}/include"
SPADE_PACKAGE_HEADER_INCLUDE_JAVA_DIR="${SPADE_PACKAGE_HEADER_INCLUDE_DIR}/java"

SPADE_PACKAGE_BIN_DIR="${SPADE_ROOT}/bin"

# Substituted so it's available in this package's own Makefile.
AC_SUBST([SPADE_ROOT])

# Exported to make them available to packages.
export SPADE_ROOT

export SPADE_PACKAGE_LIB_DIR
export SPADE_PACKAGE_SPADE_JAR_FILE
export SPADE_PACKAGE_HEADER_INCLUDE_DIR
export SPADE_PACKAGE_HEADER_INCLUDE_JAVA_DIR
export SPADE_PACKAGE_BIN_DIR

export SPADE_PACKAGE_JAR_NAME
export SPADE_PACKAGE_ANDROID_JAR_NAME
export SPADE_PACKAGE_AUDIT_BRIDGE_BIN_NAME
export SPADE_PACKAGE_OPENBSM_BRIDGE_BIN_NAME
export SPADE_PACKAGE_LINUX_FUSE_SHARED_LIB_NAME
export SPADE_PACKAGE_MAC_FUSE_SHARED_LIB_NAME
export SPADE_PACKAGE_KERNEL_MODULE_DIR_NAME
export SPADE_PACKAGE_LINUX_LLVM_TRACE_LIB_NAME
export SPADE_PACKAGE_MAC_LLVM_TRACE_LIB_NAME
export SPADE_PACKAGE_LLVM_FLUSH_BC_NAME
export SPADE_PACKAGE_LLVM_LIBC_WRAPPER_LIB_NAME

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

# Always configure java package.
AC_CONFIG_SUBDIRS([pkg/java])

# Platform specific packages.
if test "x${spade_host_os}" = "xmac"; then
    AC_CONFIG_SUBDIRS([pkg/mac])
elif test "x${spade_host_os}" = "xlinux"; then
    AC_CONFIG_SUBDIRS([pkg/linux])
fi

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
