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

#include "spade/audit/state/syscall/hook/ftrace/ftrace.h"
#include "spade/util/log/log.h"
#include "spade/audit/kernel/syscall/hook/setup/ftrace/ftrace.h"


int state_syscall_hook_ftrace_is_initialized(
    bool *dst,
    struct state_syscall_hook_ftrace *s
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


int state_syscall_hook_ftrace_init(
    struct state_syscall_hook_ftrace *s, bool dry_run
)
{
    const char *log_id = "state_syscall_hook_ftrace_init";
    int err;

    if (!s)
        return -EINVAL;

    if (s->initialized)
        return -EALREADY;

    if (!dry_run)
    {
        util_log_debug(log_id, "Initing ftrace hooks");
        err = kernel_syscall_hook_setup_ftrace_install();
        if (err != 0)
        {
            util_log_debug(log_id, "Initing ftrace hooks. Failed. Err: %d", err);
            return err;
        } else
        {
            util_log_debug(log_id, "Initing ftrace hooks. Success");
        }
    }

    s->initialized = true;
    s->dry_run = dry_run;

    return 0;
}

int state_syscall_hook_ftrace_deinit(
    struct state_syscall_hook_ftrace *s
)
{
    const char *log_id = "state_syscall_hook_ftrace_deinit";
    int err;

    if (!s || !s->initialized)
        return -EINVAL;

    if (!s->dry_run)
    {
        util_log_debug(log_id, "Deiniting ftrace hooks");
        err = kernel_syscall_hook_setup_ftrace_uninstall();
        if (err != 0)
        {
            util_log_debug(log_id, "Deiniting ftrace hooks. Failed. Err: %d", err);
        } else
        {
            util_log_debug(log_id, "Deiniting ftrace hooks. Success");
        }
    }

    s->initialized = false;
    s->dry_run = false;

    return err;
}