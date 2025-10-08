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

#include "spade/audit/kernel/syscall/hook/list.h"


#define K_S_H_DECL(sys_name) \
    { \
        .get_num = kernel_syscall_hook_function_##sys_name##_num, \
        .get_name = kernel_syscall_hook_function_##sys_name##_name, \
        .get_hook_func = kernel_syscall_hook_function_##sys_name##_hook, \
        .get_orig_func_ptr = kernel_syscall_hook_function_##sys_name##_original_ptr, \
    }

const struct kernel_syscall_hook KERNEL_SYSCALL_HOOK_LIST[] = {
    K_S_H_DECL(accept),
    K_S_H_DECL(accept4),
    K_S_H_DECL(bind),
    K_S_H_DECL(clone),
    K_S_H_DECL(connect),
    K_S_H_DECL(fork),
    K_S_H_DECL(recvfrom),
    K_S_H_DECL(recvmsg),
    K_S_H_DECL(sendmsg),
    K_S_H_DECL(sendto),
    K_S_H_DECL(setns),
    K_S_H_DECL(unshare),
    K_S_H_DECL(vfork)
};
