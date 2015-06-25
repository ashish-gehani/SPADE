llvm-link $1 flush.bc -o linked.bc
if [$2 != "-no-monitor"]; then
	opt -dot-callgraph linked.bc -o callgraph.bc	
	java -cp ../build  spade/filter/FunctionMonitor callgraph.dot $2 functionsOut
	opt -load ./LLVMTrace.so -provenance -FunctionNames-input functionsOut linked.bc -o $3 
else
	opt -load ./LLVMTrace.so -provenance -FunctionNames-input "-no-monitor" linked.bc -o $3 
fi

