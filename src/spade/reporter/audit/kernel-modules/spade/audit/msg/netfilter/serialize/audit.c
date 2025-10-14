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
#include "spade/audit/msg/netfilter/serialize/audit.h"

#define IP_STR_LEN 64


static void seqbuf_hook_num_to_string(struct seqbuf *b, unsigned int hook_num)
{
    char *hook_name;
    switch(hook_num){
        case NF_INET_LOCAL_OUT: 	hook_name = "NF_INET_LOCAL_OUT"; break;
        case NF_INET_LOCAL_IN: 		hook_name = "NF_INET_LOCAL_IN"; break;
        case NF_INET_POST_ROUTING: 	hook_name = "NF_INET_POST_ROUTING"; break;
        case NF_INET_PRE_ROUTING: 	hook_name = "NF_INET_PRE_ROUTING"; break;
        default:                    hook_name = "UNKNOWN"; break;
    }
    util_seqbuf_printf(b, "nf_hook=%s", hook_name);
}

static void seqbuf_priority_to_string(struct seqbuf *b, enum nf_ip_hook_priorities priority)
{
    char *prio_name;
    switch(priority){
        case NF_IP_PRI_FIRST: 	prio_name = "NF_IP_PRI_FIRST"; break;
        case NF_IP_PRI_LAST: 	prio_name = "NF_IP_PRI_LAST"; break;
        default: 				prio_name = "UNKNOWN"; break;
    }
    util_seqbuf_printf(b, "nf_priority=%s", prio_name);
}

static void seqbuf_addr_to_string(struct seqbuf *b, char *key_prefix, int ip_proto, struct msg_netfilter_addr *addr)
{
    char ip_str[IP_STR_LEN];

    memset(&ip_str[0], 0, IP_STR_LEN);

    if (ip_proto == NFPROTO_IPV4)
    {
        snprintf(&ip_str[0], IP_STR_LEN, "%pI4", &addr->addr.ip4);
    } else if (ip_proto == NFPROTO_IPV6)
    {
        snprintf(&ip_str[0], IP_STR_LEN, "%pI6", &addr->addr.ip6);
    } else
    {
        snprintf(&ip_str[0], IP_STR_LEN, "unknown");
    }
    util_seqbuf_printf(
        b, "%s_ip=%s %s_port=%u", 
        key_prefix, &ip_str[0], 
        key_prefix, addr->port
    );
}

static void seqbuf_transport_protocol_to_string(struct seqbuf *b, int proto)
{
    char *proto_name;
    switch(proto){
        case IPPROTO_TCP: 	proto_name = "TCP"; break;
        case IPPROTO_UDP: 	proto_name = "UDP"; break;
        default: 			proto_name = "UNKNOWN"; break;
    }
    util_seqbuf_printf(b, "nf_protocol=%s", proto_name);
}

static void seqbuf_ip_version_to_string(struct seqbuf *b, int ip_version)
{
    char *name;
    switch(ip_version){
        case NFPROTO_IPV4: 	name = "IPV4"; break;
        case NFPROTO_IPV6: 	name = "IPV6"; break;
        default: 			name = "UNKNOWN"; break;
    }
    util_seqbuf_printf(b, "nf_ip_version=%s", name);
}

int msg_netfilter_serialize_audit_msg(
    struct seqbuf *b, struct msg_netfilter *msg
)
{
    if (!b || !msg)
        return -EINVAL;

    msg_common_serialize_audit_msg_header(b, &msg->header);

    util_seqbuf_printf(b, "nf_subtype=nf_netfilter ");
    seqbuf_hook_num_to_string(b, msg->hook_num);
    util_seqbuf_printf(b, " ");
    seqbuf_priority_to_string(b, msg->priority);
    util_seqbuf_printf(b, " nf_id=%p", msg->skb_ptr);
    util_seqbuf_printf(b, " ");
    seqbuf_addr_to_string(b, "nf_src", msg->ip_proto, &msg->src_addr);
    util_seqbuf_printf(b, " ");
    seqbuf_addr_to_string(b, "nf_dst", msg->ip_proto, &msg->dst_addr);
    util_seqbuf_printf(b, " ");
    seqbuf_transport_protocol_to_string(b, msg->transport_proto);
    util_seqbuf_printf(b, " ");
    seqbuf_ip_version_to_string(b, msg->ip_proto);
    util_seqbuf_printf(b, " nf_net_ns=%u", msg->net_ns_inum);

    return 0;
}