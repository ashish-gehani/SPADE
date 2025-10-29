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

#include "spade/audit/kernel/function/action/audit/setns/setns.h"
#include "spade/audit/kernel/function/arg/setns.h"
#include "spade/audit/msg/namespace/namespace.h"
#include "spade/audit/msg/ops.h"
#include "spade/audit/helper/syscall/namespace.h"
#include "spade/audit/helper/audit_log.h"


static const enum msg_common_type GLOBAL_MSG_TYPE = MSG_NAMESPACES;


static bool _is_valid_function_ctx(struct kernel_function_context_post *sys_ctx)
{
    return (
        sys_ctx && sys_ctx->header.type == KERNEL_FUNCTION_CONTEXT_TYPE_POST && sys_ctx->header.sys_num == __NR_setns
        && sys_ctx->header.sys_arg.arg != NULL && sys_ctx->header.sys_arg.arg_size == sizeof(struct kernel_function_arg_setns)
        && sys_ctx->sys_res.success
    );
}

int kernel_function_action_audit_setns_handle(struct kernel_function_context_post *sys_ctx)
{
    int err;

    struct msg_namespace msg;

    if (!_is_valid_function_ctx(sys_ctx))
        return -EINVAL;

    err = msg_ops_kinit(GLOBAL_MSG_TYPE, &msg.header);
    if (err != 0)
        return err;

    err = helper_syscall_namespace_populate_msg(
        &msg, sys_ctx, NS_OP_SETNS
    );
    if (err != 0)
        return err;

    err = helper_syscall_namespace_log_msg_to_audit(&msg);

    return err;
}