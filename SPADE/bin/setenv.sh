#!/bin/bash
# -----------------------------------------------------------------------------
# Set java exe and conf file for all scripts
#
# -----------------------------------------------------------------------------

# resolve links - $0 may be a softlink

current=`pwd`
cd `dirname "$0"`
PRGDIR=`pwd`
cd "$current"

wrapper_home="$PRGDIR"/..
export wrapper_home

wrapper_jar="$wrapper_home"/wrapper.jar
export wrapper_jar

wrapper_app_jar="$wrapper_home"/wrapperApp.jar
export wrapper_app_jar

wrapper_java_sys_options=-Djna_tmpdir="$wrapper_home"/tmp
export wrapper_java_sys_options

wrapper_java_options=-Xmx30m
export wrapper_java_options

java_exe=java
export java_exe

# show java version
"$java_exe" -version

conf_file="$wrapper_home"/conf/wrapper.conf
export conf_file

conf_default_file="$wrapper_home"/conf/wrapper.conf.default
export conf_default_file



