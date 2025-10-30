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
    KERN_F_NUM_SYS_ACCEPT       = __NR_accept,
    KERN_F_NUM_SYS_ACCEPT4      = __NR_accept4,
    KERN_F_NUM_SYS_BIND         = __NR_bind,
    KERN_F_NUM_SYS_RECVFROM     = __NR_recvfrom,
    KERN_F_NUM_SYS_RECVMSG      = __NR_recvmsg,
    KERN_F_NUM_SYS_SENDMSG      = __NR_sendmsg,
    KERN_F_NUM_SYS_SENDTO       = __NR_sendto,
    KERN_F_NUM_SYS_CLONE        = __NR_clone,
    KERN_F_NUM_SYS_FORK         = __NR_fork,
    KERN_F_NUM_SYS_SETNS        = __NR_setns,
    KERN_F_NUM_SYS_UNSHARE      = __NR_unshare,
    KERN_F_NUM_SYS_VFORK        = __NR_vfork
};

#endif // SPADE_AUDIT_KERNEL_FUNCTION_NUMBER_H