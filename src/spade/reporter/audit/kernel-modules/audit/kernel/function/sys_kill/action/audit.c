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

#include "audit/kernel/function/number.h"
#include "audit/kernel/function/hook.h"
#include "audit/kernel/function/sys_kill/action/audit.h"
#include "audit/kernel/function/sys_kill/arg.h"
#include "audit/kernel/function/sys_kill/result.h"
#include "audit/kernel/function/sys_kill/hook.h"
#include "audit/msg/ubsi/ubsi.h"
#include "audit/msg/ops.h"
#include "audit/kernel/helper/audit_log.h"
#include "audit/kernel/helper/task.h"
#include "audit/util/log/log.h"


static const enum msg_common_type GLOBAL_MSG_TYPE = MSG_UBSI;


int kernel_function_sys_kill_action_audit_handle_post(
    const struct kernel_function_hook_context_post *ctx_post
)
{
    const char *log_id = "kernel_function_sys_kill_action_audit_handle_post";
    int err;

    struct msg_ubsi msg;

    struct kernel_function_sys_kill_arg *sys_arg;
    struct kernel_function_sys_kill_result *sys_res;

    int sys_num;
    enum kernel_function_number func_num = ctx_post->header->func_num;
    bool sys_num_default_to_func_num = false;

    if (!kernel_function_sys_kill_hook_context_post_is_valid(ctx_post))
        return -EINVAL;

    err = msg_ops_kinit(GLOBAL_MSG_TYPE, &msg.header);
    if (err != 0)
    {
        util_log_debug(log_id, "Failed msg_ops_kinit. Err: %d", err);
        return err;
    }

    err = kernel_helper_task_populate_process_info_from_current_task(&msg.proc_info);
    if (err != 0)
    {
        util_log_debug(log_id, "Failed to copy current process info");
        return err;
    }

    err = kernel_function_number_to_system_call_number(
        &sys_num, func_num, sys_num_default_to_func_num
    );
    if (err != 0)
    {
        util_log_debug(log_id, "Failed to get syscall number from function number: %d. Err: %d", func_num, err);
        return err;
    }

    sys_arg = (struct kernel_function_sys_kill_arg*)ctx_post->header->func_arg->arg;
    sys_res = (struct kernel_function_sys_kill_result*)ctx_post->func_res->res;

    msg.signal = sys_arg->sig;
    msg.syscall_number = sys_num;
    msg.syscall_result = sys_res->ret;
    msg.syscall_success = ctx_post->func_res->success;
    msg.target_pid = sys_arg->pid;

    err = kernel_helper_audit_log(NULL, &msg.header);

    return err;
}
