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

#include "spade/audit/kernel/syscall/action/audit/kill/kill.h"
#include "spade/audit/kernel/syscall/arg/kill.h"
#include "spade/audit/msg/ubsi/ubsi.h"
#include "spade/audit/msg/ops.h"
#include "spade/audit/helper/audit_log.h"
#include "spade/audit/helper/task.h"
#include "spade/util/log/log.h"


static const enum msg_common_type GLOBAL_MSG_TYPE = MSG_UBSI;


static bool _is_valid_sys_ctx(struct kernel_syscall_context_post *sys_ctx)
{
    return (
        sys_ctx && sys_ctx->header.type == SYSCALL_CONTEXT_TYPE_POST && sys_ctx->header.sys_num == __NR_kill
        && sys_ctx->header.sys_arg.arg != NULL && sys_ctx->header.sys_arg.arg_size == sizeof(struct kernel_syscall_arg_kill)
        && sys_ctx->sys_res.success
    );
}

int kernel_syscall_action_audit_kill_handle(struct kernel_syscall_context_post *sys_ctx)
{
    char *log_id = "kernel_syscall_action_audit_kill_handle";
    int err;

    struct msg_ubsi msg;
    struct kernel_syscall_arg_kill *sys_arg;

    if (!_is_valid_sys_ctx(sys_ctx))
        return -EINVAL;

    err = msg_ops_kinit(GLOBAL_MSG_TYPE, &msg.header);
    if (err != 0)
        return err;

    err = helper_task_populate_process_info_from_current_task(&msg.proc_info);
    if (err != 0)
    {
        util_log_warn(log_id, "Failed to copy current process info");
        return err;
    }

    sys_arg = (struct kernel_syscall_arg_kill*)sys_ctx->header.sys_arg.arg;

    msg.signal = sys_arg->sig;
    msg.syscall_number = sys_ctx->header.sys_num;
    msg.syscall_result = sys_ctx->sys_res.ret;
    msg.syscall_success = sys_ctx->sys_res.success;
    msg.target_pid = sys_arg->pid;

    err = helper_audit_log(NULL, &msg.header);

    return err;
}