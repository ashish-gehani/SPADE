CXX_FLAGS = `llvm-config --cxxflags`

CLANG = /usr/local/bin/clang
CLANG++ = /usr/local/bin/clang++

CLANG_FLAGS = -cc1 -triple x86_64-apple-macosx10.6.8 -emit-obj -mrelax-all -disable-free  -D _DEBUG -D _GNU_SOURCE -D __STDC_CONSTANT_MACROS -D __STDC_FORMAT_MACROS -D __STDC_LIMIT_MACROS -I /usr/local/include 

CLANG++_FLAGS = ${CXX_FLAGS} -m64 -Wl,-flat_namespace -Wl,-undefined,suppress  -dynamiclib -mmacosx-version-min=10.6

TARGET = foo

all:
	 ${CLANG} -g GetLLVMSocket.c -c -o GetLLVMSocket.o
	 ${CLANG} -c -emit-llvm ${TARGET}.c -o ${TARGET}.bc
	 ${CLANG} ${CLANG_FLAGS} -g  -o LLVMReporter.o -x c++ LLVMReporter.cpp
	 ${CLANG++} ${CLANG++_FLAGS} -o LLVMReporter.dylib LLVMReporter.o
	 opt -load LLVMReporter.dylib  -provenance ${TARGET}.bc -o ${TARGET}.bc
	 llc ${TARGET}.bc -o ${TARGET}.s
	 ${CLANG} -g ${TARGET}.s GetLLVMSocket.o -o ${TARGET}

hello:
	${CLANG}  ${CLANG_FLAGS}  -o Hello.o -x c++ Hello.cpp
	${CLANG++} ${CLANG++_FLAGS} -o Hello.dylib Hello.o

clean:
	rm -rf *.so *.bc *.s a.out *.dylib *.o *.dSYM *.d *.d.tmp ${TARGET}


