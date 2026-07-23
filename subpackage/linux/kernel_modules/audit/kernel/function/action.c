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

#include <linux/types.h>

#include "audit/kernel/function/action.h"
#include "audit/kernel/function/hook.h"
#include "audit/kernel/function/op.h"
#include "audit/global/filter.h"
#include "audit/util/log/log.h"


int kernel_function_action_pre_iterate_all(const struct kernel_function_hook_context_pre *ctx_pre)
{
    const char *log_id = "kernel_function_action_pre_iterate_all";
    const struct kernel_function_op *k_f_op;
    int err;
    int i;

    if (!kernel_function_hook_context_pre_is_valid(ctx_pre))
        return -EINVAL;

    err = kernel_function_op_get_by_func_num(&k_f_op, ctx_pre->header->func_num);
    if (err != 0)
        return err;

    for (i = 0; i < KERNEL_FUNCTION_ACTION_LEN_MAX; i++)
    {
        kernel_function_action_pre_t act_pre = k_f_op->action_list->pre[i];
        if (!act_pre)
        {
            // End of list
            break;
        }

        err = act_pre(ctx_pre);
        if (err != 0)
        {
            util_log_debug(log_id, "Failed to execute pre action for func num %d at index: %d. Err: %d", ctx_pre->header->func_num, i, err);
            break;
        }

        // Check if we should skip remaining pre actions or disallow function
        if (kernel_function_action_result_is_skip_pre_actions(ctx_pre->header->act_res->type))
        {
            util_log_debug(
                log_id,
                "Skipping remaining pre actions for func num %d at index %d due to action result type: %d",
                ctx_pre->header->func_num, i, ctx_pre->header->act_res->type
            );
            break;
        }
    }

    return err;
}

int kernel_function_action_post_iterate_all(const struct kernel_function_hook_context_post *ctx_post)
{
    const char *log_id = "kernel_function_action_post_iterate_all";
    const struct kernel_function_op *k_f_op;
    int err;
    int i;
    enum kernel_function_number f_num;

    if (!kernel_function_hook_context_post_is_valid(ctx_post))
    {
        util_log_debug(log_id, "Invalid ctx. kernel_function_hook_context_post_is_valid.");
        return -EINVAL;
    }

    f_num = ctx_post->header->func_num;

    err = kernel_function_op_get_by_func_num(&k_f_op, f_num);
    if (err != 0)
    {
        util_log_debug(log_id, "No func by num %d. kernel_function_op_get_by_func_num.", f_num);
        return err;
    }

    util_log_debug(log_id, "Starting action list iteration for func %d", f_num);

    // Check if we should skip post actions because pre set to skip post actions.
    if (kernel_function_action_result_is_skip_post_actions(ctx_post->header->act_res->type))
    {
        util_log_debug(
            log_id,
            "Skipping all post actions for func num %d due to action result type %d set by a pre-action",
            f_num, ctx_post->header->act_res->type
        );
        return 0;
    }

    for (i = 0; i < KERNEL_FUNCTION_ACTION_LEN_MAX; i++)
    {
        kernel_function_action_post_t act_post = k_f_op->action_list->post[i];
        if (!act_post)
        {
            // End of list
            util_log_debug(log_id, "End of action list for func %d", f_num);
            break;
        }

        util_log_debug(log_id, "Executing action list item at index %d for func %d", i, f_num);

        err = act_post(ctx_post);
        if (err != 0)
        {
            util_log_debug(log_id, "Failed to execute post action for func num %d at index: %d. Err: %d", f_num, i, err);
            break;
        }

        // Check if we should skip remaining post actions
        if (kernel_function_action_result_is_skip_post_actions(ctx_post->header->act_res->type))
        {
            util_log_debug(
                log_id,
                "Skipping remaining post actions for func num %d at index %d due to action result type: %d",
                f_num, i, ctx_post->header->act_res->type
            );
            break;
        }
    }

    return err;
}

