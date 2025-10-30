KDIR ?= /lib/modules/$(shell uname -r)/build

BUILD_DIR ?= build
# make MOD_DEFINES="-DDEBUG"
MOD_DEFINES ?=
# Install dir
INSTALL_DIR ?= ../../../../../lib/kernel-modules

# Variables for prerequisite checking
OS_KERNEL_RELEASE ?= $(shell uname -r)
OS_KERNEL_MAJOR ?= $(shell uname -r | cut -d '.' -f 1)
OS_KERNEL_MINOR ?= $(shell uname -r | cut -d '.' -f 2 | xargs printf "%02d")
OS_KERNEL_NUMBER ?= $(OS_KERNEL_MAJOR)$(OS_KERNEL_MINOR)
MIN_OS_KERNEL_NUMBER ?= 504
MIN_OS_KERNEL_NUMBER_PRINTABLE ?= 5.4
MAX_OS_KERNEL_NUMBER ?= 608
MAX_OS_KERNEL_NUMBER_PRINTABLE ?= 6.8
OS_ARCH ?= $(shell uname -m)

# Prerequisite status variables (true/false)
IS_MICROSOFT_KERNEL := $(if $(findstring Microsoft,$(OS_KERNEL_RELEASE)),true,false)
HAS_KERNEL_NUMBER := $(if $(OS_KERNEL_NUMBER),true,false)
IS_KERNEL_VERSION_GTE_MIN := $(shell [ -n "$(OS_KERNEL_NUMBER)" ] && [ $(OS_KERNEL_NUMBER) -ge $(MIN_OS_KERNEL_NUMBER) ] && echo true || echo false)
IS_KERNEL_VERSION_LTE_MAX := $(shell [ -n "$(OS_KERNEL_NUMBER)" ] && [ $(OS_KERNEL_NUMBER) -le $(MAX_OS_KERNEL_NUMBER) ] && echo true || echo false)
IS_KERNEL_VERSION_OK := $(shell [ "$(IS_KERNEL_VERSION_GTE_MIN)" = "true" ] && [ "$(IS_KERNEL_VERSION_LTE_MAX)" = "true" ] && echo true || echo false)
IS_X86_64 := $(if $(filter x86_64,$(OS_ARCH)),true,false)

###

GEN_BUILD_HASH_FILE_NAME := build_hash
GEN_BUILD_HASH_SRC_FILE := spade/$(GEN_BUILD_HASH_FILE_NAME).c
GEN_BUILD_HASH_OBJ_FILE := spade/$(GEN_BUILD_HASH_FILE_NAME).o
GEN_BUILD_HASH_TEMPLATE_FILE := spade/$(GEN_BUILD_HASH_FILE_NAME).template
GEN_BUILD_HASH_CACHE_FILE := spade/.build_hash_cache

# Use M variable if set (kbuild phase), otherwise use current directory
BUILD_HASH_DIR := $(if $(M),$(M),$(CURDIR))
GEN_BUILD_HASH := $(shell \
  find $(BUILD_HASH_DIR)/spade -type f \( -name '*.c' -o -name '*.h' \) \
    ! -name '$(GEN_BUILD_HASH_FILE_NAME).c' -print0 \
  | sort -z \
  | xargs -0 sha256sum | sha256sum | cut -d' ' -f1)
###

# Main module name
MAIN_MODULE_NAME = netio
CONTROLLER_MODULE_NAME = netio_controller
TEST_MODULE_NAME = spade_audit_test

# Script name for make check
CHECK_SCRIPT_MOD_TEST=bin/test/$(TEST_MODULE_NAME)/run.sh
CHECK_SCRIPT_MOD_MAIN=bin/test/$(MAIN_MODULE_NAME)/run.sh

# Module names
MODULE_NAMES := $(MAIN_MODULE_NAME) $(CONTROLLER_MODULE_NAME) $(TEST_MODULE_NAME)

