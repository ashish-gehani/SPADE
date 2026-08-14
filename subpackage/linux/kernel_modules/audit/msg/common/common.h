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

#ifndef SPADE_AUDIT_MSG_COMMON_COMMON_H
#define SPADE_AUDIT_MSG_COMMON_COMMON_H

#include <linux/sched.h>


struct msg_common_version
{
    u8 major;
    u8 minor;
    u8 patch;
};

enum msg_common_type
{
    MSG_NAMESPACES,
    MSG_NETFILTER,
    MSG_NETWORK,
    MSG_UBSI
};

// 'msg_common_header' must be the first struct in any msg.
struct msg_common_header
{
    struct msg_common_version version;
    enum msg_common_type msg_type;
};

struct msg_common_process
{
    pid_t ppid;
    pid_t pid;
    uid_t uid;
    uid_t euid;
    uid_t suid;
    uid_t fsuid;
    gid_t gid;
    gid_t egid;
    gid_t sgid;
    gid_t fsgid;
    char comm[TASK_COMM_LEN];
};

#endif // SPADE_AUDIT_MSG_COMMON_COMMON_H



