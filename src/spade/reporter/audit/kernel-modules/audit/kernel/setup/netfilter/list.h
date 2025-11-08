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

#ifndef SPADE_AUDIT_KERNEL_NETFILTER_SETUP_LIST_H
#define SPADE_AUDIT_KERNEL_NETFILTER_SETUP_LIST_H

#include <linux/types.h>
#include <linux/netfilter.h>
#include <linux/skbuff.h>
#include <linux/netfilter_ipv4.h>

#include "audit/kernel/netfilter/netfilter.h"


const struct nf_hook_ops kernel_netfilter_setup_list_hooks[] = {
	{
		.hook = kernel_netfilter_hook_first, .pf = NFPROTO_IPV4,
		.hooknum = NF_INET_LOCAL_OUT, .priority = NF_IP_PRI_FIRST
	},
	{
		.hook = kernel_netfilter_hook_last, .pf = NFPROTO_IPV4,
		.hooknum = NF_INET_LOCAL_OUT, .priority = NF_IP_PRI_LAST
	},
	{
		.hook = kernel_netfilter_hook_first, .pf = NFPROTO_IPV6,
		.hooknum = NF_INET_LOCAL_OUT, .priority = NF_IP_PRI_FIRST
	},
	{
		.hook = kernel_netfilter_hook_last, .pf = NFPROTO_IPV6,
		.hooknum = NF_INET_LOCAL_OUT, .priority = NF_IP_PRI_LAST
	},
///
	{
		.hook = kernel_netfilter_hook_first, .pf = NFPROTO_IPV4,
		.hooknum = NF_INET_LOCAL_IN, .priority = NF_IP_PRI_FIRST
	},
	{
		.hook = kernel_netfilter_hook_last, .pf = NFPROTO_IPV4,
		.hooknum = NF_INET_LOCAL_IN, .priority = NF_IP_PRI_LAST
	},
	{
		.hook = kernel_netfilter_hook_first, .pf = NFPROTO_IPV6,
		.hooknum = NF_INET_LOCAL_IN, .priority = NF_IP_PRI_FIRST
	},
	{
		.hook = kernel_netfilter_hook_last, .pf = NFPROTO_IPV6,
		.hooknum = NF_INET_LOCAL_IN, .priority = NF_IP_PRI_LAST
	},
///
	{
		.hook = kernel_netfilter_hook_first, .pf = NFPROTO_IPV4,
		.hooknum = NF_INET_POST_ROUTING, .priority = NF_IP_PRI_FIRST
	},
	{
		.hook = kernel_netfilter_hook_last, .pf = NFPROTO_IPV4,
		.hooknum = NF_INET_POST_ROUTING, .priority = NF_IP_PRI_LAST
	},
	{
		.hook = kernel_netfilter_hook_first, .pf = NFPROTO_IPV6,
		.hooknum = NF_INET_POST_ROUTING, .priority = NF_IP_PRI_FIRST
	},
	{
		.hook = kernel_netfilter_hook_last, .pf = NFPROTO_IPV6,
		.hooknum = NF_INET_POST_ROUTING, .priority = NF_IP_PRI_LAST
	},
///
	{
		.hook = kernel_netfilter_hook_first, .pf = NFPROTO_IPV4,
		.hooknum = NF_INET_PRE_ROUTING, .priority = NF_IP_PRI_FIRST
	},
	{
		.hook = kernel_netfilter_hook_last, .pf = NFPROTO_IPV4,
		.hooknum = NF_INET_PRE_ROUTING, .priority = NF_IP_PRI_LAST
	},
	{
		.hook = kernel_netfilter_hook_first, .pf = NFPROTO_IPV6,
		.hooknum = NF_INET_PRE_ROUTING, .priority = NF_IP_PRI_FIRST
	},
	{
		.hook = kernel_netfilter_hook_last, .pf = NFPROTO_IPV6,
		.hooknum = NF_INET_PRE_ROUTING, .priority = NF_IP_PRI_LAST
	}
};

const int kernel_netfilter_setup_list_hooks_size = sizeof(kernel_netfilter_setup_list_hooks) / sizeof(kernel_netfilter_setup_list_hooks[0]);

#endif // SPADE_AUDIT_KERNEL_NETFILTER_SETUP_LIST_H