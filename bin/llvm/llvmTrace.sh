#!/bin/bash
REPLIB_OSFLAG=-D_LLVMREPORTER_LINUX
LLVM_SOURCE=$1
FUNCTION_FILE=$2
LLVM_TARGET=$3
SRC_PATH=../../src
LLC=llc-3.6
CC=gcc

$CC -static ${REPLIB_OSFLAG} ${SRC_PATH}/spade/reporter/llvmBridge.c -c -o ${SRC_PATH}/spade/reporter/llvmBridge.o 
bash instrumentBitcode.sh ${LLVM_SOURCE}.bc ${FUNCTION_FILE} ${LLVM_TARGET}.bc
$LLC -relocation-model=pic ${LLVM_TARGET}.bc -o ${LLVM_TARGET}.s

$CC -fPIC ${SRC_PATH}/spade/reporter/llvmClose.c -c -o ${SRC_PATH}/spade/reporter/llvmClose.o 
$CC ${LLVM_TARGET}.s -c -o ${LLVM_TARGET}.o
$CC ${LLVM_TARGET}.o ${SRC_PATH}/spade/reporter/llvmClose.o -shared -o ${LLVM_TARGET}.so 
$CC ${LLVM_TARGET}.so ${SRC_PATH}/spade/reporter/llvmBridge.o -o ${LLVM_TARGET} -Wl,-R -Wl,./ -lcrypt 

