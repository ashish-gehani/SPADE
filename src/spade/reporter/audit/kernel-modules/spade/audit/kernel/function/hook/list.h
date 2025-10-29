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

#ifndef SPADE_AUDIT_KERNEL_FUNCTION_HOOK_LIST_H
#define SPADE_AUDIT_KERNEL_FUNCTION_HOOK_LIST_H


#include <linux/types.h>

#include "spade/audit/kernel/function/hook/hook.h"


#define KERNEL_FUNCTION_HOOK_LIST_LEN_MAX 32

// Extern declarations for all hook structs
extern const struct kernel_syscall_hook kernel_syscall_hook_accept;
extern const struct kernel_syscall_hook kernel_syscall_hook_accept4;
extern const struct kernel_syscall_hook kernel_syscall_hook_bind;
extern const struct kernel_syscall_hook kernel_syscall_hook_clone;
extern const struct kernel_syscall_hook kernel_syscall_hook_connect;
extern const struct kernel_syscall_hook kernel_syscall_hook_fork;
extern const struct kernel_syscall_hook kernel_syscall_hook_kill;
extern const struct kernel_syscall_hook kernel_syscall_hook_recvfrom;
extern const struct kernel_syscall_hook kernel_syscall_hook_recvmsg;
extern const struct kernel_syscall_hook kernel_syscall_hook_sendmsg;
extern const struct kernel_syscall_hook kernel_syscall_hook_sendto;
extern const struct kernel_syscall_hook kernel_syscall_hook_setns;
extern const struct kernel_syscall_hook kernel_syscall_hook_unshare;
extern const struct kernel_syscall_hook kernel_syscall_hook_vfork;

// Array of all hook struct pointers where the list is null terminated.
extern const struct kernel_syscall_hook *KERNEL_FUNCTION_HOOK_LIST[KERNEL_FUNCTION_HOOK_LIST_LEN_MAX];


#endif // SPADE_AUDIT_KERNEL_FUNCTION_HOOK_LIST_H