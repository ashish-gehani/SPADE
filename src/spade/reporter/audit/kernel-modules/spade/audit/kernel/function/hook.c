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
#include "spade/audit/helper/task.h"
#include "spade/util/log/log.h"


bool kernel_function_hook_context_pre_is_valid(struct kernel_function_hook_context_pre *hook_ctx_pre)
{
    return (
        hook_ctx_pre
        && hook_ctx_pre->header
        && hook_ctx_pre->header->type == KERNEL_FUNCTION_HOOK_CONTEXT_TYPE_PRE
        && hook_ctx_pre->header->act_res
        && hook_ctx_pre->header->func_arg && hook_ctx_pre->header->func_arg->arg && hook_ctx_pre->header->func_arg->arg_size != 0
    );
}

int kernel_function_hook_pre(
    struct kernel_function_hook_context_pre *hook_ctx_pre
)
{
    const char *log_id = "kernel_function_hook_pre";
    pid_t pid, ppid;
    uid_t uid;
    enum kernel_function_number func_num;
    bool dummy_success = true;

    if (!kernel_function_hook_context_pre_is_valid(hook_ctx_pre))
    {
        return -EINVAL;
    }

    func_num = hook_ctx_pre->header->func_num;

    pid = helper_task_task_view_current_pid();
    ppid = helper_task_task_view_current_ppid();
    uid = helper_task_host_view_current_uid();

    if (!global_is_auditing_started())
    {
        return 0;
    }

    if (!global_is_syscall_loggable(
            hook_ctx_pre->header->func_num, dummy_success,
            pid, ppid, uid
        )
    )
    {
        return 0;
    }

    util_log_debug(
        log_id,
        "loggable_event={func_num=%d, pid=%d, ppid=%d, uid=%u}",
        hook_ctx_pre->header->func_num, pid, ppid, uid
    );

    return kernel_function_action_pre(hook_ctx_pre);
}

bool kernel_function_hook_context_post_is_valid(struct kernel_function_hook_context_post *hook_ctx_post)
{
    return (
        hook_ctx_post
        && hook_ctx_post->header
        && hook_ctx_post->header->type == KERNEL_FUNCTION_HOOK_CONTEXT_TYPE_POST
        && hook_ctx_post->header->act_res
        && hook_ctx_post->header->func_arg && hook_ctx_post->header->func_arg->arg && hook_ctx_post->header->func_arg->arg_size != 0
        && hook_ctx_post->func_res && hook_ctx_post->func_res->res && hook_ctx_post->func_res->res_size != 0
    );
}

int kernel_function_hook_post(
    struct kernel_function_hook_context_post *hook_ctx_post
)
{
    const char *log_id = "kernel_function_hook_post";
    pid_t pid, ppid;
    uid_t uid;
    enum kernel_function_number func_num;

    if (!kernel_function_hook_context_post_is_valid(hook_ctx_post))
    {
        return -EINVAL;
    }

    func_num = hook_ctx_post->header->func_num;

    pid = helper_task_task_view_current_pid();
    ppid = helper_task_task_view_current_ppid();
    uid = helper_task_host_view_current_uid();

    if (!global_is_auditing_started())
    {
        return 0;
    }

    if (!global_is_syscall_loggable(
            hook_ctx_post->header->func_num, hook_ctx_post->func_res->success,
            pid, ppid, uid
        )
    )
    {
        return 0;
    }

    util_log_debug(
        log_id,
        "loggable_event={func_num=%d, func_success=%d, pid=%d, ppid=%d, uid=%u}",
        hook_ctx_post->header->func_num, hook_ctx_post->func_res->success, pid, ppid, uid
    );

    return kernel_function_action_post(hook_ctx_post);
}