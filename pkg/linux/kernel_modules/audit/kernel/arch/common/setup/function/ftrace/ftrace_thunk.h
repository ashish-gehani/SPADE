/*
 * Helper library for ftrace hooking kernel functions
 * Author: Harvey Phillips (xcellerator@gmx.com)
 * License: GPL
 * */

#ifndef SPADE_AUDIT_KERNEL_ARCH_COMMON_SETUP_FUNCTION_FTRACE_FTRACE_THUNK_H
#define SPADE_AUDIT_KERNEL_ARCH_COMMON_SETUP_FUNCTION_FTRACE_FTRACE_THUNK_H

#include <linux/ftrace.h>

#include "audit/kernel/arch/common/helper/kernel.h"

/* See comment above fh_ftrace_thunk() in ftrace_thunk.c */
#if KERNEL_HELPER_KERNEL_FTRACE_THUNK_HAS_FTRACE_REGS
void notrace fh_ftrace_thunk(unsigned long ip, unsigned long parent_ip, struct ftrace_ops *ops, struct ftrace_regs *fregs);
#else
void notrace fh_ftrace_thunk(unsigned long ip, unsigned long parent_ip, struct ftrace_ops *ops, struct pt_regs *regs);
#endif

#endif // SPADE_AUDIT_KERNEL_ARCH_COMMON_SETUP_FUNCTION_FTRACE_FTRACE_THUNK_H
