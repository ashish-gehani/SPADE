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

#ifndef SPADE_AUDIT_MSG_NETWORK_NETWORK_H
#define SPADE_AUDIT_MSG_NETWORK_NETWORK_H

#include <linux/sched.h>
#include <linux/socket.h>

#include "audit/msg/common/common.h"

struct msg_network
{
    struct msg_common_header header;
    int syscall_number;
    long syscall_result;
    int syscall_success;
    int fd;
    struct msg_common_process proc_info;
    int sock_type;
    struct sockaddr_storage local_saddr;
    // https://yarchive.net/comp/linux/socklen_t.html
    int local_saddr_size;
    struct sockaddr_storage remote_saddr;
    int remote_saddr_size;
    unsigned int net_ns_inum;
};

#endif // SPADE_AUDIT_MSG_NETWORK_NETWORK_H