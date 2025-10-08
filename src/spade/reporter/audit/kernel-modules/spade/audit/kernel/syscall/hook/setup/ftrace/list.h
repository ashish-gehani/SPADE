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

#ifndef SPADE_AUDIT_KERNEL_SYSCALL_HOOK_SETUP_FTRACE_LIST_H
#define SPADE_AUDIT_KERNEL_SYSCALL_HOOK_SETUP_FTRACE_LIST_H

#include <linux/types.h>

#include "spade/audit/kernel/syscall/hook/list.h"
#include "spade/audit/kernel/syscall/hook/setup/ftrace/ftrace.h"


extern struct ftrace_hook KERNEL_SYSCALL_HOOK_FTRACE_LIST[KERNEL_SYSCALL_HOOK_LIST_LEN];


#define KERNEL_SYSCALL_HOOK_FTRACE_LIST_LEN KERNEL_SYSCALL_HOOK_LIST_LEN


void kernel_syscall_hook_ftrace_list_init(void);

#endif // SPADE_AUDIT_KERNEL_SYSCALL_HOOK_SETUP_FTRACE_LIST_H
