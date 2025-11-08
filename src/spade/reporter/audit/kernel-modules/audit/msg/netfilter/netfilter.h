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

#ifndef SPADE_AUDIT_MSG_NETFILTER_NETFILTER_H
#define SPADE_AUDIT_MSG_NETFILTER_NETFILTER_H

#include <linux/sched.h>
#include <linux/socket.h>
#include <linux/netfilter_ipv4.h>

#include "audit/msg/common/common.h"


struct msg_netfilter_addr
{
    union {
        struct in_addr ip4;
        struct in6_addr ip6;
    } addr;
    u16 port;
};

struct msg_netfilter
{
    struct msg_common_header header;
    unsigned int hook_num;
    enum nf_ip_hook_priorities priority;
    const void *skb_ptr;
    struct msg_netfilter_addr src_addr;
    struct msg_netfilter_addr dst_addr;
    int ip_proto;
    int transport_proto;
    unsigned int net_ns_inum;
};

#endif // SPADE_AUDIT_MSG_NETFILTER_NETFILTER_H

