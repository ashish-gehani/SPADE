CXX_FLAGS = $(shell llvm-config --cxxflags)
C_FLAGS   = $(shell llvm-config --cflags)

OS        =  $(shell uname)

CLANG     = $(shell which clang)

TARGET = nweb

ifeq (Darwin, $(findstring Darwin, ${OS}))
REPLIB_OSFLAG  =  -D_LLVMREPORTER_MACOSX 
include Makefile.macosx
endif

ifeq (Linux, $(findstring Linux, ${OS}))
REPLIB_OSFLAG  =  -D_LLVMREPORTER_LINUX
include Makefile.linux
endif


EXES = foo nweb gettid test

clean:
	rm -rf *.a *.so *.bc *.ll *.s *.log a.out *.dylib *.o *.dSYM *.d *.d.tmp ${TARGET} ${EXES}


