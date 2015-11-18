#!/bin/bash
REPLIB_OSFLAG=-D_LLVMREPORTER_LINUX
LLVM_SOURCE=$1
FUNCTION_FILE=$2
LLVM_TARGET=$3
SRC_PATH=../../src
LLC=llc
CC=gcc

cp ${LLVM_SOURCE}.bc ${LLVM_SOURCE}2.bc 

LD_FLAGS=""
if [[ $* == *-instrument-libc* ]]
then
  echo "### Wrapping libc calls" 
  LD_FLAGS="$(opt -load ./LibcWrapper.so -wrapper ${LLVM_SOURCE}.bc -o ${LLVM_TARGET}.bc)"
  echo $LD_FLAGS
  mv ${LLVM_TARGET}.bc ${LLVM_SOURCE}2.bc
fi

bash instrumentBitcode.sh ${LLVM_SOURCE}2.bc ${FUNCTION_FILE} ${LLVM_TARGET}.bc

$LLC -relocation-model=pic ${LLVM_TARGET}.bc -o ${LLVM_TARGET}.s
$CC -static ${REPLIB_OSFLAG} ${SRC_PATH}/spade/reporter/llvm/llvmBridge.c -c -o ${SRC_PATH}/spade/reporter/llvm/llvmBridge.o 
$CC -fPIC ${SRC_PATH}/spade/reporter/llvm/llvmClose.c -c -o ${SRC_PATH}/spade/reporter/llvm/llvmClose.o 
$CC ${LLVM_TARGET}.s -c -o ${LLVM_TARGET}.o
$CC ${LLVM_TARGET}.o ${SRC_PATH}/spade/reporter/llvm/llvmClose.o -shared -o ${LLVM_TARGET}.so $LD_FLAGS  
$CC ${LLVM_TARGET}.so ${SRC_PATH}/spade/reporter/llvm/llvmBridge.o -o ${LLVM_TARGET} -Wl,-R -Wl,./ -lcrypt 
