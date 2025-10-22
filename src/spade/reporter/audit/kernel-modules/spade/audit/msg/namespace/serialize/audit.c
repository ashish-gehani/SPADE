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

#include <linux/errno.h>
#include <linux/string.h>

#include "spade/audit/msg/common/serialize/audit.h"
#include "spade/audit/msg/namespace/serialize/audit.h"


static void seqbuf_operation_to_string(struct seqbuf *b, enum msg_namespace_operation *o)
{
    char *op_str;
    switch(*o)
    {
        case NS_OP_NEW_PROCESS: op_str = "NEWPROCESS"; break;
        case NS_OP_SETNS: op_str = "SETNS"; break;
        case NS_OP_UNSHARE: op_str = "UNSHARE"; break;
        default: op_str = "UNKNOWN"; break;
    }
    util_seqbuf_printf(b, "ns_operation=ns_%s", op_str);
}

int msg_namespace_serialize_audit_msg(
    struct seqbuf *b, struct msg_namespace *msg
)
{
    if (!b || !msg)
        return -EINVAL;

    msg_common_serialize_audit_msg_header(b, &msg->header);

    util_seqbuf_printf(b, "ns_syscall=%d", msg->syscall_number);
    util_seqbuf_printf(b, " ns_subtype=ns_namespaces ");
    seqbuf_operation_to_string(b, &msg->op);
    util_seqbuf_printf(b, " ns_ns_pid=%d", msg->ns_pid);
    util_seqbuf_printf(b, " ns_host_pid=%d", msg->host_pid);
    util_seqbuf_printf(b, " ns_inum_mnt=%u", msg->ns_inum_mnt);
    util_seqbuf_printf(b, " ns_inum_net=%u", msg->ns_inum_net);
    util_seqbuf_printf(b, " ns_inum_pid=%u", msg->ns_inum_pid);
    util_seqbuf_printf(b, " ns_inum_pid_children=%u", msg->ns_inum_pid_children);
    util_seqbuf_printf(b, " ns_inum_usr=%u", msg->ns_inum_usr);
    util_seqbuf_printf(b, " ns_inum_ipc=%u", msg->ns_inum_ipc);
    util_seqbuf_printf(b, " ns_inum_cgroup=%u", msg->ns_inum_cgroup);

    return 0;
}