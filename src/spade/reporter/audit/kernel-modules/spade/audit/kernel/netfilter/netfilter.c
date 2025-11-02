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

#include <linux/version.h>
#include <linux/kernel.h>
#include <linux/audit.h>
#include <linux/netfilter.h>
#include <linux/netfilter_ipv4.h>
#include <linux/net_namespace.h>
#include <net/net_namespace.h>
#include <linux/inetdevice.h>

#if IS_ENABLED(CONFIG_NF_CONNTRACK)
#include <net/netfilter/nf_conntrack.h>
#include <linux/netfilter/nf_conntrack_common.h>
#endif

#include "spade/audit/kernel/netfilter/netfilter.h"
#include "spade/audit/msg/netfilter/create.h"
#include "spade/audit/msg/netfilter/serialize/audit.h"
#include "spade/audit/msg/ops.h"
#include "spade/audit/global/global.h"
#include "spade/audit/kernel/helper/kernel.h"
#include "spade/audit/kernel/helper/audit_log.h"
#include "spade/audit/global/filter.h"


static struct
{
    unsigned long discarded_events;
} global_state = {
    .discarded_events = 0
};

static void _inc_discarded_event_count(void)
{
    global_state.discarded_events++;
}

static bool _is_auditing(void)
{
    return global_filter_netfilter_audit_hooks_on();
}

static enum ip_conntrack_info get_conntrack_info(const struct sk_buff *skb, enum ip_conntrack_info default_ct_info)
{
    enum ip_conntrack_info ct_info;
    struct nf_conn *ct;
    if (!skb)
        return default_ct_info;
// TODO
#if KERNEL_HELPER_KERNEL_VERSION_GTE_4_11_0
    ct = nf_ct_get(skb, &ct_info); // also derives ctinfo
    if (ct)
    {
        return ct_info;
    }
    return default_ct_info;
#else
    return default_ct_info;
#endif
}

static bool packet_can_be_handled_based_on_conntrack_info(const struct sk_buff *skb)
{
    enum ip_conntrack_info ct_info = get_conntrack_info(skb, IP_CT_ESTABLISHED);
    return global_filter_netfilter_conntrack_info_is_actionable(ct_info);
}

static bool packet_can_be_handled_based_on_user(const struct sk_buff *skb)
{
    struct sock *sk = skb_to_full_sk(skb);
    if (sk && sk->sk_socket && sk->sk_socket->file && sk->sk_socket->file->f_cred)
    {
        uid_t uid = from_kuid(&init_user_ns, sk->sk_socket->file->f_cred->uid);
        return global_filter_netfilter_user_is_actionable(uid);
    }
    else
    {
        return true;
    }
}

static unsigned int get_net_ns_num_ip4(
    unsigned int hook,
    enum nf_ip_hook_priorities priority,
    struct iphdr *iph)
{
    struct net *net = NULL;
    struct net_device *dev = NULL;
    __be32 selected_addr;
    bool can_get_ns = false;
    unsigned int net_ns_inum;

    if (hook == NF_INET_LOCAL_OUT && priority == NF_IP_PRI_FIRST)
    {
        selected_addr = iph->saddr;
        can_get_ns = true;
    }
    else if (hook == NF_INET_LOCAL_IN && priority == NF_IP_PRI_LAST)
    {
        selected_addr = iph->daddr;
        can_get_ns = true;
    }

    if (!can_get_ns)
        return 0;

    rcu_read_lock();
    for_each_net_rcu(net)
    {
        dev = __ip_dev_find(net, selected_addr, false);
        if (!dev)
            continue;
        net_ns_inum = net->ns.inum;
        break;
    }
    rcu_read_unlock();

    return net_ns_inum;
}

static unsigned int get_net_ns_num_ip6(
    unsigned int hook,
    enum nf_ip_hook_priorities priority,
    struct ipv6hdr *ipv6h)
{
    struct net *net = NULL;
    struct in6_addr selected_addr;
    bool can_get_ns = false;
    unsigned int net_ns_inum = 0;

    if (hook == NF_INET_LOCAL_OUT && priority == NF_IP_PRI_FIRST)
    {
        selected_addr = ipv6h->saddr;
        can_get_ns = true;
    }
    else if (hook == NF_INET_LOCAL_IN && priority == NF_IP_PRI_LAST)
    {
        selected_addr = ipv6h->daddr;
        can_get_ns = true;
    }

    if (!can_get_ns)
        return 0;

