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
#include "spade/audit/kernel/helper/task.h"
#include "spade/audit/global/filter.h"
#include "spade/util/log/log.h"


int kernel_function_sys_kill_action_harden_handle_pre(
    const struct kernel_function_hook_context_pre *ctx_pre
)
{
    const char *log_id = "kernel_function_sys_kill_action_harden_handle_pre";
    struct kernel_function_sys_kill_arg *sys_arg;
    uid_t current_euid;
    pid_t pid, tgid;

    if (!kernel_function_sys_kill_hook_context_pre_is_valid(ctx_pre))
    {
        util_log_debug(log_id, "Invalid pre-execution context");
        return -EINVAL;
    }

    sys_arg = (struct kernel_function_sys_kill_arg*)ctx_pre->header->func_arg->arg;
    pid = sys_arg->pid;
    current_euid = kernel_helper_task_host_view_current_euid();

    tgid = kernel_helper_task_task_view_get_tgid(pid);
    if (tgid < 0)
    {
        util_log_debug(log_id, "Failed to get tgid for pid: %d. Err: %d", pid, tgid);
        return (int)tgid;
    }

    if (!global_filter_function_tgid_is_hardened(tgid))
        return 0;

    if (global_filter_function_uid_is_authorized(current_euid))
        return 0;

    util_log_debug(
        log_id,
        "Task is hardened, setting DISALLOW_FUNCTION flag for pid=%d to disallow kill",
        pid
    );
    kernel_function_action_result_set_disallow_function(ctx_pre->header->act_res);

    return 0;
}
