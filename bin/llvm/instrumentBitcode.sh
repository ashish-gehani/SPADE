#!/bin/bash
BASE="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
llvm-link $1 $BASE/flush.bc -o $BASE/linked.bc
functionFile="$2"

if [ "$functionFile" != "-no-monitor" ]; then
	opt -dot-callgraph $BASE/linked.bc -o $BASE/callgraph.bc	
	java -cp $BASE/../../build  spade/utility/FunctionMonitor $BASE/callgraph.dot $2 functionsOut
	opt -load $BASE/LLVMTrace.so -provenance -FunctionNames-input functionsOut $BASE/linked.bc -o $3 
else
	opt -load $BASE/LLVMTrace.so -provenance -FunctionNames-input "-no-monitor" $BASE/linked.bc -o $3 
fi


