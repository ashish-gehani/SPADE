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

#ifndef SPADE_AUDIT_MSG_NAMESPACE_NAMESPACE_H
#define SPADE_AUDIT_MSG_NAMESPACE_NAMESPACE_H

#include <linux/sched.h>
#include <linux/socket.h>

#include "spade/audit/msg/common/common.h"

enum msg_namespace_operation
{
    NS_OP_NEW_PROCESS = 0,
    NS_OP_SETNS = 1,
    NS_OP_UNSHARE = 2
};

struct msg_namespace
{
    struct msg_common_header header;
    int syscall_number;
    enum msg_namespace_operation op;
    pid_t ns_pid;
    pid_t host_pid;
    unsigned int ns_inum_mnt;
    unsigned int ns_inum_net;
    unsigned int ns_inum_pid;
    unsigned int ns_inum_pid_children;
    unsigned int ns_inum_usr;
    unsigned int ns_inum_ipc;
    unsigned int ns_inum_cgroup;
};

#endif // SPADE_AUDIT_MSG_NAMESPACE_NAMESPACE_H