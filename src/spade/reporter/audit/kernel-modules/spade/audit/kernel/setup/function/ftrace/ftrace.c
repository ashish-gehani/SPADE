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

#include "spade/audit/kernel/setup/function/ftrace/ftrace.h"
#include "spade/audit/kernel/setup/function/ftrace/ftrace_helper.h"
#include "spade/audit/kernel/function/op.h"
#include "spade/audit/util/log/log.h"


static size_t ftrace_hooks_len;
static struct ftrace_hook ftrace_hooks[KERNEL_FUNCTION_OP_LIST_MAX_LEN];

static struct
{
    bool initialized;
} state = {
    .initialized = false,
};


static void _init_ftrace_hooks(void)
{
    const char *log_id = "_init_ftrace_hooks";
    int i;
    for (i = 0; i < KERNEL_FUNCTION_OP_LIST_LEN; i++)
    {
        const struct kernel_function_op *op = KERNEL_FUNCTION_OP_LIST[i];
        const struct kernel_function_hook *hook;

        if (!op)
            break;

        hook = op->hook;

        if (!hook || !hook->get_name || !hook->get_hook_func || !hook->get_orig_func_ptr)
            continue;

        ftrace_hooks[i].name = hook->get_name();
        ftrace_hooks[i].function = hook->get_hook_func();
        ftrace_hooks[i].original = hook->get_orig_func_ptr();

        util_log_debug(log_id, "Inited ftrace_hook struct {name=%s} at index %d", ftrace_hooks[i].name, i);

        ftrace_hooks_len++;
    }
}

static void _ensure_initialized(void)
{
    if (!state.initialized)
    {
        _init_ftrace_hooks();
        state.initialized = true;
    }
}

int kernel_setup_function_ftrace_install(void)
{
    _ensure_initialized();
    return fh_install_hooks(ftrace_hooks, ftrace_hooks_len);
}

int kernel_setup_function_ftrace_uninstall(void)
{
    _ensure_initialized();
    fh_remove_hooks(ftrace_hooks, ftrace_hooks_len);
    return 0;
}
