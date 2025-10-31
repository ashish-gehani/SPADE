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

#include "spade/audit/state/netfilter/netfilter.h"
#include "spade/audit/kernel/setup/netfilter/netfilter.h"


int state_netfilter_is_initialized(
    bool *dst,
    struct state_netfilter *s
)
{
    if (!dst || !s)
        return -EINVAL;

    *dst = s->initialized;
    return 0;
}

int state_netfilter_init(
    struct state_netfilter *s, bool dry_run
)
{
    int err;

    if (!s)
        return -EINVAL;

    if (s->initialized)
        return -EALREADY;

    if (!dry_run)
    {
        err = kernel_setup_netfilter_do();
        if (err != 0)
            return err;
    }

    s->initialized = true;
    s->discarded_events_count = 0;
    s->dry_run = dry_run;

    return 0;
}

int state_netfilter_deinit(struct state_netfilter *s)
{
    int err;

    if (!s || !s->initialized)
        return -EINVAL;

    if (!s->dry_run)
    {
        err = kernel_setup_netfilter_undo();
    }

    s->initialized = false;
    s->dry_run = false;

    return err;
}
