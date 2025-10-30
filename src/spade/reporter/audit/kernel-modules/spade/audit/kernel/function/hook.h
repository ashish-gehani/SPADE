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

#include "spade/audit/kernel/function/arg.h"
#include "spade/audit/kernel/function/action.h"
#include "spade/audit/kernel/function/number.h"
#include "spade/audit/kernel/function/result.h"

struct kernel_function_hook
{
    /*
        Get syscall/function number.

        Returns:
            Sys number.
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


enum kernel_function_hook_context_type
{
    KERNEL_FUNCTION_HOOK_CONTEXT_TYPE_PRE,
    KERNEL_FUNCTION_HOOK_CONTEXT_TYPE_POST
};

struct kernel_function_hook_context
{
    enum kernel_function_hook_context_type type;
    enum kernel_function_number func_num;

    const struct kernel_function_arg *func_arg;

    struct kernel_function_action_result *act_res;
};

struct kernel_function_hook_context_post
{
    const struct kernel_function_hook_context *header;
    const struct kernel_function_result *func_res;
};

struct kernel_function_hook_context_pre
{
    const struct kernel_function_hook_context *header;
};

bool kernel_function_hook_context_pre_is_valid(
    struct kernel_function_hook_context_pre *hook_ctx_pre
);

bool kernel_function_hook_context_post_is_valid(
    struct kernel_function_hook_context_post *hook_ctx_post
);


/*
    Function called by function hook before execution.

    Params:
        h_ctx_pre   : Hook context pre-execution.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int kernel_function_hook_pre(
    struct kernel_function_hook_context_pre *hook_ctx_pre
);

/*
    Function called by function hook before execution.

    Params:
        h_ctx_post  : Hook context post-execution.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int kernel_function_hook_post(
    struct kernel_function_hook_context_post *hook_ctx_post
);


#endif // SPADE_AUDIT_KERNEL_FUNCTION_HOOK_H