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

#include <linux/slab.h>
#include <linux/errno.h>

#include "spade/audit/kernel/syscall/hook/setup/ftrace/list.h"
#include "spade/audit/kernel/syscall/hook/setup/ftrace/ftrace_helper.h"


struct ftrace_hook KERNEL_SYSCALL_HOOK_FTRACE_LIST[KERNEL_SYSCALL_HOOK_LIST_LEN];

static char* syscall_names[KERNEL_SYSCALL_HOOK_LIST_LEN];


static int _setup_syscall_name(int index, const char *name)
{
    size_t name_size = 32;

    if (!name)
        return -EINVAL;

    syscall_names[index] = kmalloc(name_size, GFP_KERNEL);
    if (!syscall_names[index])
        return -ENOMEM;

#ifdef PTREGS_SYSCALL_STUBS
    snprintf(syscall_names[index], name_size, "__x64_sys_%s", name);
#else
    snprintf(syscall_names[index], name_size, "sys_%s", name);
#endif

    KERNEL_SYSCALL_HOOK_FTRACE_LIST[index].name = syscall_names[index];
    return 0;
}


void kernel_syscall_hook_ftrace_list_init(void)
{
    int i;
    for (i = 0; i < KERNEL_SYSCALL_HOOK_LIST_LEN; i++)
    {
        const struct kernel_syscall_hook *hook = &KERNEL_SYSCALL_HOOK_LIST[i];
        const char *name;
        int err;

        if (!hook)
            continue;
        if (!hook->get_name || !hook->get_hook_func || !hook->get_orig_func_ptr)
            continue;

        name = hook->get_name();
        err = _setup_syscall_name(i, name);
        if (err != 0)
            continue;

        KERNEL_SYSCALL_HOOK_FTRACE_LIST[i].function = hook->get_hook_func();
        KERNEL_SYSCALL_HOOK_FTRACE_LIST[i].original = hook->get_orig_func_ptr();
    }
}
