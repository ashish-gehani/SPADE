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

#include "spade/audit/context/netfilter/netfilter.h"


int context_netfilter_is_initialized(
    bool *dst,
    struct context_netfilter *c
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

int context_netfilter_init(struct context_netfilter *ctx, struct arg *arg)
{
    if (!ctx || !arg)
        return -EINVAL;

    ctx->audit_hooks = arg->nf.audit_hooks;
    ctx->include_ns_info = arg->include_ns_info;
    ctx->monitor_ct = arg->nf.monitor_ct;
    ctx->use_user = arg->nf.use_user;
    memcpy(&ctx->m_user, &arg->monitor_user, sizeof(arg->monitor_user));

    ctx->initialized = true;

    return 0;
}

int context_netfilter_deinit(struct context_netfilter *c)
{
    if (!c || !c->initialized)
        return -EINVAL;

    memset(c, 0, sizeof(struct context_netfilter));

    c->initialized = false;

    return 0;
}