# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2011 SRI International.

# Last stable Subversion revision.
REVISION = 369

# System and architecture-specific configuration
OS_NAME = $(shell uname)
OS_ARCH = $(shell uname -m)
ifeq ($(OS_NAME), Darwin)
	OS_BUILD = build-openbsm build-macfuse
	OS_CONFIG_PATH = /usr/local/lib/pkgconfig
	ifeq ($(OS_ARCH), i386)
		JSW_BIN = cp -f jsw/bin/wrapper-macosx-universal-32 jsw/wrapper
		JSW_LIB = cp -f lib/jsw/libwrapper-macosx-universal-32.jnilib lib/libwrapper.jnilib
	else
		JSW_BIN = cp -f jsw/bin/wrapper-macosx-universal-64 jsw/wrapper
		JSW_LIB = cp -f lib/jsw/libwrapper-macosx-universal-64.jnilib lib/libwrapper.jnilib
	endif
endif
ifeq ($(OS_NAME), Linux)
	OS_BUILD = build-linuxaudit build-linuxfuse
	OS_CONFIG_PATH = /usr/lib/pkgconfig
	ifeq ($(OS_ARCH), i386)
		JSW_BIN = cp -f jsw/bin/wrapper-linux-x86-32 jsw/wrapper
		JSW_LIB = cp -f lib/jsw/libwrapper-linux-x86-32.so lib/libwrapper.so
	else
		JSW_BIN = cp -f jsw/bin/wrapper-linux-x86-64 jsw/wrapper
		JSW_LIB = cp -f lib/jsw/libwrapper-linux-x86-64.so lib/libwrapper.so
	endif
endif

.PHONY: build

all:
	@echo 'Usage:'
	@echo '      make download     - to download the code'
	@echo '      make build        - to compile the code'

# ---------- Downloading SPADE ----------

# Download everything.
download:
	svn checkout -r $(REVISION) --force --depth infinity http://data-provenance.googlecode.com/svn/trunk/SPADE/src/
	svn checkout -r $(REVISION) --force --depth infinity http://data-provenance.googlecode.com/svn/trunk/SPADE/lib/
	svn checkout -r $(REVISION) --force --depth infinity http://data-provenance.googlecode.com/svn/trunk/SPADE/jsw/
	svn checkout -r $(REVISION) --force --depth empty http://data-provenance.googlecode.com/svn/trunk/SPADE/cfg/
	svn checkout -r $(REVISION) --force --depth empty http://data-provenance.googlecode.com/svn/trunk/SPADE/log/

# ---------- Building SPADE ----------

# Build the SPADE kernel.
build:
	@mkdir -p build;
	@echo 'Building the SPADE Kernel...'
	javac -cp 'build:lib/*' -sourcepath src -d build src/spade/core/*.java
	@echo 'Building clients...'
	javac -cp 'build:lib/*' -sourcepath src -d build src/spade/client/*.java
	@echo 'Building utilities...'
	javac -cp 'build:lib/*' -sourcepath src -d build src/spade/utility/*.java
	@echo 'Building storage...'
	javac -cp 'build:lib/*' -sourcepath src -d build src/spade/storage/*.java
	@echo 'Building the Pipe reporter...'
	javac -cp 'build:lib/*' -sourcepath src -d build src/spade/reporter/Pipe.java
	@echo 'Building the Lsof reporter...'
	javac -cp 'build:lib/*' -sourcepath src -d build src/spade/reporter/Lsof.java
	@make $(OS_BUILD)
	@echo 'Copying wrapper binary and libraries...'
	$(shell $(JSW_BIN))
	$(shell $(JSW_LIB))
	$(shell chmod +x jsw/wrapper)

# Build the LinuxAudit reporter.
build-linuxaudit:
	@echo 'Building the LinuxAudit reporter...'
	javac -cp 'build:lib/*' -sourcepath src -d build src/spade/reporter/LinuxAudit.java
	javac -cp 'build:lib/*' -sourcepath src -d build src/spade/reporter/LinuxAuditDump.java
	gcc -o build/spade/reporter/spadeLinuxAudit src/spade/reporter/spadeLinuxAudit.c

# Build the LinuxFUSE reporter.
build-linuxfuse:
	@echo 'Building the LinuxFUSE reporter...'
	javac -cp 'build:lib/*' -sourcepath src -d build src/spade/reporter/LinuxFUSE.java
	javah -classpath 'build:lib/*' -o src/spade/reporter/libLinuxFUSE.h spade.reporter.LinuxFUSE
	export PKG_CONFIG_PATH=$(OS_CONFIG_PATH)
	gcc -shared -Wl,-soname,libLinuxFUSE.so -I$(shell java -classpath build spade.utility.JavaHome)/../include -I$(shell java -classpath build spade.utility.JavaHome)/../include/linux -Wall `pkg-config fuse --cflags --libs` src/spade/reporter/libLinuxFUSE.c -o lib/libLinuxFUSE.so

# Build the OpenBSM reporter.
build-openbsm:
	@echo 'Building the OpenBSM reporter...'
	javac -cp 'build:lib/*' -sourcepath src -d build src/spade/reporter/OpenBSM.java
	gcc -o build/spade/reporter/spadeOpenBSM -lbsm src/spade/reporter/spadeOpenBSM.c

# Build the MacFUSE reporter.
build-macfuse:
	@echo 'Building the MacFUSE reporter...'
	javac -cp 'build:lib/*' -sourcepath src -d build src/spade/reporter/MacFUSE.java
	javah -classpath 'build:lib/*' -o src/spade/reporter/libMacFUSE.h spade.reporter.MacFUSE
	gcc -dynamiclib -I/System/Library/Frameworks/JavaVM.framework/Headers -D__FreeBSD__=10 -D_FILE_OFFSET_BITS=64 -I/usr/local/include/fuse -Wall -g -D__DARWIN_64_BIT_INO_T=1 -o lib/libMacFUSE.jnilib src/spade/reporter/libMacFUSE.c -lfuse_ino64

# ---------- Running SPADE ----------

# Run the SPADE kernel.
kernel:
	@echo 'Starting the SPADE Kernel...'
	@java -server -Xmx512M -cp 'build:lib/*' -Djava.library.path=lib spade.core.Kernel &

# Run the control shell.
control-shell:
	@java -cp 'build:lib/*' spade.client.ControlClient

# Run the query shell.
query-shell:
	@java -cp 'build:lib/*' spade.client.QueryClient

# ---------- Removing files ----------

# Remove Java classes and native libraries, executables that we compiled.

clean:
	@echo 'Removing Java classes, native libraries, executables...'
	@rm -rf build
	@rm -rf src/spade/reporter/spadeLinuxAudit stc/spade/reporter/spadeOpenBSM lib/libLinuxFUSE.* lib/libMacFUSE.* lib/libwrapper.* jsw/wrapper

# Remove everything except the Makefile.

clean-all:
	@echo 'Removing everything except Makefile...'
	@rm -rf build lib src cfg log jsw

