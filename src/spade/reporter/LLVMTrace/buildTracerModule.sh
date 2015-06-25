INCLUDE_PATH=/usr/include/llvm-3.5/llvm

echo llvm[0]: "Compiling llvmTracer.cpp for Release+Asserts build" "(PIC)"

clang++ -I$INCLUDE_PATH -I./  -D_DEBUG -D_GNU_SOURCE -D__STDC_CONSTANT_MACROS -D__STDC_FORMAT_MACROS -D__STDC_LIMIT_MACROS -O3 -fomit-frame-pointer -std=c++11 -fvisibility-inlines-hidden -fno-exceptions -fno-rtti -fPIC -ffunction-sections -fdata-sections -Wcast-qual    -pedantic -Wno-long-long -Wall -W -Wno-unused-parameter -Wwrite-strings  -Wcovered-switch-default -Wno-uninitialized  -Wno-missing-field-initializers -Wno-comment -c -MMD -MP -MF "llvmTracer.d.tmp" -MT "llvmTracer.o" -MT "llvmTracer.d" llvmTracer.cpp -o llvmTracer.o ; \

echo llvm[0]: Linking Release+Asserts "Loadable Module" \
	  LLVMTrace.so
clang++  -O3 -Wl,-R -Wl,'$ORIGIN' -Wl,--gc-sections -rdynamic -L./ -L./  -shared -o LLVMTrace.so llvmTracer.o \
	   -lz -lpthread -ltinfo -ldl -lm 

mv LLVMTrace.so ../../../../bin/llvm

clang -emit-llvm -c flushModule.c -o flush.bc
mv flush.bc ../../../../bin/llvm
