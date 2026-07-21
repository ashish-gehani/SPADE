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


#include <linux/atomic.h>
#include <linux/errno.h>
#include <linux/kernel.h>
#include <linux/version.h>
#include <linux/string.h>

#include "audit/state/state.h"
#include "audit/util/log/log.h"


int state_is_initialized(
    bool *dst,
    struct state *s
)
{
    if (!dst || !s)
        return -EINVAL;

    if (!s->initialized)
    {
        *dst = false;
        return 0;
    }

    *dst = true;
    return 0;
}

int state_init(struct state *s, bool dry_run)
{
    int err;
    bool function_is_inited = false;
    bool namespace_is_inited = false;
    bool netfilter_is_inited = false;

    if (!s)
        return -EINVAL;

    if (s->initialized)
        return -EALREADY;

    err = state_function_is_initialized(&function_is_inited, &s->function);
    if (err != 0)
        return err;

    if (!function_is_inited)
    {
        err = state_function_init(&s->function, dry_run);
        if (err != 0)
            return err;
    }

    err = state_namespace_is_initialized(&namespace_is_inited, &s->namespace);
    if (err != 0)
        return err;

    if (!namespace_is_inited)
    {
        err = state_namespace_init(&s->namespace, dry_run);
        if (err != 0)
            return err;
    }

    err = state_netfilter_is_initialized(&netfilter_is_inited, &s->netfilter);
    if (err != 0)
        return err;

    if (!netfilter_is_inited)
    {
        err = state_netfilter_init(&s->netfilter, dry_run);
        if (err != 0)
            return err;
    }

    s->initialized = true;
    s->dry_run = dry_run;

    return 0;
}

int state_deinit(struct state *s)
{
    int nf_err;
    int ns_err;
    int func_err;

    if (!s || !s->initialized)
        return -EINVAL;

    nf_err = state_netfilter_deinit(&s->netfilter);
    ns_err = state_namespace_deinit(&s->namespace);
    func_err = state_function_deinit(&s->function);

    s->initialized = false;
    s->dry_run = false;

    if (nf_err != 0)
        return nf_err;

    if (ns_err != 0)
        return ns_err;

    if (func_err != 0)
        return func_err;

    return 0;
}