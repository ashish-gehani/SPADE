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

#include "spade/audit/context/function/function.h"


int context_function_is_initialized(
    bool *dst,
    struct context_function *c
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

int context_function_init(struct context_function *ctx, const struct arg *arg)
{
    if (!ctx || !arg)
        return -EINVAL;

    memcpy(&ctx->m_pids, &arg->monitor_pid, sizeof(arg->monitor_pid));
    memcpy(&ctx->m_ppids, &arg->monitor_ppid, sizeof(arg->monitor_ppid));
    ctx->include_ns_info = arg->include_ns_info;
    ctx->monitor_function_result = arg->monitor_function_result;
    ctx->network_io = arg->network_io;
    memcpy(&ctx->m_uids, &arg->monitor_user, sizeof(arg->monitor_user));
    memcpy(&ctx->harden_pids, &arg->harden_pids, sizeof(arg->harden_pids));
    memcpy(&ctx->harden_ppids, &arg->harden_ppids, sizeof(arg->harden_ppids));

    ctx->initialized = true;

    return 0;
}

int context_function_deinit(struct context_function *c)
{
    if (!c || !c->initialized)
        return -EINVAL;

    // memset(c, 0, sizeof(struct context_function)); // todo uncomment after locking.

    c->initialized = false;

    return 0;
}