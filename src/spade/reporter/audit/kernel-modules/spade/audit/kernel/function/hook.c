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

#include <linux/errno.h>

#include "spade/audit/kernel/function/action.h"
#include "spade/audit/kernel/function/hook.h"
#include "spade/audit/kernel/function/op.h"
#include "spade/audit/global/global.h"
#include "spade/audit/global/filter.h"
#include "spade/audit/kernel/helper/task.h"
#include "spade/util/log/log.h"


bool kernel_function_hook_context_pre_is_valid(const struct kernel_function_hook_context_pre *hook_ctx_pre)
{
    return (
        hook_ctx_pre
        && hook_ctx_pre->header
        && hook_ctx_pre->proc
        && hook_ctx_pre->header->act_res
        && hook_ctx_pre->header->func_arg && hook_ctx_pre->header->func_arg->arg
    );
}

bool kernel_function_hook_context_post_is_valid(const struct kernel_function_hook_context_post *hook_ctx_post)
{
    return (
        hook_ctx_post
        && hook_ctx_post->header
        && hook_ctx_post->proc
        && hook_ctx_post->header->act_res
        && hook_ctx_post->header->func_arg && hook_ctx_post->header->func_arg->arg
        && hook_ctx_post->func_res && hook_ctx_post->func_res->res
    );
}

int kernel_function_hook_pre(
    const struct kernel_function_hook_context_pre *hook_ctx_pre
)
{
    enum kernel_function_number func_num;
    pid_t pid, ppid;
    uid_t uid;

    if (!kernel_function_hook_context_pre_is_valid(hook_ctx_pre))
    {
        return -EINVAL;
    }

    func_num = hook_ctx_pre->header->func_num;
    pid = hook_ctx_pre->proc->pid;
    ppid = hook_ctx_pre->proc->ppid;
    uid = hook_ctx_pre->proc->uid;

    if (!global_is_auditing_started())
    {
        return 0;
    }

    return kernel_function_action_pre_iterate_all(hook_ctx_pre);
}

int kernel_function_hook_post(
    const struct kernel_function_hook_context_post *hook_ctx_post
)
{
    enum kernel_function_number func_num;
    pid_t pid, ppid;
    uid_t uid;
    bool func_success;

    if (!kernel_function_hook_context_post_is_valid(hook_ctx_post))
    {
        return -EINVAL;
    }

    func_num = hook_ctx_post->header->func_num;
    pid = hook_ctx_post->proc->pid;
    ppid = hook_ctx_post->proc->ppid;
    uid = hook_ctx_post->proc->uid;
    func_success = hook_ctx_post->func_res->success;

    if (!global_is_auditing_started())
    {
        return 0;
    }

    return kernel_function_action_post_iterate_all(hook_ctx_post);
}