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

#ifndef SPADE_AUDIT_KERNEL_SYSCALL_HOOK_SETUP_TABLE_LIST_H
#define SPADE_AUDIT_KERNEL_SYSCALL_HOOK_SETUP_TABLE_LIST_H

#include <linux/types.h>

#include "spade/audit/kernel/syscall/hook/list.h"


struct table_hook_entry
{
    int num;
    void *hook_func;
    void **orig_func_ptr;
};

extern struct table_hook_entry KERNEL_SYSCALL_HOOK_TABLE_LIST[KERNEL_SYSCALL_HOOK_LIST_LEN];


#define KERNEL_SYSCALL_HOOK_TABLE_LIST_LEN KERNEL_SYSCALL_HOOK_LIST_LEN


void kernel_syscall_hook_table_list_init(void);

#endif // SPADE_AUDIT_KERNEL_SYSCALL_HOOK_SETUP_TABLE_LIST_H
