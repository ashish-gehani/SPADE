#!/bin/bash
#
#  --------------------------------------------------------------------------------
#  SPADE - Support for Provenance Auditing in Distributed Environments.
#  Copyright (C) 2021 SRI International

#  This program is free software: you can redistribute it and/or
#  modify it under the terms of the GNU General Public License as
#  published by the Free Software Foundation, either version 3 of the
#  License, or (at your option) any later version.

#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
#  General Public License for more details.

#  You should have received a copy of the GNU General Public License
#  along with this program. If not, see <http://www.gnu.org/licenses/>.
#  --------------------------------------------------------------------------------

LLVM_INSTALL_DIR=$1

if [ -z "${LLVM_INSTALL_DIR}" ]
then
	echo "Must specify the LLVM installation dir path as the first argument"
	exit 1
fi

if [ ! -d "${LLVM_INSTALL_DIR}" ]
then
	echo "LLVM installation not found at '${LLVM_INSTALL_DIR}'"
	exit 1
fi

#export LLVM_DIR=/usr/lib/llvm-11

export LLVM_DIR="${LLVM_INSTALL_DIR}"

build_dir="build/llvm-add-metadata"
lib_dir="lib"
shared_lib="libAddMetadata.so"

if [ ! -d "${lib_dir}" ]
then
	echo "Please run this script from SPADE home directory. Lib directory '${lib_dir}' not found."
	exit 1
fi

mkdir -p "${build_dir}"

if [ ! -d "${build_dir}" ]
then
	echo "Failed to create build directory '${build_dir}'"
	exit 1
fi

spade_dir=`pwd`

cd "${build_dir}" && \
cmake -DLT_LLVM_INSTALL_DIR="${LLVM_INSTALL_DIR}" "${spade_dir}/src/spade/reporter/llvm/AddMetadata" && \
make

cd "${spade_dir}"

if [ ! -f "${build_dir}/${shared_lib}" ]
then
	echo "AddMetadata shared library not found at path '${build_dir}/${shared_lib}'"
	rm -r "${build_dir}"
	exit 1
fi

mv "${build_dir}/${shared_lib}" "${lib_dir}" && echo "AddMetadata shared library: '${lib_dir}/${shared_lib}'"

rm -r "${build_dir}"
