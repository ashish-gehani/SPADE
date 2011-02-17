# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2011 SRI International.

# Download the external libraries that SPADE uses.
libraries:
	svn checkout http://data-provenance.googlecode.com/svn/trunk/SPADE/lib/

# Create the SPADE source directories.
skeleton-source:
	svn checkout --depth empty http://data-provenance.googlecode.com/svn/trunk/SPADE/src/;\
	svn update --set-depth immediates src/spade/

# Download the SPADE kernel.
kernel-source:	skeleton-source
	svn update --set-depth infinity src/spade/core/

# Download SPADE's OPM implementation.
opm-source:	skeleton-source
	svn update --set-depth infinity src/spade/opm/

# Download the storage systems.
storage-source:	skeleton-source
	svn update --set-depth infinity src/spade/storage/

# Download the Pipe producer.
pipe-source:	skeleton-source
	svn update --set-depth infinity src/spade/producer/PipeProducer.java

# Buld the SPADE kernel.
# NOTE: storage-source is a dependency because of Lineage.exportDOT(),
#       which is temporarily present for querying as a stopgap till the
#       REST API is implemented, at which point the method and dependency
#       should be removed.
kernel-build:	kernel-source storage-source libraries
	cd src;\
	javac -cp '.:../lib/*' spade/core/Kernel.java;
	touch .kernel_built

# Build the SPADE OPM code.
opm-build:	opm-source kernel-build
	cd src;\
	javac -cp '.:../lib/*' spade/opm/vertex/*.java spade/opm/edge/*.java

# Build the storage systems.
storage-build:	storage-source kernel-build
	cd src;\
	javac -cp '.:../lib/*' spade/storage/*.java

# Build the Pipe producer.
pipe-build:	pipe-source opm-build
	cd src;\
	javac -cp '.:../lib/*' spade/producer/PipeProducer.java

# Build the Pipe producer and Graphviz storage for the tutorial.
tutorial:	pipe-build storage-build	

# Start the SPADE kernel.
run:	.kernel_built
	cd src;\
	java -cp '.:../lib/*' spade/core/Kernel

# Remove all .class files.
clean-class:
	cd $(SPADE_DIR);\
	find src -name '*.class' -exec rm \{\} \;

clean:
	rm -rf lib src
