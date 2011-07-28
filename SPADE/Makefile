# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2011 SRI International.

# Last stable Subversion revision.
REVISION = 348

# 'pkg-config' configuration
LINUX_PKG_CONFIG_PATH = /usr/lib/pkgconfig
MAC_PKG_CONFIG_PATH = /usr/local/lib/pkgconfig

# Platform must be specified
all:
	@echo 'Usage:'
	@echo '      make download     - to download the code.'
	@echo '      make <platform>   - to compile the code.'
	@echo '           (platform = linux | mac | windows)'

# ---------- Downloading SPADE ----------

# Download everything.
download:	svn-kernel svn-utility svn-client svn-pipe svn-openbsm svn-macfuse svn-linux-audit svn-linux-fuse svn-lsof

# Download SPADE's core and its dependencies.
svn-kernel:	skeleton svn-libraries svn-core svn-opm svn-storage

# Download the external libraries that SPADE uses.
svn-libraries:
	svn checkout -r $(REVISION) --force http://data-provenance.googlecode.com/svn/trunk/SPADE/lib/

# Create the SPADE source directories.
skeleton:
	svn checkout -r $(REVISION) --force --depth empty http://data-provenance.googlecode.com/svn/trunk/SPADE/src/;\
	svn checkout -r $(REVISION) --force --depth empty http://data-provenance.googlecode.com/svn/trunk/SPADE/dev/;\
	svn checkout -r $(REVISION) --force --depth empty http://data-provenance.googlecode.com/svn/trunk/SPADE/cfg/;\
	svn checkout -r $(REVISION) --force --depth empty http://data-provenance.googlecode.com/svn/trunk/SPADE/log/;\
	svn update -r $(REVISION) --force --set-depth immediates src/spade/

# Download the utilities.
svn-utility:
	svn update -r $(REVISION) --force --set-depth infinity src/spade/utility/

# Download the clients.
svn-client:
	svn update -r $(REVISION) --force --set-depth infinity src/spade/client/

# Download the SPADE kernel.
svn-core:
	svn update -r $(REVISION) --force --set-depth infinity src/spade/core/

# Download SPADE's OPM implementation.
svn-opm:
	svn update -r $(REVISION) --force --set-depth infinity src/spade/opm/

# Download the storage systems.
svn-storage:
	svn update -r $(REVISION) --force --set-depth infinity src/spade/storage/

# Download the Pipe reporter.
svn-pipe:
	svn update -r $(REVISION) --force src/spade/reporter/Pipe.java

# Download the LinuxAudit reporter.
svn-linux-audit:
	svn update -r $(REVISION) --force src/spade/reporter/LinuxAudit.java
	svn update -r $(REVISION) --force src/spade/reporter/spadeLinuxAudit.c

# Download the LinuxFUSE reporter.
svn-linux-fuse:
	svn update -r $(REVISION) --force src/spade/reporter/LinuxFUSE.java
	svn update -r $(REVISION) --force src/spade/reporter/libLinuxFUSE.c

# Download the OpenBSM reporter.
svn-openbsm:
	svn update -r $(REVISION) --force src/spade/reporter/OpenBSM.java
	svn update -r $(REVISION) --force src/spade/reporter/spadeOpenBSM.c

# Download the MacFUSE reporter.
svn-macfuse:
	svn update -r $(REVISION) --force src/spade/reporter/MacFUSE.java
	svn update -r $(REVISION) --force src/spade/reporter/libMacFUSE.c

# Download the Lsof reporter.
svn-lsof:
	svn update -r $(REVISION) --force src/spade/reporter/Lsof.java

# ---------- Building SPADE ----------

# Build everything.
build-common:	build-core build-client build-utility build-neo4j

