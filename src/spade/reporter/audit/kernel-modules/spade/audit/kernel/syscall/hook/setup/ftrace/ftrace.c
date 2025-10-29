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

#include "spade/audit/kernel/syscall/hook/setup/ftrace/ftrace.h"
#include "spade/audit/kernel/syscall/hook/setup/ftrace/ftrace_helper.h"

static size_t ftrace_hooks_len;
static struct ftrace_hook ftrace_hooks[KERNEL_SYSCALL_HOOK_LIST_LEN_MAX];

static struct
{
    bool initialized;
} state = {
    .initialized = false,
};


static void _init_ftrace_hooks(void)
{
    int i;
    for (i = 0; i < KERNEL_SYSCALL_HOOK_LIST_LEN_MAX; i++)
    {
        const struct kernel_syscall_hook *hook = KERNEL_SYSCALL_HOOK_LIST[i];

        if (!hook)
            break;
        if (!hook->get_name || !hook->get_hook_func || !hook->get_orig_func_ptr)
            continue;

        ftrace_hooks[i].name = hook->get_name();
        ftrace_hooks[i].function = hook->get_hook_func();
        ftrace_hooks[i].original = hook->get_orig_func_ptr();
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

int kernel_syscall_hook_setup_ftrace_install(void)
{
    _ensure_initialized();
    return fh_install_hooks(ftrace_hooks, ftrace_hooks_len);
}

int kernel_syscall_hook_setup_ftrace_uninstall(void)
{
    _ensure_initialized();
    fh_remove_hooks(ftrace_hooks, ftrace_hooks_len);
    return 0;
}
