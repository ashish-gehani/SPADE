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
    KFAR_TYPE_SKIP_ALL_ACTIONS      = KFAR_TYPE_SKIP_PRE_ACTIONS | KFAR_TYPE_SKIP_POST_ACTIONS,
};

struct kernel_function_action_result
{
    enum kernel_function_action_result_type type;
};

/*
    Function called by function-hook to perform a pre-execution action.

    Params:
        ctx_pre     : Function context pre-execution.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
typedef int (*kernel_function_action_pre_t)(
    const struct kernel_function_hook_context_pre *ctx_pre
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
    const struct kernel_function_hook_context_post *ctx_post
);

struct kernel_function_action_list
{
    const kernel_function_action_pre_t pre[KERNEL_FUNCTION_ACTION_LEN_MAX];
    const kernel_function_action_post_t post[KERNEL_FUNCTION_ACTION_LEN_MAX];
};

/*
    Iterate and execute all registered pre-execution actions for a hooked function.

    Iterates through the pre-action list for the function and executes each action.
    Stops iteration if an action fails, or if the action result indicates to skip
    remaining pre-actions or disallow the function.

    Params:
        ctx_pre     : Function context pre-execution.

    Returns:
        0       -> Success (all applicable actions executed successfully).
        -EINVAL -> Invalid context.
        -ive    -> Error code from failed action.
*/
int kernel_function_action_pre_iterate_all(const struct kernel_function_hook_context_pre *ctx_pre);

/*
    Iterate and execute all registered post-execution actions for a hooked function.

    Iterates through the post-action list for the function and executes each action.
    Stops iteration if an action fails or if the action result indicates to skip
    remaining post-actions. Skips all post-actions if a pre-action set the skip
    post-actions flag.

    Params:
        ctx_post    : Function context post-execution.

    Returns:
        0       -> Success (all applicable actions executed successfully).
        -EINVAL -> Invalid context.
        -ive    -> Error code from failed action.
*/
int kernel_function_action_post_iterate_all(const struct kernel_function_hook_context_post *ctx_post);

/*
    Pre-execution action to check if execution should be performed based on global filter.

    Params:
        ctx_pre     : Function context pre-execution.

    Returns:
        0       -> Success (action should be performed).
        -EINVAL -> Invalid context.
        -EPERM  -> Action should be skipped (not actionable).
*/
int kernel_function_action_pre_is_actionable(
    const struct kernel_function_hook_context_pre *ctx_pre
);

/*
    Post-execution action to check if execution should be performed based on global filter.

    Params:
        ctx_post    : Function context post-execution.

    Returns:
        0       -> Success (action should be performed).
        -EINVAL -> Invalid context.
        -EPERM  -> Action should be skipped (not actionable).
*/
int kernel_function_action_post_is_actionable(
    const struct kernel_function_hook_context_post *ctx_post
);

/*
    Check if the action result indicates the function should be disallowed.

    Params:
        type    : Action result type to check.

    Returns:
        true    -> Function execution should be disallowed.
        false   -> Function execution should proceed normally.
*/
bool kernel_function_action_result_is_disallow_function(
    enum kernel_function_action_result_type type
);

/*
    Check if the action result indicates pre-execution actions should be skipped.

    Params:
        type    : Action result type to check.

    Returns:
        true    -> Pre-execution actions should be skipped.
        false   -> Pre-execution actions should be performed.
*/
bool kernel_function_action_result_is_skip_pre_actions(
    enum kernel_function_action_result_type type
);

/*
    Check if the action result indicates post-execution actions should be skipped.

    Params:
        type    : Action result type to check.

    Returns:
        true    -> Post-execution actions should be skipped.
        false   -> Post-execution actions should be performed.
*/
bool kernel_function_action_result_is_skip_post_actions(
    enum kernel_function_action_result_type type
);

/*
    Set the skip pre-actions bit in the action result.

    Params:
        act_res : Pointer to the action result to modify.
*/
void kernel_function_action_result_set_skip_pre_actions(
    struct kernel_function_action_result *act_res
);

/*
    Set the disallow function bit in the action result.

    Params:
        act_res : Pointer to the action result to modify.
*/
void kernel_function_action_result_set_disallow_function(
    struct kernel_function_action_result *act_res
);

/*
    Set the skip post-actions bit in the action result.

    Params:
        act_res : Pointer to the action result to modify.
*/
void kernel_function_action_result_set_skip_post_actions(
    struct kernel_function_action_result *act_res
);

/*
    Set the skip all actions bits in the action result (both pre and post).

    Params:
        act_res : Pointer to the action result to modify.
*/
void kernel_function_action_result_set_skip_all_actions(
    struct kernel_function_action_result *act_res
);

#endif // SPADE_AUDIT_KERNEL_FUNCTION_ACTION_H