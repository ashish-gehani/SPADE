KDIR ?= /lib/modules/$(shell uname -r)/build
BUILD_OUTPUT ?= build

# Module names
MODULE_NAMES := netio netio_controller

# Syscall names
SYSCALL_NAMES := accept accept4 bind clone connect fork recvfrom recvmsg sendmsg sendto setns unshare vfork

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


### BEGIN - Generate hash
GEN_H_FILE := spade/config/generated.h
GEN_TMPLT_FILE := spade/config/generated.template

GENERATED_BUILD_HASH := $(shell \
  find $(src) -type f \( -name '*.c' -o -name '*.h' \) \
    ! -name 'generated.h' -print0 \
  | sort -z \
  | xargs -0 sha256sum | sha256sum | cut -d' ' -f1)
### END - Generate hash

# Include path
ccflags-y += -I$(src)

# Modules objects to build
obj-m := $(addsuffix .o,$(MODULE_NAMES))

# netio module objects
netio-y := \
  spade/arg/parse.o \
  spade/arg/print.o \
  spade/audit/context/netfilter/netfilter.o \
  spade/audit/context/syscall/syscall.o \
  spade/audit/context/context.o \
  spade/audit/global/common/common.o \
  spade/audit/global/netfilter/netfilter.o \
  spade/audit/global/syscall/syscall.o \
  spade/audit/global/global.o \
  spade/audit/helper/syscall/namespace.o \
  spade/audit/helper/syscall/network.o \
  spade/audit/helper/audit_log.o \
  spade/audit/helper/build_hash.o \
  spade/audit/helper/kernel.o \
  spade/audit/helper/sock.o \
  spade/audit/helper/task.o \
  spade/audit/kernel/namespace/setup/setup.o \
  spade/audit/kernel/netfilter/setup/setup.o \
  spade/audit/kernel/netfilter/netfilter.o \
  $(SYSCALL_ACTION_OBJS) \
  spade/audit/kernel/syscall/action/audit/audit.o \
  spade/audit/kernel/syscall/action/action.o \
  spade/audit/kernel/syscall/hook/execution/handler/handler.o \
  $(SYSCALL_HOOK_FUNC_OBJS) \
  spade/audit/kernel/syscall/hook/list.o \
  spade/audit/kernel/syscall/hook/setup/ftrace/ftrace.o \
  spade/audit/kernel/syscall/hook/setup/ftrace/ftrace_helper.o \
  spade/audit/kernel/syscall/hook/setup/ftrace/list.o \
  spade/audit/kernel/syscall/hook/setup/table/table.o \
  spade/audit/kernel/syscall/hook/setup/table/list.o \
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
  spade/audit/msg/ops.o \
  spade/audit/state/netfilter/netfilter.o \
  spade/audit/state/syscall/hook/ftrace/ftrace.o \
  spade/audit/state/syscall/hook/table/table.o \
  spade/audit/state/syscall/hook/hook.o \
  spade/audit/state/syscall/namespace/namespace.o \
  spade/audit/state/syscall/syscall.o \
  spade/audit/state/state.o \
  spade/audit/audit.o \
  spade/config/config.o \
  spade/util/log/log.o \
  spade/util/seqbuf/seqbuf.o

# netio controller module objects
netio_controller-y := \
  spade/arg/parse.o \
  spade/arg/print.o \
  spade/config/config.o \
  spade/controller/controller.o \
  spade/controller/param.o \
  spade/util/log/log.o \
  spade/util/seqbuf/seqbuf.o


.PHONY: hash-print all clean

hash-print:
	$(info $(GENERATED_BUILD_HASH))

$(GEN_H_FILE): $(GEN_TMPLT_FILE)
	@sed -e 's/@GENERATED_BUILD_HASH@/$(GENERATED_BUILD_HASH)/g' $< > $@

all: $(GEN_H_FILE)
	$(MAKE) -C $(KDIR) M=$(PWD) modules
	@mkdir -p $(BUILD_OUTPUT)
	@for file in $(MOD_OUT_FILES); do \
		if [ -f $$file ]; then \
			mv $$file $(BUILD_OUTPUT)/; \
		fi; \
	done

clean:
	$(MAKE) -C $(KDIR) M=$(PWD) clean
	-rm -f $(GEN_H_FILE)
	-rm -rf $(BUILD_OUTPUT)
