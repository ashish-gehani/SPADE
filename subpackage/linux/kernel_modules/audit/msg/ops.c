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

#include "audit/msg/ops.h"
#include "audit/msg/namespace/ops.h"
#include "audit/msg/netfilter/ops.h"
#include "audit/msg/network/ops.h"
#include "audit/msg/ubsi/ops.h"
#include "audit/util/seqbuf/seqbuf.h"


const struct msg_ops* msg_ops_get(enum msg_common_type type)
{
    switch (type)
    {
        case MSG_NAMESPACES         : return msg_namespace_ops_get();
        case MSG_NETFILTER          : return msg_netfilter_ops_get();
        case MSG_NETWORK  : return msg_network_ops_get();
        case MSG_UBSI   : return msg_ubsi_ops_get();
        default                     : return NULL;
    }
}

struct msg_common_header* msg_ops_kalloc_kinit(enum msg_common_type type)
{
    int err;
    struct msg_common_header *msg;
    const struct msg_ops* o = msg_ops_get(type);

    if (!o)
        return NULL;

    msg = o->kalloc();
    if (!msg)
        return NULL;

    err = o->kinit(msg);
    if (err != 0)
    {
        msg_ops_kfree(msg);
        return NULL;
    }

    return msg;
}

int msg_ops_kinit(enum msg_common_type type, struct msg_common_header* msg)
{
    const struct msg_ops* o;
    if (!msg)
        return -EINVAL;

    o = msg_ops_get(type);

    if (!o)
        return -ENOENT;

    return o->kinit(msg);
}

int msg_ops_to_audit_str(struct seqbuf *b, struct msg_common_header* msg)
{
    const struct msg_ops* o;
    if (!b || !msg)
        return -EINVAL;

    o = msg_ops_get(msg->msg_type);

    if (!o)
        return -ENOENT;

    return o->to_audit_str(b, msg);
}

void msg_ops_kfree(struct msg_common_header* msg)
{
    if (!msg)
        return;

    kfree(msg);
}

