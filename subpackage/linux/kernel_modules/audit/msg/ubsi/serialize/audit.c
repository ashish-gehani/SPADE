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

#include "audit/msg/common/serialize/audit.h"
#include "audit/msg/ubsi/serialize/audit.h"


int msg_ubsi_serialize_audit_msg(
    struct seqbuf *b, struct msg_ubsi *msg
)
{
    if (!b || !msg)
        return -EINVAL;

    util_seqbuf_printf(b, "ubsi_intercepted=\"");

    msg_common_serialize_audit_msg_header(b, &msg->header);

    util_seqbuf_printf(b, "syscall=%d", msg->syscall_number);
    util_seqbuf_printf(b, " success=%s", msg->syscall_success ? "yes" : "no");
    util_seqbuf_printf(b, " exit=%ld", msg->syscall_result);
    util_seqbuf_printf(b, " a0=%x", msg->target_pid);
    util_seqbuf_printf(b, " a1=%x", msg->signal);
    util_seqbuf_printf(b, " a2=0 a3=0 items=0 ");
    msg_common_serialize_audit_msg_process(b, &msg->proc_info);

    util_seqbuf_printf(b, "\"");

    return 0;
}