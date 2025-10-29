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

#include "spade/audit/kernel/function/context/post.h"
#include "spade/audit/kernel/function/context/pre.h"
#include "spade/audit/kernel/function/hook/execution/handler/handler.h"
#include "spade/audit/global/global.h"
#include "spade/audit/helper/task.h"
#include "spade/util/log/log.h"


static void _init_action_audit(
    struct kernel_syscall_action *dst
)
{
    dst->type = ACTION_TYPE_AUDIT;
}

static void _init_sys_context_post(
    struct kernel_syscall_context_post *dst,
    int sys_num, struct kernel_syscall_arg *sys_arg,
    struct kernel_syscall_result *sys_res
)
{
    dst->header.type = SYSCALL_CONTEXT_TYPE_POST;
    dst->header.sys_num = sys_num;
    dst->header.sys_arg = *sys_arg;
    dst->sys_res = *sys_res;
}

static void _init_sys_context_pre(
    struct kernel_syscall_context_pre *dst,
    int sys_num, struct kernel_syscall_arg *sys_arg
)
{
    dst->header.type = SYSCALL_CONTEXT_TYPE_PRE;
    dst->header.sys_num = sys_num;
    dst->header.sys_arg = *sys_arg;
}

int kernel_syscall_hook_execution_handler_handle_pre(
    struct kernel_syscall_action_result* act_res,
    int sys_num, struct kernel_syscall_arg *sys_arg
)
{
    struct kernel_syscall_action act_audit;
    struct kernel_syscall_context_pre sys_ctx_pre;

    if (!act_res || !sys_arg)
    {
        return -EINVAL;
    }

    _init_action_audit(&act_audit);
    _init_sys_context_pre(&sys_ctx_pre, sys_num, sys_arg);

    // todo... add actions ... careful of incorrect arguments since the syscall might fail later.
    act_res->result = 0;
    act_res->type = ACTION_RESULT_TYPE_SUCCESS;
    return 0;
}

int kernel_syscall_hook_execution_handler_handle_post(
    struct kernel_syscall_action_result* act_res,
    int sys_num, struct kernel_syscall_arg *sys_arg,
    struct kernel_syscall_result *sys_res
)
{
    const char *log_id = "kernel_syscall_hook_execution_handler_handle_post";
    int err;
    struct kernel_syscall_action act_audit;
    struct kernel_syscall_context_post sys_ctx_post;
    pid_t pid, ppid;
    uid_t uid;

    if (!act_res || !sys_arg || !sys_res)
    {
        return -EINVAL;
    }

    _init_action_audit(&act_audit);
    _init_sys_context_post(&sys_ctx_post, sys_num, sys_arg, sys_res);

    pid = helper_task_task_view_current_pid();
    ppid = helper_task_task_view_current_ppid();
    uid = helper_task_host_view_current_uid();

    if (!global_is_auditing_started())
    {
        return 0;
    }

    if (!global_is_syscall_loggable(sys_num, sys_res->success, pid, ppid, uid))
    {
        return 0;
    }

    util_log_debug(
        log_id,
        "loggable_event={sys_num=%d, sys_exit=%ld, sys_success=%d, pid=%d, ppid=%d, uid=%u}",
        sys_num, sys_res->ret, sys_res->success, pid, ppid, uid
    );

    err = kernel_syscall_action_handle(&act_audit, &(sys_ctx_post.header));
    if (err == 0)
    {
        act_res->result = act_audit.result.result;
        act_res->type = act_audit.result.type;
    }

    return err;
}