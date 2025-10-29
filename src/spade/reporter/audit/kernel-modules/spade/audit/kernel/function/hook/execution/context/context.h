/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2025 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */

#ifndef SPADE_AUDIT_KERNEL_FUNCTION_HOOK_EXECUTION_CONTEXT_CONTEXT_H
#define SPADE_AUDIT_KERNEL_FUNCTION_HOOK_EXECUTION_CONTEXT_CONTEXT_H

#include <linux/types.h>

#include "spade/audit/kernel/function/arg/arg.h"

enum kernel_syscall_hook_execution_context_type
{
    HOOK_EXECUTION_CONTEXT_TYPE_PRE,
    HOOK_EXECUTION_CONTEXT_TYPE_POST
};

struct kernel_syscall_hook_execution_context
{
    enum kernel_syscall_hook_execution_context_type ctx_type;
    int sys_num;
    struct kernel_syscall_arg sys_arg;
};

#endif // SPADE_AUDIT_KERNEL_FUNCTION_HOOK_EXECUTION_CONTEXT_CONTEXT_H