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

#include "spade/audit/kernel/function/action.h"
#include "spade/audit/kernel/function/hook.h"
#include "spade/audit/kernel/function/sys_kill/action/harden.h"
#include "spade/audit/kernel/function/sys_kill/hook.h"
#include "spade/audit/kernel/function/sys_kill/arg.h"
#include "spade/audit/global/filter.h"
#include "spade/util/log/log.h"


int kernel_function_sys_kill_action_harden_handle_pre(
    const struct kernel_function_hook_context_pre *ctx_pre
)
{
    const char *log_id = "kernel_function_sys_kill_action_harden_handle_pre";
    pid_t pid;
    struct kernel_function_sys_kill_arg *sys_arg;

    if (!kernel_function_sys_kill_hook_context_pre_is_valid(ctx_pre))
    {
        util_log_debug(log_id, "Invalid pre-execution context");
        return -EINVAL;
    }

    sys_arg = (struct kernel_function_sys_kill_arg*)ctx_pre->header->func_arg->arg;
    pid = sys_arg->pid;

    if (global_filter_function_pid_is_hardened(sys_arg->pid))
    {
        util_log_debug(
            log_id,
            "Task is hardened, setting DISALLOW_FUNCTION flag for pid=%d to disallow kill",
            pid
        );
        kernel_function_action_result_set_disallow_function(ctx_pre->header->act_res);
    }

    return 0;
}
