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

# Buld the SPADE kernel.
# NOTE: storage-source (and transitively opm-source) are dependencies 
#       because of Lineage.exportDOT(), which is temporarily present 
#       for querying as a stopgap till the REST API is implemented,
#       at which point the method and dependencies should be removed.
kernel-build:	kernel-source opm-source storage-source libraries
	cd src;\
	javac -cp '.:../lib/*' spade/core/Kernel.java;

# Start the SPADE kernel.
run:
	cd src;\
	java -cp '.:../lib/*' spade/core/Kernel

# Remove all .class files.
clean-class:
	find src -name '*.class' -exec rm \{\} \;

clean:
	rm -rf lib src
