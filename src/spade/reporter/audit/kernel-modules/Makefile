# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2015 SRI International.

MODULES_SRC = src/spade/reporter/audit/kernel-modules
MODULES_BIN = lib/kernel-modules
FIND_CMD_SRC_FILES_FLAGS = ! -name 'README' ! -name 'Makefile' ! -name 'netio.c' ! -name 'netio_controller.c' ! -name 'globals.h'

obj-m += netio.o netio_controller.o

ccflags-y := -DRETPOLINE

all:
	@rm -rf $(MODULES_BIN)
	@mkdir -p $(MODULES_BIN)
	make -C /lib/modules/$(shell uname -r)/build M=$(PWD)/src/spade/reporter/audit/kernel-modules modules
	@find $(MODULES_SRC) -mindepth 1 -prune $(FIND_CMD_SRC_FILES_FLAGS) -exec mv -t $(MODULES_BIN) {} \;

clean:
	@find $(MODULES_SRC) -mindepth 1 -prune $(FIND_CMD_SRC_FILES_FLAGS) -exec rm -rf {} \;
	@rm -rf $(MODULES_BIN)
