KDIR ?= /lib/modules/$(shell uname -r)/build
BUILD_OUTPUT ?= build

# Module names
MODULE_NAMES := netio spade_audit_test

# Syscall names
SYSCALL_NAMES := accept accept4 bind clone connect fork kill recvfrom recvmsg sendmsg sendto setns unshare vfork

# Generated object file lists
SYSCALL_ACTION_OBJS := $(foreach syscall,$(SYSCALL_NAMES),spade/audit/kernel/syscall/action/audit/$(syscall)/$(syscall).o)
SYSCALL_HOOK_FUNC_OBJS := $(foreach syscall,$(SYSCALL_NAMES),spade/audit/kernel/syscall/hook/function/$(syscall)/$(syscall).o)

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
ccflags-y += -I$(src)

# Modules objects to build
obj-m := $(addsuffix .o,$(MODULE_NAMES))

###

# Common object file lists
ARG_OBJS := \
  spade/audit/arg/parse.o \
  spade/audit/arg/print.o

CONFIG_OBJS := \
  spade/audit/config/config.o \
  spade/audit/config/load.o \
  spade/audit/config/print.o

CONTEXT_OBJS := \
  spade/audit/context/netfilter/netfilter.o \
  spade/audit/context/netfilter/print.o \
  spade/audit/context/syscall/syscall.o \
  spade/audit/context/syscall/print.o \
  spade/audit/context/context.o \
  spade/audit/context/print.o

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
  spade/audit/kernel/syscall/action/audit/audit.o \
  spade/audit/kernel/syscall/action/action.o \
  spade/audit/kernel/syscall/hook/execution/handler/handler.o \
  spade/audit/kernel/syscall/hook/list.o \
  spade/audit/kernel/syscall/hook/setup/ftrace/ftrace.o \
  spade/audit/kernel/syscall/hook/setup/ftrace/ftrace_helper.o \
  spade/audit/kernel/syscall/hook/setup/ftrace/list.o \
  spade/audit/kernel/syscall/hook/setup/table/table.o \
  spade/audit/kernel/syscall/hook/setup/table/list.o

PARAM_OBJS := \
  spade/audit/param/param.o

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

UTIL_OBJS := \
  spade/util/log/log.o \
  spade/util/log/module.o \
  spade/util/print/print.o \
  spade/util/seqbuf/seqbuf.o

TEST_USER_BIN := \
  test/user/bin/ns/ns \
  test/user/bin/socket/net/client \
  test/user/bin/socket/net/server \
  test/user/bin/socket/unix/client \
  test/user/bin/socket/unix/server \
  test/user/bin/ubsi/ubsi

###

# netio module objects
netio-y := \
  $(ARG_OBJS) \
  $(CONFIG_OBJS) \
  $(CONTEXT_OBJS) \
  $(GLOBAL_OBJS) \
  $(HELPER_OBJS) \
  $(KERNEL_OBJS) \
  $(PARAM_OBJS) \
  $(MSG_OBJS) \
  $(STATE_OBJS) \
  $(SYSCALL_ACTION_OBJS) \
  $(SYSCALL_HOOK_FUNC_OBJS) \
  $(UTIL_OBJS) \
  spade/audit/audit.o

# test module objects
spade_audit_test-y := \
  $(ARG_OBJS) \
  $(CONFIG_OBJS) \
  $(CONTEXT_OBJS) \
  $(GLOBAL_OBJS) \
  $(HELPER_OBJS) \
  $(KERNEL_OBJS) \
  $(PARAM_OBJS) \
  $(MSG_OBJS) \
  $(STATE_OBJS) \
  $(SYSCALL_ACTION_OBJS) \
  $(SYSCALL_HOOK_FUNC_OBJS) \
  $(UTIL_OBJS) \
  test/kernel/arg.o \
  test/kernel/context.o \
  test/kernel/common.o \
  test/kernel/global.o \
  test/kernel/msg.o \
  test/kernel/state.o \
  test/kernel/test.o

###

.PHONY: all clean test-user

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

all: test-user
	$(MAKE) -C $(KDIR) M=$(PWD) modules
	@mkdir -p $(BUILD_OUTPUT)
	@for file in $(MOD_OUT_FILES); do \
		if [ -f $$file ]; then \
			mv $$file $(BUILD_OUTPUT)/; \
		fi; \
	done

clean:
	$(MAKE) -C $(KDIR) M=$(PWD) clean
	-rm -rf $(BUILD_OUTPUT)
	-rm -f $(TEST_USER_BIN)