# Function names
# FUNCTION_NAMES := accept accept4 bind clone connect fork kill recvfrom recvmsg sendmsg sendto setns unshare vfork
HOOKED_FUNCTION_NAMES := sys_accept
HOOKED_FUNCTION_OBJS = \
  $(foreach f_name,$(HOOKED_FUNCTION_NAMES),spade/audit/kernel/function/$(f_name)/action/audit.o) \
  $(foreach f_name,$(HOOKED_FUNCTION_NAMES),spade/audit/kernel/function/$(f_name)/action.o) \
  $(foreach f_name,$(HOOKED_FUNCTION_NAMES),spade/audit/kernel/function/$(f_name)/hook.o) \
  $(foreach f_name,$(HOOKED_FUNCTION_NAMES),spade/audit/kernel/function/$(f_name)/op.o)

# Module output files
MOD_OUT_FILES = \
  Module.symvers \
  modules.order \
  $(foreach mod,$(MODULE_NAMES),.$(mod).ko.cmd) \
  $(foreach mod,$(MODULE_NAMES),.$(mod).mod.cmd) \
  $(foreach mod,$(MODULE_NAMES),.$(mod).mod.o.cmd) \
  $(foreach mod,$(MODULE_NAMES),.$(mod).o.cmd) \
  $(foreach mod,$(MODULE_NAMES),$(mod).ko) \
  $(foreach mod,$(MODULE_NAMES),$(mod).mod) \
  $(foreach mod,$(MODULE_NAMES),$(mod).mod.c) \
  $(foreach mod,$(MODULE_NAMES),$(mod).mod.o) \
  $(foreach mod,$(MODULE_NAMES),$(mod).o)

# Include path
# -Wno-attributes added because of 'mul_u64_u64_div_u64' attribute mismatch on 6.8.
ccflags-y += -I$(src) -Wno-attributes

# Modules objects to build
obj-m := $(addsuffix .o,$(MODULE_NAMES))

###

# Common object file lists
ARG_OBJS := \
  spade/audit/arg/print.o

BUILD_HASH_OBJS := \
  $(GEN_BUILD_HASH_OBJ_FILE)

CONTEXT_OBJS := \
  spade/audit/context/netfilter/netfilter.o \
  spade/audit/context/netfilter/print.o \
  spade/audit/context/syscall/syscall.o \
  spade/audit/context/syscall/print.o \
  spade/audit/context/context.o \
  spade/audit/context/print.o

EXPORTED_OBJS := \
  spade/audit/exported/auditing.o

GLOBAL_OBJS := \
  spade/audit/global/common/common.o \
  spade/audit/global/netfilter/netfilter.o \
  spade/audit/global/syscall/syscall.o \
  spade/audit/global/global.o

HELPER_OBJS := \
  spade/audit/helper/syscall/namespace.o \
  spade/audit/helper/syscall/network.o \
  spade/audit/helper/audit_log.o \
  spade/audit/helper/kernel.o \
  spade/audit/helper/sock.o \
  spade/audit/helper/task.o

KERNEL_OBJS := \
  spade/audit/kernel/namespace/namespace.o \
  spade/audit/kernel/namespace/setup/setup.o \
  spade/audit/kernel/netfilter/setup/setup.o \
  spade/audit/kernel/netfilter/netfilter.o \
  spade/audit/kernel/function/action.o \
  spade/audit/kernel/function/hook.o \
  spade/audit/kernel/function/op.o \
  spade/audit/kernel/setup/function/ftrace/ftrace.o \
  spade/audit/kernel/setup/function/ftrace/ftrace_helper.o \
  $(HOOKED_FUNCTION_OBJS)

MSG_OBJS := \
  spade/audit/msg/common/serialize/audit.o \
  spade/audit/msg/common/create.o \
  spade/audit/msg/namespace/serialize/audit.o \
  spade/audit/msg/namespace/create.o \
  spade/audit/msg/namespace/ops.o \
  spade/audit/msg/netfilter/serialize/audit.o \
  spade/audit/msg/netfilter/create.o \
  spade/audit/msg/netfilter/ops.o \
  spade/audit/msg/network/serialize/audit.o \
  spade/audit/msg/network/create.o \
  spade/audit/msg/network/ops.o \
  spade/audit/msg/ubsi/serialize/audit.o \
  spade/audit/msg/ubsi/create.o \
  spade/audit/msg/ubsi/ops.o \
  spade/audit/msg/ops.o

