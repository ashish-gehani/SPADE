/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International

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

#include <linux/kernel.h>
#include <linux/errno.h>
#include <net/scm.h>

#include "audit/msg/common/common.h"
#include "audit/msg/common/serialize/audit.h"
#include "audit/msg/scm_fd/serialize/audit.h"


static int _msg_scm_fd_serialize_audit_msg(
    struct seqbuf *b, struct msg_scm_fd *msg
)
{
    int i;

    if (!b || !msg)
        return -EINVAL;

    util_seqbuf_printf(b, "spade_record_type=scm_fds ");

    msg_common_serialize_audit_msg_header(b, &msg->header);

    util_seqbuf_printf(b, " pid=%d", msg->pid);
    util_seqbuf_printf(b, " syscall=%d", msg->syscall_number);
    util_seqbuf_printf(b, " fds_count=%d", msg->fds_count);

    for (i = 0; i < msg->fds_count; i++)
        util_seqbuf_printf(b, " fds[%d]=%d", i, msg->fds[i]);

    return 0;
}

int msg_scm_fd_serialize_audit_msg(
    struct seqbuf *b, struct msg_scm_fd *msg
)
{
    if (!msg)
        return -EINVAL;
    return _msg_scm_fd_serialize_audit_msg(b, msg);
}
