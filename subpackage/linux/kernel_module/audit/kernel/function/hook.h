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

#ifndef SPADE_AUDIT_KERNEL_FUNCTION_HOOK_H
#define SPADE_AUDIT_KERNEL_FUNCTION_HOOK_H

#include <linux/types.h>

#include "audit/kernel/helper/task.h"
#include "audit/kernel/function/arg.h"
#include "audit/kernel/function/action.h"
#include "audit/kernel/function/number.h"
#include "audit/kernel/function/result.h"

struct kernel_function_hook
{
    /*
        Get function number.

        Returns:
            Function number.
    */
    enum kernel_function_number (*get_num)(void);

    /*
        Get function symbol name.

        Returns:
            name    -> Success.
            NULL    -> Error.
    */
    const char* (*get_name)(void);

    /*
        Get the hook function... called instead of the original function.

        Returns:
            ptr     -> Success.
            NULL    -> Error.
    */
    void* (*get_hook_func)(void);

    /*
        Get the pointer where the original functions address should be set.

        Returns:
            ptr     -> Success.
            NULL    -> Error.
    */
    void* (*get_orig_func_ptr)(void);
};

struct kernel_function_hook_process_context
{
    const pid_t pid;
    const pid_t ppid;
    const uid_t uid;
};

#define KERNEL_FUNCTION_HOOK_PROCESS_CONTEXT_CURRENT \
    &(const struct kernel_function_hook_process_context){ \
        .pid = kernel_helper_task_task_view_current_pid(), \
        .ppid = kernel_helper_task_task_view_current_ppid(), \
        .uid = kernel_helper_task_host_view_current_uid() \
    } \

struct kernel_function_hook_context
{
    const enum kernel_function_number func_num;

    const struct kernel_function_arg *func_arg;

    struct kernel_function_action_result * const act_res;
};

struct kernel_function_hook_context_post
{
    // first member always
    const struct kernel_function_hook_context *header;
    const struct kernel_function_hook_process_context *proc;
    const struct kernel_function_result *func_res;
};

struct kernel_function_hook_context_pre
{
    // first member always
    const struct kernel_function_hook_context *header;
    const struct kernel_function_hook_process_context *proc;
};

bool kernel_function_hook_context_pre_is_valid(
    const struct kernel_function_hook_context_pre *hook_ctx_pre
);

bool kernel_function_hook_context_post_is_valid(
    const struct kernel_function_hook_context_post *hook_ctx_post
);

/*
    Function called by function hook before execution.

     Params:
        hook_ctx_pre   : Hook context pre-execution.

     Returns:
         0       -> Success.
         -ive    -> Error code.
*/
int kernel_function_hook_pre(
    const struct kernel_function_hook_context_pre *hook_ctx_pre
);
 
/*
    Function called by function hook after execution.

    Params:
        h_ctx_post  : Hook context post-execution.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int kernel_function_hook_post(
     const struct kernel_function_hook_context_post *hook_ctx_post
);


#endif // SPADE_AUDIT_KERNEL_FUNCTION_HOOK_H