PARAM_OBJS := \
  spade/audit/param/param.o

STATE_OBJS := \
  spade/audit/state/netfilter/netfilter.o \
  spade/audit/state/netfilter/print.o \
  spade/audit/state/syscall/hook/ftrace/ftrace.o \
  spade/audit/state/syscall/hook/ftrace/print.o \
  spade/audit/state/syscall/hook/hook.o \
  spade/audit/state/syscall/hook/print.o \
  spade/audit/state/syscall/namespace/namespace.o \
  spade/audit/state/syscall/namespace/print.o \
  spade/audit/state/syscall/syscall.o \
  spade/audit/state/syscall/print.o \
  spade/audit/state/state.o \
  spade/audit/state/print.o

TYPE_OBJS := \
  spade/audit/type/parse.o \
  spade/audit/type/print.o

UTIL_OBJS := \
  spade/util/log/log.o \
  spade/util/log/module.o \
  spade/util/seqbuf/seqbuf.o

TEST_USER_BIN := \
  test/user/bin/ns/ns \
  test/user/bin/socket/net/client \
  test/user/bin/socket/net/server \
  test/user/bin/socket/unix/client \
  test/user/bin/socket/unix/server \
  test/user/bin/ubsi/ubsi

###

# main module objects
$(MAIN_MODULE_NAME)-y := \
  $(ARG_OBJS) \
  $(BUILD_HASH_OBJS) \
  $(CONTEXT_OBJS) \
  $(EXPORTED_OBJS) \
  $(GLOBAL_OBJS) \
  $(HELPER_OBJS) \
  $(KERNEL_OBJS) \
  $(MSG_OBJS) \
  $(STATE_OBJS) \
  $(TYPE_OBJS) \
  $(UTIL_OBJS) \
  spade/audit/audit.o

$(CONTROLLER_MODULE_NAME)-y := \
  $(ARG_OBJS) \
  $(BUILD_HASH_OBJS) \
  $(PARAM_OBJS) \
  $(TYPE_OBJS) \
  $(UTIL_OBJS) \
  spade/audit/controller/controller.o

# test module objects
$(TEST_MODULE_NAME)-y := \
  $(ARG_OBJS) \
  $(BUILD_HASH_OBJS) \
  $(CONTEXT_OBJS) \
  $(GLOBAL_OBJS) \
  $(HELPER_OBJS) \
  $(KERNEL_OBJS) \
  $(MSG_OBJS) \
  $(PARAM_OBJS) \
  $(STATE_OBJS) \
  $(TYPE_OBJS) \
  $(UTIL_OBJS) \
  test/kernel/spade/audit/arg.o \
  test/kernel/spade/audit/context.o \
  test/kernel/spade/audit/common.o \
  test/kernel/spade/audit/global.o \
  test/kernel/spade/audit/msg.o \
  test/kernel/spade/audit/state.o \
  test/kernel/spade/audit/test.o

###

.PHONY: all all-prereq clean test-user install check-full check hash-print hash-update

hash-print:
	$(info $(GEN_BUILD_HASH))

hash-update:
	@if [ ! -f "$(GEN_BUILD_HASH_CACHE_FILE)" ] || [ "$$(cat $(GEN_BUILD_HASH_CACHE_FILE) 2>/dev/null)" != "$(GEN_BUILD_HASH)" ]; then \
		echo "(Re-)Generating build hash"; \
		echo "$(GEN_BUILD_HASH)" > $(GEN_BUILD_HASH_CACHE_FILE); \
		sed -e 's/@GENERATED_BUILD_HASH@/"$(GEN_BUILD_HASH)"/g' $(GEN_BUILD_HASH_TEMPLATE_FILE) > $(GEN_BUILD_HASH_SRC_FILE); \
	fi

test-user: $(TEST_USER_BIN)