int kernel_function_action_pre_is_actionable(
    const struct kernel_function_hook_context_pre *ctx_pre
)
{
    const char *log_id = "kernel_function_action_pre_is_actionable";
    bool is_actionable;
    enum kernel_function_number func_num;
    pid_t pid, ppid;
    uid_t uid;

    if (!kernel_function_hook_context_pre_is_valid(ctx_pre))
        return -EINVAL;

    func_num = ctx_pre->header->func_num;
    pid = ctx_pre->proc->pid;
    ppid = ctx_pre->proc->ppid;
    uid = ctx_pre->proc->uid;

    is_actionable = global_filter_function_pre_execution_is_actionable(
        func_num, pid, ppid, uid
    );

    if (!is_actionable)
    {
        kernel_function_action_result_set_skip_pre_actions(ctx_pre->header->act_res);
    }

    if (is_actionable)
        util_log_debug(
            log_id,
            "function={is_actionable=%d, func_num=%d, pid=%d, ppid=%d, uid=%u}",
            is_actionable, func_num, pid, ppid, uid
        );

    return 0;
}

int kernel_function_action_post_is_actionable(
    const struct kernel_function_hook_context_post *ctx_post
)
{
    const char *log_id = "kernel_function_action_post_is_actionable";
    bool func_success;
    bool is_actionable;
    enum kernel_function_number func_num;
    pid_t pid, ppid;
    uid_t uid;

    if (!kernel_function_hook_context_post_is_valid(ctx_post))
        return -EINVAL;

    func_success = ctx_post->func_res->success;
    func_num = ctx_post->header->func_num;
    pid = ctx_post->proc->pid;
    ppid = ctx_post->proc->ppid;
    uid = ctx_post->proc->uid;

    is_actionable = global_filter_function_post_execution_is_actionable(
        func_num, func_success, pid, ppid, uid
    );

    if (!is_actionable)
    {
        kernel_function_action_result_set_skip_post_actions(ctx_post->header->act_res);
    }

    if (is_actionable)
        util_log_debug(
            log_id,
            "function={is_actionable=%d, func_num=%d, func_success=%d, pid=%d, ppid=%d, uid=%u}",
            is_actionable, func_num, func_success, pid, ppid, uid
        );

    return 0;
}

bool kernel_function_action_result_is_disallow_function(
    enum kernel_function_action_result_type type
)
{
    return (type & KFAR_TYPE_DISALLOW_FUNCTION) == KFAR_TYPE_DISALLOW_FUNCTION;
}

bool kernel_function_action_result_is_skip_pre_actions(
    enum kernel_function_action_result_type type
)
{
    return (type & KFAR_TYPE_SKIP_PRE_ACTIONS) == KFAR_TYPE_SKIP_PRE_ACTIONS;
}

bool kernel_function_action_result_is_skip_post_actions(
    enum kernel_function_action_result_type type
)
{
    return (type & KFAR_TYPE_SKIP_POST_ACTIONS) == KFAR_TYPE_SKIP_POST_ACTIONS;
}

void kernel_function_action_result_set_skip_pre_actions(
    struct kernel_function_action_result *act_res
)
{
    if (act_res)
        act_res->type |= KFAR_TYPE_SKIP_PRE_ACTIONS;
}

void kernel_function_action_result_set_disallow_function(
    struct kernel_function_action_result *act_res
)
{
    if (act_res)
        act_res->type |= KFAR_TYPE_DISALLOW_FUNCTION;
}

void kernel_function_action_result_set_skip_post_actions(
    struct kernel_function_action_result *act_res
)
{
    if (act_res)
        act_res->type |= KFAR_TYPE_SKIP_POST_ACTIONS;
}

void kernel_function_action_result_set_skip_all_actions(
    struct kernel_function_action_result *act_res
)
{
    if (act_res)
        act_res->type |= KFAR_TYPE_SKIP_ALL_ACTIONS;
}

