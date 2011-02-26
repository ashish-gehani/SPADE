# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2011 SRI International.

# Last stable Subversion revision.
REVISION = 240

# Download the external libraries that SPADE uses.
svn-libraries:
	svn checkout -r $(REVISION) --force http://data-provenance.googlecode.com/svn/trunk/SPADE/lib/

# Create the SPADE source directories.
skeleton:
	svn checkout -r $(REVISION) --force --depth empty http://data-provenance.googlecode.com/svn/trunk/SPADE/src/;\
	svn update -r $(REVISION) --force --set-depth immediates src/spade/

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

# Build the Neo4j storage.
build-neo4j:
	cd src;\
	javac -cp '.:../lib/*' spade/storage/Neo4j.java

# Build the Pipe reporter.
build-pipe:
	cd src;\
	javac -cp '.:../lib/*' spade/reporter/Pipe.java

# Run the SPADE kernel.
start-spade:
	cd src;\
	java -cp '.:../lib/*' spade/core/Kernel

# Remove all .class files.
clean-class:
	find src -name '*.class' -exec rm \{\} \;

# Remove everything except the Makefile.
clean:
	rm -rf lib src
