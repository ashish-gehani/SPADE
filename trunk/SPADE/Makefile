# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2011 SRI International.

# Last stable Subversion revision.
REVISION = 295

# Download and build everything.
all:	svn-spade svn-pipe svn-utility svn-client build-spade build-neo4j build-pipe build-utility build-client

# Download everything.
svn:	svn-spade svn-pipe svn-utility svn-client

# Build everything.
build:	build-spade build-neo4j build-pipe build-utility build-client

# Download the external libraries that SPADE uses.
svn-libraries:
	svn checkout -r $(REVISION) --force http://data-provenance.googlecode.com/svn/trunk/SPADE/lib/

# Create the SPADE source directories.
skeleton:
	svn checkout -r $(REVISION) --force --depth empty http://data-provenance.googlecode.com/svn/trunk/SPADE/src/;\
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

# Download the OpenBSM reporter.
svn-openbsm:
	svn update -r $(REVISION) --force src/spade/reporter/OpenBSM.java
	svn update -r $(REVISION) --force src/spade/reporter/spade_reporter_OpenBSM.c

# Download the LinuxAudit reporter.
svn-linuxaudit:
	svn update -r $(REVISION) --force src/spade/reporter/LinuxAudit.java
	svn update -r $(REVISION) --force src/spade/reporter/spade_reporter_LinuxAudit.c

# Download the LinuxFUSE reporter.
svn-linuxfuse:
	svn update -r $(REVISION) --force src/spade/reporter/LinuxFUSE.java
	svn update -r $(REVISION) --force src/spade/reporter/spade_reporter_LinuxFUSE.c

# Download the MacFUSE reporter.
svn-macfuse:
	svn update -r $(REVISION) --force src/spade/reporter/MacFUSE.java
	svn update -r $(REVISION) --force src/spade/reporter/spade_reporter_MacFUSE.c

# Download the Lsof reporter.
svn-lsof:
	svn update -r $(REVISION) --force src/spade/reporter/Lsof.java

# NOTE: svn-storage (and transitively svn-opm) are dependencies below
#       because of Lineage.exportDOT(), which is temporarily present
#       for querying as a stopgap till the REST API is implemented,
#       at which point the method and dependencies should be removed.

# Download SPADE's core and its dependencies.
svn-spade:	svn-libraries skeleton svn-core svn-opm svn-storage

# Build the SPADE kernel.
build-spade:
	cd src;\
	javac -cp '.:../lib/*' spade/core/*.java;

# Build the utilities.
build-utility:
	cd src;\
	javac -cp '.:../lib/*' spade/utility/*.java;

# Build the clients.
build-client:
	cd src;\
	javac -cp '.:../lib/*' spade/client/*.java;

# Build the Neo4j storage.
build-neo4j:
	cd src;\
	javac -cp '.:../lib/*' spade/storage/Neo4j.java;

# Build the Pipe reporter.
build-pipe:
	cd src;\
	javac -cp '.:../lib/*' spade/reporter/Pipe.java;

# Build the OpenBSM reporter.
build-openbsm:
	cd src;\
	javac -cp '.:../lib/*' spade/reporter/OpenBSM.java;\
	gcc -o spade/reporter/spade_reporter_OpenBSM -lbsm spade/reporter/spade_reporter_OpenBSM.c;

# Build the LinuxAudit reporter.
build-linuxaudit:
	cd src;\
	javac -cp '.:../lib/*' spade/reporter/LinuxAudit.java;\
	gcc -o spade/reporter/spade_reporter_LinuxAudit spade/reporter/spade_reporter_LinuxAudit.c;

# Build the LinuxFUSE reporter.
build-linuxfuse:
	cd src;\
	javac -cp '.:../lib/*' spade/reporter/LinuxFUSE.java;\
	javah -o spade/reporter/spade_reporter_LinuxFUSE.h spade.reporter.LinuxFUSE;\
	gcc -shared -Wl,-soname,libspadeLinuxFUSE.so -I$(shell cd src;\ java spade/utility/JavaHome)/../include -I$(shell cd src;\ java spade/utility/JavaHome)/../include/linux -L. -Wall `pkg-config fuse --cflags --libs` spade/reporter/spade_reporter_LinuxFUSE.c -o libspadeLinuxFUSE.so;

# Build the MacFUSE reporter.
build-macfuse:
	cd src;\
	javac -cp '.:../lib/*' spade/reporter/MacFUSE.java;\
	javah -o spade/reporter/spade_reporter_MacFUSE.h spade.reporter.MacFUSE;\
	gcc -dynamiclib -D__FreeBSD__=10 -D_FILE_OFFSET_BITS=64 -DFUSE_USE_VERSION=26 -I/usr/local/include/fuse -I/System/Library/Frameworks/JavaVM.framework/Headers -Wall -g -lfuse spade/reporter/spade_reporter_MacFUSE.c -o libspadeMacFUSE.jnilib;

# Build the Lsof reporter.
build-lsof:
	cd src;\
	javac -cp '.:../lib/*' spade/reporter/Lsof.java;

# Run the SPADE kernel.
start-spade:
	cd src;\
	java -cp '.:../lib/*' -Djava.library.path=. spade/core/Kernel;

# Run the control shell.
start-control-client:
	cd src;\
	java -cp '.:../lib/*' spade/client/ControlClient controlPipeIn controlPipeOut;

# Run the query shell.
start-query-client:
	cd src;\
	java -cp '.:../lib/*' spade/client/QueryClient queryPipeIn queryPipeOut;

# Remove all .class files.
clean-class:
	find src -name '*.class' -exec rm \{\} \;

# Remove everything except the Makefile.
clean:
	rm -rf lib src;

