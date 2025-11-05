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
#include "spade/audit/kernel/function/sys_clone/action/audit.h"
#include "spade/audit/kernel/function/sys_clone/arg.h"
#include "spade/audit/kernel/function/sys_clone/hook.h"
#include "spade/audit/kernel/function/sys_clone/result.h"
#include "spade/audit/msg/namespace/namespace.h"
#include "spade/audit/msg/ops.h"
#include "spade/audit/kernel/helper/namespace.h"
#include "spade/audit/kernel/helper/audit_log.h"
#include "spade/util/log/log.h"


static const enum msg_common_type GLOBAL_MSG_TYPE = MSG_NAMESPACES;


int kernel_function_sys_clone_action_audit_handle_post(
    const struct kernel_function_hook_context_post *ctx_post
)
{
    const char *log_id = "kernel_function_sys_clone_action_audit_handle_post";
    int err;

    struct msg_namespace msg;

    struct kernel_function_sys_clone_arg *sys_arg;
    struct kernel_function_sys_clone_result *sys_res;

    if (!kernel_function_sys_clone_hook_context_post_is_valid(ctx_post))
        return -EINVAL;

    err = msg_ops_kinit(GLOBAL_MSG_TYPE, &msg.header);
    if (err != 0)
    {
        util_log_debug(log_id, "Failed msg_ops_kinit. Err: %d", err);
        return err;
    }

    sys_arg = (struct kernel_function_sys_clone_arg*)ctx_post->header->func_arg->arg;
    sys_res = (struct kernel_function_sys_clone_result*)ctx_post->func_res->res;

    err = kernel_helper_namespace_populate_msg(
        &msg,
        ctx_post->header->func_num, sys_res->ret, ctx_post->func_res->success,
        NS_OP_NEW_PROCESS
    );
    if (err != 0)
    {
        util_log_debug(log_id, "Failed kernel_helper_namespace_populate_msg. Err: %d", err);
        return err;
    }

    err = kernel_helper_namespace_log_msg_to_audit(&msg);
    if (err != 0)
    {
        util_log_debug(log_id, "Failed kernel_helper_namespace_log_msg_to_audit. Err: %d", err);
    }

    return err;
}
