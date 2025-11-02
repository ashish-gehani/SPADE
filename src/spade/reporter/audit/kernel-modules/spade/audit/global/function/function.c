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

#include "spade/audit/global/function/function.h"
#include "spade/util/log/log.h"


int global_function_number_is_actionable(bool *dst, struct context_function *ctx, enum kernel_function_number func_num)
{
    if (!dst || !ctx)
        return -EINVAL;

    if (!ctx->network_io)
    {
        switch(func_num)
        {
            case KERN_F_NUM_SYS_RECVFROM:
            case KERN_F_NUM_SYS_RECVMSG:
            case KERN_F_NUM_SYS_SENDMSG:
            case KERN_F_NUM_SYS_SENDTO:
            {
                goto exit_false;
            }
            default:
                break;
        }
    }

    if (!ctx->include_ns_info)
    {
        switch(func_num)
        {
            case KERN_F_NUM_SYS_CLONE:
            case KERN_F_NUM_SYS_FORK:
            case KERN_F_NUM_SYS_SETNS:
            case KERN_F_NUM_SYS_UNSHARE:
            case KERN_F_NUM_SYS_VFORK:
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

int global_function_success_is_actionable(bool *dst, struct context_function *ctx, bool func_success)
{
    if (!dst || !ctx)
        return -EINVAL;

    if (ctx->monitor_function_result == TMFR_ALL)
    {
        // log all. Check other filters.
    }
    else if (ctx->monitor_function_result == TMFR_ONLY_FAILED)
    {
        if (func_success)
        {
            goto exit_false;
        }
    }
    else if (ctx->monitor_function_result == TMFR_ONLY_SUCCESSFUL)
    {
        if (!func_success)
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

int global_function_pre_execution_is_actionable(
    bool *dst,
    struct context_function *ctx,
    enum kernel_function_number func_num
)
{
    int err;

    if (!dst || !ctx)
        return -EINVAL;

    err = global_function_number_is_actionable(dst, ctx, func_num);
    if (err != 0 || !(*dst))
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

int global_function_post_execution_is_actionable(
    bool *dst,
    struct context_function *ctx,
    enum kernel_function_number func_num, bool func_success
)
{
    int err;

    if (!dst || !ctx)
        return -EINVAL;

    err = global_function_number_is_actionable(dst, ctx, func_num);
    if (err != 0 || !(*dst))
    {
        goto exit_false;
    }

    err = global_function_success_is_actionable(dst, ctx, func_success);
    if (err != 0 || !(*dst))
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