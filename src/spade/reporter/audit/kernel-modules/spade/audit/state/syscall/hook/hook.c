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

#include "spade/audit/state/syscall/hook/hook.h"
#include "spade/util/log/log.h"


int state_syscall_hook_is_initialized(
    bool *dst,
    struct state_syscall_hook *s
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


int state_syscall_hook_init(
    struct state_syscall_hook *s, bool dry_run
)
{
    int err;

    if (!s)
        return -EINVAL;

    if (s->initialized)
        return -EALREADY;

    err = state_syscall_hook_ftrace_init(&s->ftrace, dry_run);

    if (err != 0)
    {
        return err;
    }

    s->initialized = true;
    s->dry_run = dry_run;

    return 0;
}

int state_syscall_hook_deinit(
    struct state_syscall_hook *s
)
{
    int err;

    if (!s || !s->initialized)
        return -EINVAL;

    err = state_syscall_hook_ftrace_deinit(&s->ftrace);

    s->initialized = false;
    s->dry_run = false;

    return err;
}