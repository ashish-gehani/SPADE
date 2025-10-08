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

#include <linux/kernel.h>
#include <linux/types.h>
#include <linux/errno.h>
#include <asm/syscall.h>

#include "spade/audit/global/common/common.h"
#include "spade/audit/global/syscall/syscall.h"


int global_syscall_is_loggable(
    bool *dst,
    struct context_syscall *ctx,
    int sys_num, bool sys_success,
    pid_t pid, pid_t ppid, uid_t uid
)
{
    bool uid_is_loggable, pid_is_in_pid_array, ppid_is_in_ppid_array;

    if (!dst || !ctx)
        return -EINVAL;

    if (!ctx->network_io)
    {
        switch(sys_num)
        {
            case __NR_recvfrom:
            case __NR_recvmsg:
            case __NR_sendmsg:
            case __NR_sendto:
            {
                goto exit_false;
            }
            default:
                break;
        }
    }

    if (!ctx->include_ns_info)
    {
        switch(sys_num)
        {
            case __NR_clone:
            case __NR_fork:
            case __NR_setns:
            case __NR_unshare:
            case __NR_vfork:
            {
                goto exit_false;
            }
            default:
                break;
        }
    }

    if (ctx->monitor_syscalls == AMMS_ALL)
    {
        // log all. Check other filters.
    }
    else if (ctx->monitor_syscalls == AMMS_ONLY_FAILED)
    {
        if (sys_success)
        {
            goto exit_false;
        }
    }
    else if (ctx->monitor_syscalls == AMMS_ONLY_SUCCESSFUL)
    {
        if (!sys_success)
        {
            goto exit_false;
        }
    }

    uid_is_loggable = global_common_is_uid_loggable(&ctx->user, uid);
    if (!uid_is_loggable)
    {
        goto exit_false;
    }

    pid_is_in_pid_array = global_common_is_pid_in_array(
        &(ctx->ignore_pids.arr[0]), ctx->ignore_pids.len,
        pid
    );

    if (pid_is_in_pid_array)
    {
        goto exit_false;
    }

    ppid_is_in_ppid_array = global_common_is_pid_in_array(
        &(ctx->ignore_ppids.arr[0]), ctx->ignore_ppids.len,
        ppid
    );

    if (ppid_is_in_ppid_array)
    {
        goto exit_false;
    }

    goto exit_true;

exit_false:
    *dst = false;
    return 0;

exit_true:
    *dst = true;
    return 0;
}