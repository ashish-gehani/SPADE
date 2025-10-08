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

#ifndef SPADE_AUDIT_KERNEL_SYSCALL_HOOK_EXECUTION_HANDLER_HANDLER_H
#define SPADE_AUDIT_KERNEL_SYSCALL_HOOK_EXECUTION_HANDLER_HANDLER_H

#include <linux/types.h>

#include "spade/audit/kernel/syscall/arg/arg.h"
#include "spade/audit/kernel/syscall/result/result.h"
#include "spade/audit/kernel/syscall/action/action.h"

/*
    Function called by syscall hook pre-syscall-execution.

    Params:
        act_res     : The struct to put the result of the action into.
        sys_num     : Syscall number.
        sys_arg     : Syscall arguments.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int kernel_syscall_hook_execution_handler_handle_pre(
    struct kernel_syscall_action_result* act_res,
    int sys_num, struct kernel_syscall_arg *sys_arg
);

/*
    Function called by syscall hook post-syscall-execution.

    Params:
        act_res     : The struct to put the result of the action into.
        sys_num     : Syscall number.
        sys_arg     : Syscall arguments.
        sys_res     : Syscall result.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int kernel_syscall_hook_execution_handler_handle_post(
    struct kernel_syscall_action_result* act_res,
    int sys_num, struct kernel_syscall_arg *sys_arg,
    struct kernel_syscall_result *sys_res
);

#endif // SPADE_AUDIT_KERNEL_SYSCALL_HOOK_EXECUTION_HANDLER_HANDLER_H