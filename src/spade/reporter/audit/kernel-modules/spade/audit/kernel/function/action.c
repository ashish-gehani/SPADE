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

#include "spade/audit/kernel/function/action.h"
#include "spade/audit/kernel/function/op.h"
#include "spade/util/log/log.h"


int kernel_function_action_pre(struct kernel_function_hook_context_pre *ctx_pre)
{
    const char *log_id = "kernel_function_action_pre";
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
            util_log_warn(log_id, "Failed to execute pre action for func num %d at index: %d. Err: %d", ctx_pre->header->func_num, i, err);
            break;
        }
    }

    return err;
}

int kernel_function_action_post(struct kernel_function_hook_context_post *ctx_post)
{
    const char *log_id = "kernel_function_action_post";
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
            util_log_warn(log_id, "Failed to execute post action for func num %d at index: %d. Err: %d", f_num, i, err);
            break;
        }
    }

    return err;
}

