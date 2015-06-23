#!/bin/bash
REPLIB_OSFLAG=-D_LLVMREPORTER_LINUX
LLVM_SOURCE=$1
FUNCTION_FILE=$2
LLVM_TARGET=$3
SRC_PATH=../src

gcc -static ${REPLIB_OSFLAG} ${SRC_PATH}/spade/reporter/llvmBridge.c -c -o ${SRC_PATH}/spade/reporter/llvmBridge.o

bash instrumentBitcode.sh ${LLVM_SOURCE}.bc ${FUNCTION_FILE} ${LLVM_TARGET}.bc
llc -relocation-model=pic ${LLVM_TARGET}.bc -o ${LLVM_TARGET}.s

gcc -fPIC ${SRC_PATH}/spade/reporter/llvmClose.c -c -o ${SRC_PATH}/spade/reporter/llvmClose.o
gcc ${LLVM_TARGET}.s -c -o ${LLVM_TARGET}.o
gcc ${LLVM_TARGET}.o ${SRC_PATH}/spade/reporter/llvmClose.o -shared -o ${LLVM_TARGET}.so 
gcc ${LLVM_TARGET}.so ${SRC_PATH}/spade/reporter/llvmBridge.o -o ${LLVM_TARGET} -Wl,-R -Wl,./ -lcrypt

