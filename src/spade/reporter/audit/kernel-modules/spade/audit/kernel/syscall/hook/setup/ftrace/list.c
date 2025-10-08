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

#include "spade/audit/kernel/syscall/hook/setup/ftrace/list.h"


struct ftrace_hook KERNEL_SYSCALL_HOOK_FTRACE_LIST[KERNEL_SYSCALL_HOOK_LIST_LEN];


void kernel_syscall_hook_ftrace_list_init(void)
{
    int i;
    for (i = 0; i < KERNEL_SYSCALL_HOOK_LIST_LEN; i++)
    {
        const struct kernel_syscall_hook *hook = &KERNEL_SYSCALL_HOOK_LIST[i];
        if (!hook)
            continue;
        if (!hook->get_name || !hook->get_hook_func || !hook->get_orig_func_ptr)
            continue;
        KERNEL_SYSCALL_HOOK_FTRACE_LIST[i].name = hook->get_name();
        KERNEL_SYSCALL_HOOK_FTRACE_LIST[i].function = hook->get_hook_func();
        KERNEL_SYSCALL_HOOK_FTRACE_LIST[i].original = hook->get_orig_func_ptr();
    }
}
