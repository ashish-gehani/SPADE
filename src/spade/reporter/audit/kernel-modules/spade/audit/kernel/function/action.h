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

#ifndef SPADE_AUDIT_KERNEL_FUNCTION_ACTION_H
#define SPADE_AUDIT_KERNEL_FUNCTION_ACTION_H


#include <linux/types.h>


/* Forward declarations to avoid circular dependency with hook.h */
struct kernel_function_hook_context_pre;
struct kernel_function_hook_context_post;


#define KERNEL_FUNCTION_ACTION_LEN_MAX 8


enum kernel_function_action_result_type
{
    KFAR_TYPE_SKIP_PRE_ACTIONS      = 0b00000001,
    KFAR_TYPE_DISALLOW_FUNCTION     = 0b00000010,
    KFAR_TYPE_SKIP_POST_ACTIONS     = 0b00000100,
};

struct kernel_function_action_result
{
    enum kernel_function_action_result_type type;
};

static inline bool kernel_function_action_result_is_disallow_function(
    enum kernel_function_action_result_type type
)
{
    return (type & KFAR_TYPE_DISALLOW_FUNCTION) == KFAR_TYPE_DISALLOW_FUNCTION;
}

static inline bool kernel_function_action_result_is_skip_pre_actions(
    enum kernel_function_action_result_type type
)
{
    return (type & KFAR_TYPE_SKIP_PRE_ACTIONS) == KFAR_TYPE_SKIP_PRE_ACTIONS;
}

static inline bool kernel_function_action_result_is_skip_post_actions(
    enum kernel_function_action_result_type type
)
{
    return (type & KFAR_TYPE_SKIP_POST_ACTIONS) == KFAR_TYPE_SKIP_POST_ACTIONS;
}

/*
    Function called by function-hook to perform a pre-execution action.

    Params:
        ctx_pre     : Function context pre-execution.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
typedef int (*kernel_function_action_pre_t)(
    struct kernel_function_hook_context_pre *ctx_pre
);

/*
    Function called by function-hook to perform a post-execution action.

    Params:
        ctx_post    : Function context post-execution.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
typedef int (*kernel_function_action_post_t)(
    struct kernel_function_hook_context_post *ctx_post
);

struct kernel_function_action_list
{
    kernel_function_action_pre_t pre[KERNEL_FUNCTION_ACTION_LEN_MAX];
    kernel_function_action_post_t post[KERNEL_FUNCTION_ACTION_LEN_MAX];
};

int kernel_function_action_pre(struct kernel_function_hook_context_pre *ctx_pre);

int kernel_function_action_post(struct kernel_function_hook_context_post *ctx_post);


#endif // SPADE_AUDIT_KERNEL_FUNCTION_ACTION_H