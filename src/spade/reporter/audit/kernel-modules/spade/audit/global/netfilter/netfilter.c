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

#include "spade/audit/global/process/process.h"
#include "spade/audit/global/netfilter/netfilter.h"


int global_netfilter_uid_is_actionable(
    bool *dst,
    struct context_netfilter *ctx,
    uid_t uid
)
{
    if (!dst || !ctx)
        return -EINVAL;

    if (!ctx->use_user)
        *dst = true;
    else
        *dst = global_process_uid_is_actionable(&ctx->m_user, uid);

    return 0;
}

int global_netfilter_conntrack_info_is_actionable(
    bool *dst,
    struct context_netfilter *ctx,
    enum ip_conntrack_info ct_info
)
{
    if (!dst || !ctx)
        return -EINVAL;

    switch (ctx->monitor_ct)
    {
        case TMC_ALL:
            goto return_true;
        case TMC_ONLY_NEW: 
            if (ct_info == IP_CT_NEW)
                goto return_true;
            goto return_false;
        default:
            goto return_false;
    }

return_true:
    *dst = true;
    return 0;

return_false:
    *dst = false;
    return 0;
}