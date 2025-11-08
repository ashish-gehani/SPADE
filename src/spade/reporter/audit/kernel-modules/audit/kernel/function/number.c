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

#include <asm/syscall.h>

#include "audit/kernel/function/number.h"

int kernel_function_number_to_system_call_number(
    int *dst,
    enum kernel_function_number f_num,
    bool default_to_func_num
)
{
    if (!dst)
        return -EINVAL;

    switch (f_num)
    {
    case KERN_F_NUM_SYS_ACCEPT:
        *dst = __NR_accept;
        break;
    case KERN_F_NUM_SYS_ACCEPT4:
        *dst = __NR_accept4;
        break;
    case KERN_F_NUM_SYS_BIND:
        *dst = __NR_bind;
        break;
    case KERN_F_NUM_SYS_CLONE:
        *dst = __NR_clone;
        break;
    case KERN_F_NUM_SYS_CONNECT:
        *dst = __NR_connect;
        break;
    case KERN_F_NUM_SYS_FORK:
        *dst = __NR_fork;
        break;
    case KERN_F_NUM_SYS_KILL:
        *dst = __NR_kill;
        break;
    case KERN_F_NUM_SYS_RECVFROM:
        *dst = __NR_recvfrom;
        break;
    case KERN_F_NUM_SYS_RECVMSG:
        *dst = __NR_recvmsg;
        break;
    case KERN_F_NUM_SYS_SENDMSG:
        *dst = __NR_sendmsg;
        break;
    case KERN_F_NUM_SYS_SENDTO:
        *dst = __NR_sendto;
        break;
    case KERN_F_NUM_SYS_SETNS:
        *dst = __NR_setns;
        break;
    case KERN_F_NUM_SYS_UNSHARE:
        *dst = __NR_unshare;
        break;
    case KERN_F_NUM_SYS_VFORK:
        *dst = __NR_vfork;
        break;
    default:
        if (default_to_func_num)
        {
            *dst = f_num;
        } else
        {
            return -ENOENT;
        }
        break;
    }

    return 0;
}
