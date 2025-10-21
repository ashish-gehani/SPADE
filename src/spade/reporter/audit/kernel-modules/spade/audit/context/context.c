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
#include <linux/kernel.h>
#include <linux/version.h>
#include <linux/string.h>

#include "spade/audit/context/context.h"
#include "spade/util/log/log.h"


int context_is_initialized(
    bool *dst,
    struct context *c
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

int context_init(struct context *c, const struct arg *arg)
{
    int err;
    bool syscall_is_inited = false;
    bool netfilter_is_inited = false;

    if (!c)
        return -EINVAL;

    if (c->initialized)
        return -EALREADY;

    err = context_syscall_is_initialized(&syscall_is_inited, &c->syscall);
    if (err != 0)
        return err;

    if (!syscall_is_inited)
    {
        err = context_syscall_init(&c->syscall, arg);
        if (err != 0)
            return err;
    }

    err = context_netfilter_is_initialized(&netfilter_is_inited, &c->netfilter);
    if (err != 0)
        return err;

    if (!netfilter_is_inited)
    {
        err = context_netfilter_init(&c->netfilter, arg);
        if (err != 0)
            return err;
    }

    c->initialized = true;

    return 0;
}

int context_deinit(struct context *c)
{
    int nf_err;
    int sys_err;

    if (!c || !c->initialized)
        return -EINVAL;

    nf_err = context_netfilter_deinit(&c->netfilter);
    sys_err = context_syscall_deinit(&c->syscall);

    c->initialized = false;

    if (nf_err != 0)
        return nf_err;

    if (sys_err != 0)
        return sys_err;

    return 0;
}