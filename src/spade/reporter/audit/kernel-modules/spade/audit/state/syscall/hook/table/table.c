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

#include "spade/audit/state/syscall/hook/table/table.h"
#include "spade/util/log/log.h"
#include "spade/audit/kernel/syscall/hook/setup/table/table.h"


int state_syscall_hook_table_is_initialized(
    bool *dst,
    struct state_syscall_hook_table *s
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


int state_syscall_hook_table_init(
    struct state_syscall_hook_table *s
)
{
    const char *log_id = "state_syscall_hook_table_init";
    int err;

    if (!s)
        return -EINVAL;

    if (s->initialized)
        return -EALREADY;

    util_log_debug(log_id, "Initing sys call table hooks");
    err = kernel_syscall_hook_setup_table_install();
    if (err != 0)
    {
        util_log_debug(log_id, "Initing sys call table hooks. Failed. Err: %d", err);
        return err;
    } else
    {
        util_log_debug(log_id, "Initing sys call table hooks. Success");
    }

    s->initialized = true;

    return 0;
}

int state_syscall_hook_table_deinit(
    struct state_syscall_hook_table *s
)
{
    const char *log_id = "state_syscall_hook_table_deinit";
    int err;

    if (!s || !s->initialized)
        return -EINVAL;

    util_log_debug(log_id, "Deiniting sys call table hooks");
    err = kernel_syscall_hook_setup_table_uninstall();
    s->initialized = false;

    if (err != 0)
    {
        util_log_debug(log_id, "Deiniting sys call table hooks. Failed. Err: %d", err);
    } else
    {
        util_log_debug(log_id, "Deiniting sys call table hooks. Success");
    }
    
    return err;
}