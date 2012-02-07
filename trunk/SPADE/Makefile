# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2011 SRI International.

# Last stable Subversion revision.
REVISION = 385

# System and architecture-specific configuration
OS_NAME = $(shell uname)
OS_ARCH = $(shell uname -m)
ifeq ($(OS_NAME), Darwin)
	OS_BUILD = build-openbsm build-macfuse
	OS_CONFIG_PATH = /usr/local/lib/pkgconfig
endif
ifeq ($(OS_NAME), Linux)
	OS_BUILD = build-linuxaudit build-linuxfuse
	OS_CONFIG_PATH = /usr/lib/pkgconfig
endif

.PHONY: build

all:
	@echo 'Usage:'
	@echo '      make download     - to download the code'
	@echo '      make build        - to compile the code'
	@echo '      make control      - to start the control client'
	@echo '      make query        - to start the query client'
	@echo ' '
	@echo '      make clean        - to remove the compiled code'
	@echo '      make clean-all    - to remove everything except the makefile'
	@echo ' '

# ---------- Downloading SPADE ----------

# Download everything.
download:
	svn export -r $(REVISION) --force --depth infinity http://data-provenance.googlecode.com/svn/trunk/SPADE/src/
	svn export -r $(REVISION) --force --depth infinity http://data-provenance.googlecode.com/svn/trunk/SPADE/lib/
	svn export -r $(REVISION) --force --depth infinity http://data-provenance.googlecode.com/svn/trunk/SPADE/bin/
	svn export -r $(REVISION) --force --depth infinity http://data-provenance.googlecode.com/svn/trunk/SPADE/cfg/
	svn export -r $(REVISION) --force --depth infinity http://data-provenance.googlecode.com/svn/trunk/SPADE/log/
	svn export -r $(REVISION) --force http://data-provenance.googlecode.com/svn/trunk/SPADE/run

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

# Start the control client.
control:
	@java -cp 'build:lib/*' spade.client.ControlClient

# Start the query client.
query:
	@java -cp 'build:lib/*' spade.client.QueryClient

# ---------- Removing files ----------

# Remove Java classes and native libraries, executables that we compiled.

clean:
	@echo 'Removing Java classes, native libraries, executables...'
	@rm -rf build
	@rm -rf src/spade/reporter/*.h lib/libLinuxFUSE.* lib/libMacFUSE.*

# Remove everything except the Makefile.

clean-all:
	@echo 'Removing everything except Makefile...'
	@rm -rf build lib src cfg log bin run

