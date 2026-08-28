/*
 * Helper library for ftrace hooking kernel functions
 * Author: Harvey Phillips (xcellerator@gmx.com)
 * License: GPL
 * */

#include "audit/kernel/arch/common/setup/function/ftrace/ftrace_thunk.h"

#include <linux/ftrace.h>
#include <linux/version.h>

/* Arch-specific implementations (e.g. audit/kernel/arch/x86_64/setup/function/ftrace/ftrace_thunk.c)
 * provide a strong definition of fh_ftrace_thunk() that overrides this one at link time. This weak
 * empty definition exists so archs without one yet still link successfully. */
#if KERNEL_HELPER_FTRACE_HOOK_HAS_FTRACE_REGS
void __weak notrace fh_ftrace_thunk(unsigned long ip, unsigned long parent_ip, struct ftrace_ops *ops, struct ftrace_regs *fregs)
{
}
#else
void __weak notrace fh_ftrace_thunk(unsigned long ip, unsigned long parent_ip, struct ftrace_ops *ops, struct pt_regs *regs)
{
}
#endif
