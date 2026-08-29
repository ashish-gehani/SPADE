/*
 * Helper library for ftrace hooking kernel functions
 * Author: Harvey Phillips (xcellerator@gmx.com)
 * License: GPL
 * */

#include "audit/kernel/arch/common/setup/function/ftrace/ftrace_thunk.h"

#include <linux/ftrace.h>
#include <linux/version.h>

#include "audit/kernel/arch/common/setup/function/ftrace/ftrace_helper.h"

/* See comment below within fh_install_hook() (ftrace_helper.c) */
#if KERNEL_HELPER_FTRACE_HOOK_HAS_FTRACE_REGS
void notrace fh_ftrace_thunk(unsigned long ip, unsigned long parent_ip, struct ftrace_ops *ops, struct ftrace_regs *fregs)
{
    struct ftrace_hook *hook = container_of(ops, struct ftrace_hook, ops);
    struct pt_regs *regs = ftrace_get_regs(fregs);

#if USE_FENTRY_OFFSET
    regs->pc = (unsigned long) hook->function;
#else
    if(!within_module(parent_ip, THIS_MODULE))
        regs->pc = (unsigned long) hook->function;
#endif
}
#else
void notrace fh_ftrace_thunk(unsigned long ip, unsigned long parent_ip, struct ftrace_ops *ops, struct pt_regs *regs)
{
    struct ftrace_hook *hook = container_of(ops, struct ftrace_hook, ops);

#if USE_FENTRY_OFFSET
    regs->pc = (unsigned long) hook->function;
#else
    if(!within_module(parent_ip, THIS_MODULE))
        regs->pc = (unsigned long) hook->function;
#endif
}
#endif
