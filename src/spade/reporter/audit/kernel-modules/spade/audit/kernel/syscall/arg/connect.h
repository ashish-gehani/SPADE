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

#ifndef SPADE_AUDIT_KERNEL_SYSCALL_ARG_CONNECT_H
#define SPADE_AUDIT_KERNEL_SYSCALL_ARG_CONNECT_H

#include <linux/types.h>
#include <linux/socket.h>

struct kernel_syscall_arg_connect
{
    int sockfd;
    struct sockaddr __user *addr;
    uint32_t addrlen;
};

#endif // SPADE_AUDIT_KERNEL_SYSCALL_ARG_CONNECT_H