test/user/bin/ns/%: test/user/src/ns/%.c
	@mkdir -p $(dir $@)
	gcc -o $@ $<

test/user/bin/socket/net/%: test/user/src/socket/net/%.c
	@mkdir -p $(dir $@)
	gcc -o $@ $<

test/user/bin/socket/unix/%: test/user/src/socket/unix/%.c
	@mkdir -p $(dir $@)
	gcc -o $@ $<

test/user/bin/ubsi/%: test/user/src/ubsi/%.c
	@mkdir -p $(dir $@)
	gcc -o $@ $<

all-prereq: hash-update
	@if [ "$(IS_MICROSOFT_KERNEL)" = "true" ]; then \
		echo 'ERROR: OS Kernel not supported (Microsoft/WSL detected)'; \
		exit 1; \
	fi
	@if [ "$(IS_X86_64)" != "true" ]; then \
		echo 'ERROR: Architecture not supported. Only x86_64 is supported. Current: $(OS_ARCH)'; \
		exit 1; \
	fi
	@if [ "$(HAS_KERNEL_NUMBER)" != "true" ]; then \
		echo 'ERROR: Kernel modules build failed. Failed to get current kernel release number.'; \
		exit 1; \
	fi
	@if [ "$(IS_KERNEL_VERSION_OK)" != "true" ]; then \
		echo 'ERROR: Kernel modules build failed. Kernel version $(shell uname -r) must be between $(MIN_OS_KERNEL_NUMBER_PRINTABLE) and $(MAX_OS_KERNEL_NUMBER_PRINTABLE).'; \
		exit 1; \
	fi
	@echo 'All prerequisites satisfied'

all: all-prereq test-user
	$(MAKE) -C $(KDIR) M=$(CURDIR) EXTRA_CFLAGS="$(MOD_DEFINES) $(EXTRA_CFLAGS)" modules;
	@mkdir -p $(BUILD_DIR); \
	for file in $(MOD_OUT_FILES); do \
		if [ -f $$file ]; then \
			mv $$file $(BUILD_DIR)/; \
		fi; \
	done; \
	echo 'Warning: Kernel module uses workaround from https://github.com/xcellerator/linux_kernel_hacking/issues/3#issue-782815541 by hkerma';

clean:
	$(MAKE) -C $(KDIR) M=$(CURDIR) clean
	-rm -f $(GEN_BUILD_HASH_SRC_FILE)
	-rm -f $(GEN_BUILD_HASH_CACHE_FILE)
	-rm -rf $(BUILD_DIR)
	-rm -f $(TEST_USER_BIN)
	-rm -f $(INSTALL_DIR)/$(MAIN_MODULE_NAME).ko
	-rm -f $(INSTALL_DIR)/$(CONTROLLER_MODULE_NAME).ko

install:
	@if [ ! -f "$(BUILD_DIR)/$(MAIN_MODULE_NAME).ko" ]; then \
		echo "ERROR: $(MAIN_MODULE_NAME).ko not found in $(BUILD_DIR)/. Run 'make all' first."; \
		exit 1; \
	fi
	@if [ ! -f "$(BUILD_DIR)/$(CONTROLLER_MODULE_NAME).ko" ]; then \
		echo "ERROR: $(CONTROLLER_MODULE_NAME).ko not found in $(BUILD_DIR)/. Run 'make all' first."; \
		exit 1; \
	fi
	@mkdir -p $(INSTALL_DIR)
	@cp $(BUILD_DIR)/$(MAIN_MODULE_NAME).ko $(INSTALL_DIR)/
	@cp $(BUILD_DIR)/$(CONTROLLER_MODULE_NAME).ko $(INSTALL_DIR)/
	@echo "Successfully installed $(MAIN_MODULE_NAME).ko & $(CONTROLLER_MODULE_NAME).ko to $(INSTALL_DIR)"

check-full:
	$(CHECK_SCRIPT_MOD_TEST)
	$(CHECK_SCRIPT_MOD_MAIN) test watch_audited_user

check:
	$(CHECK_SCRIPT_MOD_TEST) --dry-run