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

#ifndef SPADE_AUDIT_KERNEL_FUNCTION_NUMBER_H
#define SPADE_AUDIT_KERNEL_FUNCTION_NUMBER_H

#include <asm/syscall.h>

enum kernel_function_number
{
    KERN_F_NUM_SYS_ACCEPT = 0,
    KERN_F_NUM_SYS_ACCEPT4,
    KERN_F_NUM_SYS_BIND,
    KERN_F_NUM_SYS_CLONE,
    KERN_F_NUM_SYS_CONNECT,
    KERN_F_NUM_SYS_FORK,
    KERN_F_NUM_SYS_KILL,
    KERN_F_NUM_SYS_RECVFROM,
    KERN_F_NUM_SYS_RECVMSG,
    KERN_F_NUM_SYS_SENDMSG,
    KERN_F_NUM_SYS_SENDTO,
    KERN_F_NUM_SYS_SETNS,
    KERN_F_NUM_SYS_UNSHARE,
    KERN_F_NUM_SYS_VFORK
    // Start next func num after 500+ to skip all system calls.
};

/*
    Get system call number from the given function number.

    Params:
        dst                     : Pointer to set the result value at.
        f_num                   : Function number.
        default_to_func_num     : If true, when function number does not map to syscall number then return function number.

    Returns:
        0           -> dst is successfully set.
        -ive        -> Error code.
*/
int kernel_function_number_to_system_call_number(
    int *dst,
    enum kernel_function_number f_num,
    bool default_to_func_num
);

#endif // SPADE_AUDIT_KERNEL_FUNCTION_NUMBER_H