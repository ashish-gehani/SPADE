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

#include "spade/audit/kernel/syscall/hook/setup/ftrace/ftrace.h"
#include "spade/audit/kernel/syscall/hook/setup/ftrace/list.h"


static struct
{
    bool initialized;
} state = {
    .initialized = false,
};

static void _ensure_initialized(void)
{
    if (!state.initialized)
    {
        kernel_syscall_hook_ftrace_list_init();
        state.initialized = true;
    }
}

int kernel_syscall_hook_setup_ftrace_install(void)
{
    _ensure_initialized();
    return fh_install_hooks(KERNEL_SYSCALL_HOOK_FTRACE_LIST, KERNEL_SYSCALL_HOOK_FTRACE_LIST_LEN);
}

int kernel_syscall_hook_setup_ftrace_uninstall(void)
{
    _ensure_initialized();
    fh_remove_hooks(KERNEL_SYSCALL_HOOK_FTRACE_LIST, KERNEL_SYSCALL_HOOK_FTRACE_LIST_LEN);
    return 0;
}