# Build the SPADE kernel.
build-core:
	cd src;\
	javac -cp '.:../lib/*' spade/core/*.java;

# Build the clients.
build-client:
	cd src;\
	javac -cp '.:../lib/*' spade/client/*.java;

# Build the utilities.
build-utility:
	cd src;\
	javac -cp '.:../lib/*' spade/utility/*.java;

# Build the Neo4j storage.
build-neo4j:
	cd src;\
	javac -cp '.:../lib/*' spade/storage/Neo4j.java;

# Build the Pipe reporter.
build-pipe:
	cd src;\
	javac -cp '.:../lib/*' spade/reporter/Pipe.java;

# Build the LinuxAudit reporter.
build-linux-audit:
	cd src;\
	javac -cp '.:../lib/*' spade/reporter/LinuxAudit.java;\
	gcc -o spade/reporter/spadeLinuxAudit spade/reporter/spadeLinuxAudit.c;

# Build the LinuxFUSE reporter.
build-linux-fuse:
	cd src;\
	javac -cp '.:../lib/*' spade/reporter/LinuxFUSE.java;\
	javah -o spade/reporter/libLinuxFUSE.h spade.reporter.LinuxFUSE;\
	export PKG_CONFIG_PATH=$(LINUX_PKG_CONFIG_PATH);\
	gcc -shared -Wl,-soname,libLinuxFUSE.so -I$(shell cd src;java spade/utility/JavaHome)/../include -I$(shell cd src;java spade/utility/JavaHome)/../include/linux -Wall `pkg-config fuse --cflags --libs` spade/reporter/libLinuxFUSE.c -o ../lib/libLinuxFUSE.so;

# Build the OpenBSM reporter.
build-openbsm:
	cd src;\
	javac -cp '.:../lib/*' spade/reporter/OpenBSM.java;\
	gcc -o spade/reporter/spadeOpenBSM -lbsm spade/reporter/spadeOpenBSM.c;

# Build the MacFUSE reporter.
build-macfuse:
	cd src;\
	javac -cp '.:../lib/*' spade/reporter/MacFUSE.java;\
	javah -o spade/reporter/libMacFUSE.h spade.reporter.MacFUSE;\
	gcc -dynamiclib -I/System/Library/Frameworks/JavaVM.framework/Headers -D__FreeBSD__=10 -D_FILE_OFFSET_BITS=64 -I/usr/local/include/fuse -Wall -g -D__DARWIN_64_BIT_INO_T=1 -o ../lib/libMacFUSE.jnilib spade/reporter/libMacFUSE.c -lfuse_ino64;

# Build the Lsof reporter.
build-lsof:
	cd src;\
	javac -cp '.:../lib/*' spade/reporter/Lsof.java;

# Build SPADE for Linux.
linux:	build-common build-pipe build-lsof build-linux-audit build-linux-fuse

# Build SPADE for Mac OS X.
mac:	build-common build-pipe build-lsof build-openbsm build-macfuse

# Build SPADE for Windows.
windows:	build-common

# ---------- Running SPADE ----------

# Run the SPADE kernel.
kernel:
	@echo 'SPADE 2.0 Kernel'
	@cd src;\
	java -server -Xmx512M -cp '.:../lib/*' -Djava.library.path=../lib/ spade/core/Kernel;

# Run the control shell.
control-shell:
	@cd src;\
	java -cp '.:../lib/*' spade/client/ControlClient ../dev/controlPipeIn ../dev/controlPipeOut;

# Run the query shell.
query-shell:
	@cd src;\
	java -cp '.:../lib/*' spade/client/QueryClient ../dev/queryPipeIn ../dev/queryPipeOut;

# ---------- Removing files ----------

# Remove Java classes and native libraries, executables that we compiled.

clean:
	find src -name '*.class' -exec rm \{\} \;
	rm -f spade/reporter/spadeLinuxAudit spade/reporter/spadeOpenBSM lib/libLinuxFUSE.so lib/libMacFUSE.jnilib \;

# Remove everything except the Makefile.

download-clean:
	rm -rf lib src dev cfg;

