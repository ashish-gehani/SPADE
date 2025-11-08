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

#include <linux/slab.h>
#include <linux/gfp.h>
#include <linux/types.h>

#include "spade/audit/msg/namespace/ops.h"
#include "spade/audit/msg/namespace/create.h"
#include "spade/audit/msg/namespace/namespace.h"
#include "spade/audit/msg/namespace/serialize/audit.h"
#include "spade/audit/util/seqbuf/seqbuf.h"


static struct msg_common_header* _kalloc(void)
{
    return kzalloc(sizeof(struct msg_namespace), GFP_KERNEL);
}

static int _kinit(struct msg_common_header* msg)
{
    return msg_namespace_create((struct msg_namespace *)msg);
}

static int _to_audit_str(struct seqbuf *b, struct msg_common_header *msg)
{
    struct msg_namespace *msg_ptr;
    if (!b || !msg)
        return -EINVAL;

    if (msg->msg_type != MSG_NAMESPACES)
        return -EINVAL;

    msg_ptr = (struct msg_namespace *)msg;

    return msg_namespace_serialize_audit_msg(b, msg_ptr);
}

static const struct msg_ops msg_namespace_ops = {
    .kalloc = _kalloc,
    .kinit = _kinit,
    .to_audit_str = _to_audit_str
};

const struct msg_ops* msg_namespace_ops_get(void)
{
    return &msg_namespace_ops;
}