    rcu_read_lock();
    for_each_net_rcu(net)
    {
        int found = ipv6_chk_addr(net, &selected_addr, NULL, 0);
        if (!found)
            continue;
        net_ns_inum = net->ns.inum;
        break;
    }
    rcu_read_unlock();

    return net_ns_inum;
}

static unsigned int get_net_ns_num(
    unsigned int hook,
    enum nf_ip_hook_priorities priority,
    int ip_version,
    void *iph_generic)
{
    if (!global_filter_netfilter_include_ns_info())
        return 0;
    if (ip_version == AF_INET)
    {
        return get_net_ns_num_ip4(hook, priority, (struct iphdr *)iph_generic);
    }
    else if (ip_version == AF_INET6)
    {
        return get_net_ns_num_ip6(hook, priority, (struct ipv6hdr *)iph_generic);
    }
    return 0;
}

static void nf_handle_packet(enum nf_ip_hook_priorities prio, const struct sk_buff *skb, const struct nf_hook_state *nf_state)
{
    struct audit_context *audit_ctx;
    struct msg_netfilter msg;

    if (!skb || !nf_state)
        goto exit;

    if (!packet_can_be_handled_based_on_user(skb))
        goto discard_and_exit;

    if (!packet_can_be_handled_based_on_conntrack_info(skb))
        goto discard_and_exit;

    audit_ctx = NULL;

    msg_netfilter_create(&msg);

    msg.skb_ptr = skb;
    msg.hook_num = nf_state->hook;
    msg.priority = prio;
    msg.ip_proto = nf_state->pf;

    if (nf_state->pf == NFPROTO_IPV4)
    {
        struct iphdr *iph;
        iph = ip_hdr(skb);
        if (!iph)
            goto discard_and_exit;
        if (iph->protocol != IPPROTO_TCP && iph->protocol != IPPROTO_UDP)
            goto discard_and_exit;

        msg.transport_proto = iph->protocol;

        memcpy(&(msg.src_addr.addr.ip4), &(iph->saddr), sizeof(struct in_addr));
        memcpy(&(msg.dst_addr.addr.ip4), &(iph->daddr), sizeof(struct in_addr));

        msg.net_ns_inum = get_net_ns_num(nf_state->hook, prio, AF_INET, iph);
    }
    else if (nf_state->pf == NFPROTO_IPV6)
    {
        struct ipv6hdr *ipv6h;
        ipv6h = ipv6_hdr(skb);
        if (!ipv6h)
            goto discard_and_exit;
        if (ipv6h->nexthdr != IPPROTO_TCP && ipv6h->nexthdr != IPPROTO_UDP)
            goto discard_and_exit;

        msg.transport_proto = ipv6h->nexthdr;

        memcpy(&(msg.src_addr.addr.ip6), &(ipv6h->saddr), sizeof(struct in6_addr));
        memcpy(&(msg.dst_addr.addr.ip6), &(ipv6h->daddr), sizeof(struct in6_addr));

        msg.net_ns_inum = get_net_ns_num(nf_state->hook, prio, AF_INET6, ipv6h);
    }
    else
    {
        goto discard_and_exit;
    }

    if (msg.transport_proto == IPPROTO_TCP)
    {
        struct tcphdr *tcph;
        tcph = tcp_hdr(skb);
        if (!tcph)
            goto discard_and_exit;
        msg.src_addr.port = ntohs(tcph->source);
        msg.dst_addr.port = ntohs(tcph->dest);
    }
    else if (msg.transport_proto == IPPROTO_UDP)
    {
        struct udphdr *udph;
        udph = udp_hdr(skb);
        if (!udph)
            goto discard_and_exit;
        msg.src_addr.port = ntohs(udph->source);
        msg.dst_addr.port = ntohs(udph->dest);
    }
    else
    {
        goto discard_and_exit;
    }

    kernel_helper_audit_log(audit_ctx, &msg.header);

discard_and_exit:
    _inc_discarded_event_count();

exit:
    return;
}

unsigned int kernel_netfilter_hook_first(void *priv, struct sk_buff *skb, const struct nf_hook_state *state)
{
    if (_is_auditing())
        nf_handle_packet(NF_IP_PRI_FIRST, skb, state);
    return NF_ACCEPT;
}

unsigned int kernel_netfilter_hook_last(void *priv, struct sk_buff *skb, const struct nf_hook_state *state)
{
    if (_is_auditing())
        nf_handle_packet(NF_IP_PRI_LAST, skb, state);
    return NF_ACCEPT;
}