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


#include <linux/gfp.h>
#include <linux/errno.h>

#include "spade/audit/helper/audit_log.h"
#include "spade/audit/msg/ops.h"
#include "spade/util/seqbuf/seqbuf.h"


int helper_audit_log(struct audit_context *ctx, struct msg_common_header *msg_h)
{
    int err;
    char buf_msg[HELPER_AUDIT_LOG_MSG_BUF_LEN];
    struct seqbuf sb;

    if (!msg_h)
        return -EINVAL;

    util_seqbuf_init(&sb, &buf_msg[0], HELPER_AUDIT_LOG_MSG_BUF_LEN);

    err = msg_ops_to_audit_str(&sb, msg_h);
    if (err != 0)
        return err;

    if (util_seqbuf_has_overflowed(&sb))
        return -ENOMEM;

    audit_log(ctx, GFP_KERNEL, AUDIT_USER, &buf_msg[0]);

    return 0;
}