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
#include <linux/string.h>

#include "spade/audit/config/config.h"
#include "spade/audit/context/syscall/syscall.h"
#include "spade/util/log/log.h"


int context_syscall_is_initialized(
    bool *dst,
    struct context_syscall *c
)
{
    if (!dst || !c)
        return -EINVAL;

    if (!c->initialized)
    {
        *dst = false;
        return 0;
    }

    *dst = true;
    return 0;
}

int context_syscall_init(struct context_syscall *ctx, struct arg *arg)
{
    const char *log_id = "context_syscall_init";

    if (!ctx || !arg)
        return -EINVAL;

    if (!CONFIG_GLOBAL.debug)
    {
        if (arg->monitor_syscalls != AMMS_ONLY_SUCCESSFUL)
        {
            util_log_warn(
                log_id, 
                "Monitoring of only successful syscalls is allowed in non-debug mode. Err: %d", 
                -ENOTSUPP
            );
            return -ENOTSUPP;
        }
    }

    memcpy(&ctx->ignore_pids, &arg->ignore_pids, sizeof(arg->ignore_pids));
    memcpy(&ctx->ignore_ppids, &arg->ignore_ppids, sizeof(arg->ignore_ppids));
    ctx->include_ns_info = arg->include_ns_info;
    ctx->monitor_syscalls = arg->monitor_syscalls;
    ctx->network_io = arg->network_io;
    memcpy(&ctx->user, &arg->user, sizeof(arg->user));

    ctx->initialized = true;

    return 0;
}

int context_syscall_deinit(struct context_syscall *c)
{
    if (!c || !c->initialized)
        return -EINVAL;

    memset(c, 0, sizeof(struct context_syscall));

    c->initialized = false;

    return 0;
}