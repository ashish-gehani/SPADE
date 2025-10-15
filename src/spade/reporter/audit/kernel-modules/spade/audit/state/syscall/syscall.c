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

#include "spade/audit/state/syscall/syscall.h"
#include "spade/audit/helper/kernel.h"
#include "spade/util/log/log.h"


int state_syscall_is_initialized(
    bool *dst,
    struct state_syscall *s
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

int state_syscall_init(
    struct state_syscall *s, bool dry_run
)
{
    const char *log_id = "state_syscall_init";
    int err;
    bool namespace_is_inited = false;
    bool syscall_is_inited = false;

    if (!s)
        return -EINVAL;

    if (s->initialized)
        return -EALREADY;

    err = state_syscall_namespace_is_initialized(&namespace_is_inited, &s->ns);
    if (err != 0)
        return err;

    if (!namespace_is_inited)
    {
        util_log_debug(log_id, "Initing syscall namespace state");
        err = state_syscall_namespace_init(&s->ns, dry_run);
        if (err != 0)
        {
            util_log_debug(log_id, "Initing syscall namespace state. Failed. Err: %d", err);
            return err;
        } else
        {
            util_log_debug(log_id, "Initing syscall namespace state. Success");
        }
    }

    err = state_syscall_hook_is_initialized(&syscall_is_inited, &s->hook);
    if (err != 0)
        return err;

    if (!syscall_is_inited)
    {
        util_log_debug(log_id, "Initing syscall hook state");
        err = state_syscall_hook_init(&s->hook, dry_run);
        if (err != 0)
        {
            util_log_debug(log_id, "Initing syscall hook state. Failed. Err: %d", err);
            return err;
        } else
        {
            util_log_debug(log_id, "Initing syscall hook state. Success");
        }
    }

    s->initialized = true;
    s->dry_run = dry_run;

    return 0;
}

int state_syscall_deinit(struct state_syscall *s)
{
    int ns_err;
    int sys_err;

    if (!s || !s->initialized)
        return -EINVAL;

    ns_err = state_syscall_namespace_deinit(&s->ns);
    sys_err = state_syscall_hook_deinit(&s->hook);

    s->initialized = false;
    s->dry_run = false;

    if (ns_err != 0)
        return ns_err;

    if (sys_err != 0)
        return sys_err;

    return 0;
}