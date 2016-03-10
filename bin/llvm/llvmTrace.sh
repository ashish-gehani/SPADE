#!/bin/bash
REPLIB_OSFLAG=-D_LLVMREPORTER_LINUX
LLVM_SOURCE=$1
FUNCTION_FILE=$2
LLVM_TARGET=$3
BASE="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
SRC_PATH=$BASE/../../src
LLC=llc
CC=gcc

LD_FLAGS=""
if [[ $* == *-instrument-libc* ]]
then
  echo "### Wrapping libc calls" 
  LD_FLAGS="$(opt -load $BASE/LibcWrapper.so -wrapper ${LLVM_SOURCE}.bc -o ${LLVM_Instrumented}.bc)"
  echo $LD_FLAGS
else
  cp ${LLVM_SOURCE}.bc ${LLVM_Instrumented}.bc 
fi

### 
llvm-link ${LLVM_Instrumented}.bc $BASE/flush.bc -o $BASE/linked.bc

if [ "$FUNCTION_FILE" != "-monitor-all" ]; then
	opt -dot-callgraph $BASE/linked.bc -o $BASE/callgraph.bc	
	java -cp $BASE/../../build  spade/utility/FunctionMonitor $BASE/callgraph.dot ${FUNCTION_FILE} functionsOut
	opt -load $BASE/LLVMTrace.so -provenance -FunctionNames-input functionsOut $BASE/linked.bc -o ${LLVM_TARGET}.bc 
else
	opt -load $BASE/LLVMTrace.so -provenance -FunctionNames-input "-monitor-all" $BASE/linked.bc -o ${LLVM_TARGET}.bc 
fi
###


$LLC -relocation-model=pic ${LLVM_TARGET}.bc -o ${LLVM_TARGET}.s
$CC -static ${REPLIB_OSFLAG} ${SRC_PATH}/spade/reporter/llvm/llvmBridge.c -c -o ${SRC_PATH}/spade/reporter/llvm/llvmBridge.o 
$CC -fPIC ${SRC_PATH}/spade/reporter/llvm/llvmClose.c -c -o ${SRC_PATH}/spade/reporter/llvm/llvmClose.o 
$CC ${LLVM_TARGET}.s -c -o ${LLVM_TARGET}.o
$CC ${LLVM_TARGET}.o ${SRC_PATH}/spade/reporter/llvm/llvmClose.o -shared -o ${LLVM_TARGET}.so $LD_FLAGS  
$CC ${LLVM_TARGET}.so ${SRC_PATH}/spade/reporter/llvm/llvmBridge.o -o ${LLVM_TARGET} -Wl,-R -Wl,./ -lcrypt -lm 
