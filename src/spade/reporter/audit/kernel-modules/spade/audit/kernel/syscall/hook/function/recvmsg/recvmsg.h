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

#ifndef SPADE_AUDIT_KERNEL_SYSCALL_HOOK_FUNCTION_RECVMSG_H
#define SPADE_AUDIT_KERNEL_SYSCALL_HOOK_FUNCTION_RECVMSG_H

#include <linux/types.h>

/*
    Get syscall number.
*/
int kernel_syscall_hook_function_recvmsg_num(void);

/*
    Get syscall name.
*/
const char* kernel_syscall_hook_function_recvmsg_name(void);

/*
    Get pointer to original function to update or use.
*/
void *kernel_syscall_hook_function_recvmsg_original_ptr(void);

/*
    Get hook function to call.
*/
void *kernel_syscall_hook_function_recvmsg_hook(void);

#endif // SPADE_AUDIT_KERNEL_SYSCALL_HOOK_FUNCTION_RECVMSG_H