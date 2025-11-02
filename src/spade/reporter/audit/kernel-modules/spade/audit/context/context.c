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
    int err = 0;
    bool function_is_inited = false;
    bool netfilter_is_inited = false;

    if (!c)
    {
        err = -EINVAL;
        goto exit;
    }

    if (c->initialized)
    {
        err = -EALREADY;
        goto exit;
    }

    err = context_function_is_initialized(&function_is_inited, &c->function);
    if (err != 0)
    {
        goto exit;
    }

    if (!function_is_inited)
    {
        err = context_function_init(&c->function, arg);
        if (err != 0)
        {
            goto exit_deinit_all;
        }
    }

    err = context_netfilter_is_initialized(&netfilter_is_inited, &c->netfilter);
    if (err != 0)
    {
        goto exit_deinit_all;
    }

    if (!netfilter_is_inited)
    {
        err = context_netfilter_init(&c->netfilter, arg);
        if (err != 0)
        {
            goto exit_deinit_all;
        }
    }

    c->initialized = true;
    err = 0;
    goto exit;

exit_deinit_all:
    context_function_deinit(&c->function);
    context_netfilter_deinit(&c->netfilter);
    return err;

exit:
    return err;
}

int context_deinit(struct context *c)
{
    int nf_err;
    int func_err;

    if (!c || !c->initialized)
        return -EINVAL;

    nf_err = context_netfilter_deinit(&c->netfilter);
    func_err = context_function_deinit(&c->function);

    c->initialized = false;

    if (nf_err != 0)
        return nf_err;

    if (func_err != 0)
        return func_err;

    return 0;
}