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


int global_syscall_is_loggable_by_sys_num(bool *dst, struct context_syscall *ctx, int sys_num)
{
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

    goto exit_true;

exit_false:
    *dst = false;
    return 0;

exit_true:
    *dst = true;
    return 0;
}

int global_syscall_is_loggable_by_sys_success(bool *dst, struct context_syscall *ctx, bool sys_success)
{
    if (!dst || !ctx)
        return -EINVAL;

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

    goto exit_true;

exit_false:
    *dst = false;
    return 0;

exit_true:
    *dst = true;
    return 0;
}

int global_syscall_is_loggable_by_uid(bool *dst, struct context_syscall *ctx, uid_t uid)
{
    bool uid_is_loggable;

    if (!dst || !ctx)
        return -EINVAL;

    uid_is_loggable = global_common_is_uid_loggable(&ctx->user, uid);
    if (!uid_is_loggable)
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

int global_syscall_is_loggable_by_pid(bool *dst, struct context_syscall *ctx, pid_t pid)
{
    bool pid_is_in_pid_array;

    if (!dst || !ctx)
        return -EINVAL;

    pid_is_in_pid_array = global_common_is_pid_in_array(
        &(ctx->ignore_pids.arr[0]), ctx->ignore_pids.len,
        pid
    );

    if (pid_is_in_pid_array)
        goto exit_false;

    goto exit_true;

exit_false:
    *dst = false;
    return 0;

exit_true:
    *dst = true;
    return 0;
}

int global_syscall_is_loggable_by_ppid(bool *dst, struct context_syscall *ctx, pid_t ppid)
{
    bool ppid_is_in_ppid_array;

    if (!dst || !ctx)
        return -EINVAL;

    ppid_is_in_ppid_array = global_common_is_pid_in_array(
        &(ctx->ignore_ppids.arr[0]), ctx->ignore_ppids.len,
        ppid
    );

    if (ppid_is_in_ppid_array)
        goto exit_false;

    goto exit_true;

exit_false:
    *dst = false;
    return 0;

exit_true:
    *dst = true;
    return 0;
}

int global_syscall_is_loggable(
    bool *dst,
    struct context_syscall *ctx,
    int sys_num, bool sys_success,
    pid_t pid, pid_t ppid, uid_t uid
)
{
    int err;

    if (!dst || !ctx)
        return -EINVAL;

    err = global_syscall_is_loggable_by_sys_num(dst, ctx, sys_num);
    if (err != 0 || !(*dst))
        goto exit_false;

    err = global_syscall_is_loggable_by_sys_success(dst, ctx, sys_success);
    if (err != 0 || !(*dst))
        goto exit_false;

    err = global_syscall_is_loggable_by_uid(dst, ctx, uid);
    if (err != 0 || !(*dst))
        goto exit_false;

    err = global_syscall_is_loggable_by_pid(dst, ctx, pid);
    if (err != 0 || !(*dst))
        goto exit_false;

    err = global_syscall_is_loggable_by_ppid(dst, ctx, ppid);
    if (err != 0 || !(*dst))
        goto exit_false;

    goto exit_true;

exit_false:
    *dst = false;
    return 0;

exit_true:
    *dst = true;
    return 0;
}