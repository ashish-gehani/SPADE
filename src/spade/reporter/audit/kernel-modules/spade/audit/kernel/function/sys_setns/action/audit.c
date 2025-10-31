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
#include <linux/types.h>
#include <asm/syscall.h>

#include "spade/audit/kernel/function/number.h"
#include "spade/audit/kernel/function/hook.h"
#include "spade/audit/kernel/function/sys_setns/action/audit.h"
#include "spade/audit/kernel/function/sys_setns/arg.h"
#include "spade/audit/kernel/function/sys_setns/result.h"
#include "spade/audit/msg/namespace/namespace.h"
#include "spade/audit/msg/ops.h"
#include "spade/audit/helper/syscall/namespace.h"
#include "spade/audit/helper/audit_log.h"
#include "spade/util/log/log.h"
#include "spade/audit/helper/task.h"


static const enum msg_common_type GLOBAL_MSG_TYPE = MSG_NAMESPACES;


static bool _is_valid_setns_ctx_post(struct kernel_function_hook_context_post *ctx)
{
    return (
        kernel_function_hook_context_post_is_valid(ctx)
        && ctx->header->func_num == KERN_F_NUM_SYS_SETNS
        && ctx->header->func_arg->arg_size == sizeof(struct kernel_function_sys_setns_arg)
        && ctx->func_res->res_size == sizeof(struct kernel_function_sys_setns_result)
        && ctx->func_res->success
    );
}

int kernel_function_sys_setns_action_audit_handle_post(
    struct kernel_function_hook_context_post *ctx_post
)
{
    const char *log_id = "kernel_function_sys_setns_action_audit_handle_post";
    int err;
    long target_pid;

    struct msg_namespace msg;

    struct kernel_function_sys_setns_arg *sys_arg;
    struct kernel_function_sys_setns_result *sys_res;

    if (!_is_valid_setns_ctx_post(ctx_post))
        return -EINVAL;

    err = msg_ops_kinit(GLOBAL_MSG_TYPE, &msg.header);
    if (err != 0)
    {
        util_log_debug(log_id, "Failed msg_ops_kinit. Err: %d", err);
        return err;
    }

    sys_arg = (struct kernel_function_sys_setns_arg*)ctx_post->header->func_arg->arg;
    sys_res = (struct kernel_function_sys_setns_result*)ctx_post->func_res->res;

    target_pid = helper_task_task_view_current_pid();

    err = helper_syscall_namespace_populate_msg(
        &msg,
        ctx_post->header->func_num, target_pid, ctx_post->func_res->success,
        NS_OP_SETNS
    );
    if (err != 0)
    {
        util_log_debug(log_id, "Failed helper_syscall_namespace_populate_msg. Err: %d", err);
        return err;
    }

    err = helper_syscall_namespace_log_msg_to_audit(&msg);
    if (err != 0)
    {
        util_log_debug(log_id, "Failed helper_syscall_namespace_log_msg_to_audit. Err: %d", err);
    }

    return err;
}
