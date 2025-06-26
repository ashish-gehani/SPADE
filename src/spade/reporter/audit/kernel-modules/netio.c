/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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

#include <linux/audit.h>
#include <linux/file.h>
#include <linux/kallsyms.h>
#include <linux/mnt_namespace.h>
#include <linux/pid_namespace.h>
#include <linux/net_namespace.h>
#include <linux/user_namespace.h>
#include <linux/ipc_namespace.h>
#include <linux/net.h>
#include <linux/ns_common.h>
#include <linux/nsproxy.h>
#include <linux/proc_ns.h>
#include <linux/uaccess.h>  // copy_from_user
#include <linux/unistd.h>  // __NR_<system-call-name>
#include <linux/version.h>
#include <linux/netfilter.h>
#include <linux/netfilter_ipv4.h>
#include <linux/ip.h>
#include <linux/ipv6.h>
#include <linux/tcp.h>
#include <linux/udp.h>
#include <linux/inetdevice.h>
#include <linux/ptrace.h>

#include "globals.h"

#if IS_ENABLED(CONFIG_NF_CONNTRACK)
#include <net/netfilter/nf_conntrack.h>
#include <linux/netfilter/nf_conntrack_common.h>
#endif

#ifndef _ADDRCONF_H
#include <net/addrconf.h>
#endif

#define BUFFER_SIZE_IP 50

/*
 * 'stop' variable used to start and stop ONLY logging of system calls to audit log.
 * Don't need to synchronize 'stop' variable modification because it can only be set by a kernel module and only one
 * kernel module is updating it at the moment. Also, only one instance of a kernel module can be added at a time
 * hence ensuring no concurrent updates.
 */
static volatile int stop = 1;
static int nf_discarded = 0;

static unsigned int nf_spade_hook_function_first(void *priv, struct sk_buff *skb, const struct nf_hook_state *state);
static unsigned int nf_spade_hook_function_last(void *priv, struct sk_buff *skb, const struct nf_hook_state *state);
static void nf_spade_log_to_audit(const int priority, const struct sk_buff *skb, const struct nf_hook_state *state);
static void nf_ct_spade_get_ip_conntrack_info_enum(const struct sk_buff *skb, enum ip_conntrack_info *ctinfo, int *manual_ct);

static int item_in_array(int, int[], int);
static int log_syscall(int, int, int, int);
int netio_logging_start(char* caller_build_hash, int net_io_flag, int syscall_success_flag, 
									int pids_ignore_length, int pids_ignore_list[],
									int ppids_ignore_length, int ppids_ignore_list[],
									int uids_len, int uids[], int ignore_uids,
									int harden_tgids_length, int harden_tgids_list[], int namespaces_flag,
									int nf_hooks_flag, int nf_hooks_log_all_ct_flag, int nf_handle_user_flag); // 1 success, 0 failure
void netio_logging_stop(char* caller_build_hash);


static int item_in_array(int id, int arr[], int arrlen){
	for(int i = 0; i < arrlen; i++){
		if(arr[i] == id)
			return 1;
	}
	return 0;
}

static int log_syscall(int pid, int ppid, int uid, int success){
	if(syscall_success == -1){ // log all
		// log it if other filters matched
	}else if(syscall_success == 0){ // only log failed ones
		if(success == 1){ // successful so don't log
			return -1;
		}
	}else if(syscall_success == 1){ // only log successful ones
		if(success == 0){
			return -1; //failed so don't log	
		}
	}

	if(ignore_uids == 1){
		if(item_in_array(uid, uids, uids_len)){
			return -1;
		}
	}else{ // ignore_uids = 0 i.e. capture the uid in the list
		if(!item_in_array(uid, uids, uids_len)){
			return -1;
		}
	}
	
	if(item_in_array(pid, pids_ignore, pids_ignore_len)){
		return -1;
	}
	
	if(item_in_array(ppid, ppids_ignore, ppids_ignore_len)){
		return -1;
	}
	return 1;
}

static int __init onload(void){
    /*
     * A non 0 return means init_module failed; module can't be loaded.
     */
    return 0;
}

static void __exit onunload(void) {
	return;
}

static void nf_ct_spade_get_ip_conntrack_info_enum(const struct sk_buff *skb, enum ip_conntrack_info *ctinfo, int *manual_ct){
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0)
	struct nf_conn *nfconn;

	nfconn = (struct nf_conn *)skb_nfct(skb);
	if(nfconn){
		nfconn = nf_ct_get(skb, ctinfo);
		*manual_ct = 0;
	}else{
		*ctinfo = IP_CT_NEW; // check everything new for correctness over performance
		*manual_ct = 1;
	}
#else
	*ctinfo = IP_CT_NEW;
	*manual_ct = 1;
#endif
}

static void nf_spade_log_to_audit(const int priority, const struct sk_buff *skb, const struct nf_hook_state *state){
	if(skb && state){
		int ingress; // 1 otherwise egress (0)
		int net_ns_found;
		unsigned int net_ns_inum;
		int hooknum;
		char *hook_name;
		char *priority_name;
		char *protocol_name;
		char *ip_version_name;
		char *ct_info_name;

		char buffer_src_ip[BUFFER_SIZE_IP];
		char buffer_dst_ip[BUFFER_SIZE_IP];

		int manual_ct;
		enum ip_conntrack_info ctinfo;
		int src_port;
		int dst_port;
		int print_result;
		unsigned int protocol;

		ingress = -1;
		net_ns_found = 0;
		net_ns_inum = 0;
		src_port = -1;
		dst_port = -1;
		protocol = -1;
		print_result = 0;

		hooknum = state->hook;

		if(nf_handle_user == 1){
			struct sock *sk;
			sk = skb_to_full_sk(skb);
			if(sk != NULL && sk->sk_socket != NULL && sk->sk_socket->file != NULL && sk->sk_socket->file->f_cred != NULL){
				if(log_syscall(-1, -1, (int)(from_kuid(&init_user_ns, sk->sk_socket->file->f_cred->uid)), -1) != 1){
					nf_discarded++;
					return;
				}
			}
		}


		nf_ct_spade_get_ip_conntrack_info_enum(skb, &ctinfo, &manual_ct);
		if(nf_hooks_log_all_ct != 1){
			if(ctinfo != IP_CT_NEW){
				return;
			}
		}

		switch(hooknum){
			case NF_INET_LOCAL_OUT: 	hook_name = "NF_INET_LOCAL_OUT"; ingress = 0; break;
			case NF_INET_LOCAL_IN: 		hook_name = "NF_INET_LOCAL_IN"; ingress = 1; break;
			case NF_INET_POST_ROUTING: 	hook_name = "NF_INET_POST_ROUTING"; ingress = 0; break;
			case NF_INET_PRE_ROUTING: 	hook_name = "NF_INET_PRE_ROUTING"; ingress = 1; break;
			default: 			return;
		}

		if(state->pf == NFPROTO_IPV4){
			struct iphdr *iph;
			iph = ip_hdr(skb);
			if(!iph){
				return;
			}else{
				if(iph->protocol != IPPROTO_TCP && iph->protocol != IPPROTO_UDP){
					return;
				}else{
					protocol = iph->protocol;
					memset(&buffer_src_ip[0], '\0', BUFFER_SIZE_IP);

					print_result = snprintf(&buffer_src_ip[0], BUFFER_SIZE_IP, "%pI4", &iph->saddr);
					if(print_result < 0 || print_result >= BUFFER_SIZE_IP){
						return;
					}

					memset(&buffer_dst_ip[0], '\0', BUFFER_SIZE_IP);

					print_result = snprintf(&buffer_dst_ip[0], BUFFER_SIZE_IP, "%pI4", &iph->daddr);
					if(print_result < 0 || print_result >= BUFFER_SIZE_IP){
						return;
					}
				}
			}
			ip_version_name = "IPV4";
			if(namespaces != 0){
				int get_ns;
				struct net *net;
				struct net_device *dev;
				__be32 selected_addr;

				get_ns = 0;

				if(hooknum == NF_INET_LOCAL_OUT && priority == NF_IP_PRI_FIRST){
					selected_addr = iph->saddr;
					get_ns = 1;
				}else if(hooknum == NF_INET_LOCAL_IN && priority == NF_IP_PRI_LAST){
					selected_addr = iph->daddr;
					get_ns = 1;
				}

				if(get_ns == 1){
					dev = NULL;
					rcu_read_lock();
					for_each_net_rcu(net){
						if(dev == NULL){
							dev = __ip_dev_find(net, selected_addr, false);
							if(dev){
								net_ns_inum = net->ns.inum;
								net_ns_found = 1;
							}
						}
					}
					rcu_read_unlock();
				}
			}
		}else if(state->pf == NFPROTO_IPV6){
			struct ipv6hdr *ipv6h;
			ipv6h = ipv6_hdr(skb);
			if(!ipv6h){
				return;
			}else{
				if(ipv6h->nexthdr != IPPROTO_TCP && ipv6h->nexthdr != IPPROTO_UDP){
					return;
				}else{
					protocol = ipv6h->nexthdr;
					memset(&buffer_src_ip[0], '\0', BUFFER_SIZE_IP);

					print_result = snprintf(&buffer_src_ip[0], BUFFER_SIZE_IP, "%pI6", &ipv6h->saddr);
					if(print_result < 0 || print_result >= BUFFER_SIZE_IP){
						return;
					}

					memset(&buffer_dst_ip[0], '\0', BUFFER_SIZE_IP);

					print_result = snprintf(&buffer_dst_ip[0], BUFFER_SIZE_IP, "%pI6", &ipv6h->daddr);
					if(print_result < 0 || print_result >= BUFFER_SIZE_IP){
						return;
					}
				}
			}
			ip_version_name = "IPV6";
			if(namespaces != 0){
				int get_ns;
				struct net *net;
				int found;
				struct in6_addr selected_addr;

				get_ns = 0;

                                if(hooknum == NF_INET_LOCAL_OUT && priority == NF_IP_PRI_FIRST){
                                        selected_addr = ipv6h->saddr;
                                        get_ns = 1;
                                }else if(hooknum == NF_INET_LOCAL_IN && priority == NF_IP_PRI_LAST){
                                        selected_addr = ipv6h->daddr;
                                        get_ns = 1;
                                }

				if(get_ns == 1){
					found = 0;
					rcu_read_lock();
					for_each_net_rcu(net){
						if(found == 0){
							found = ipv6_chk_addr(net, &selected_addr, NULL, 0);
							if(found){
								net_ns_inum = net->ns.inum;
								net_ns_found = 1;
							}
						}
					}
					rcu_read_unlock();
				}
			}
		}else{
			return;
		}

		if(protocol != IPPROTO_TCP && protocol != IPPROTO_UDP){
			return;
		}

		if(protocol == IPPROTO_TCP){
			struct tcphdr *tcph;
			tcph = tcp_hdr(skb);
			if(!tcph){
				return;
			}else{
				src_port = ntohs(tcph->source);
				dst_port = ntohs(tcph->dest);
			}
			protocol_name = "TCP";
		}else if(protocol == IPPROTO_UDP){
			struct udphdr *udph;
			udph = udp_hdr(skb);
			if(!udph){
				return;
			}else{
				src_port = ntohs(udph->source);
				dst_port = ntohs(udph->dest);
			}
			protocol_name = "UDP";
		}else{
			return;
		}

		switch(priority){
			case NF_IP_PRI_FIRST: 	priority_name = "NF_IP_PRI_FIRST"; break;
			case NF_IP_PRI_LAST: 	priority_name = "NF_IP_PRI_LAST"; break;
			default: 				priority_name = "UNKNOWN"; break;
		}

		switch(ctinfo){
			case IP_CT_ESTABLISHED:			ct_info_name = "IP_CT_ESTABLISHED"; break;
			case IP_CT_RELATED:				ct_info_name = "IP_CT_RELATED"; break;
			case IP_CT_NEW:					ct_info_name = "IP_CT_NEW"; break;
			//case IP_CT_IS_REPLY:			ct_info_name = "IP_CT_IS_REPLY"; break;
			case IP_CT_ESTABLISHED_REPLY:	ct_info_name = "IP_CT_ESTABLISHED_REPLY"; break;
			case IP_CT_RELATED_REPLY:		ct_info_name = "IP_CT_RELATED_REPLY"; break;
			default:						ct_info_name = "UNKNOWN"; break;
		}

		if(namespaces != 0){
			if(net_ns_found){
				audit_log(NULL, GFP_KERNEL, AUDIT_USER,
						"version=%s nf_subtype=nf_netfilter nf_hook=%s nf_priority=%s nf_id=%p nf_src_ip=%s nf_src_port=%d nf_dst_ip=%s nf_dst_port=%d nf_protocol=%s nf_ip_version=%s nf_net_ns=%u",
						"nf0",
						hook_name, priority_name,
						skb, buffer_src_ip, src_port, buffer_dst_ip, dst_port, protocol_name, ip_version_name, net_ns_inum
						);
			}else{
				audit_log(NULL, GFP_KERNEL, AUDIT_USER,
						"version=%s nf_subtype=nf_netfilter nf_hook=%s nf_priority=%s nf_id=%p nf_src_ip=%s nf_src_port=%d nf_dst_ip=%s nf_dst_port=%d nf_protocol=%s nf_ip_version=%s nf_net_ns=-1",
						"nf0",
						hook_name, priority_name,
						skb, buffer_src_ip, src_port, buffer_dst_ip, dst_port, protocol_name, ip_version_name
						);
			}
		}else{
			// null log_net_ns
			audit_log(NULL, GFP_KERNEL, AUDIT_USER,
					"version=%s nf_subtype=nf_netfilter nf_hook=%s nf_priority=%s nf_id=%p nf_src_ip=%s nf_src_port=%d nf_dst_ip=%s nf_dst_port=%d nf_protocol=%s nf_ip_version=%s",
					"nf0",
					hook_name, priority_name,
					skb, buffer_src_ip, src_port, buffer_dst_ip, dst_port, protocol_name, ip_version_name
					);
		}
	}
}

static unsigned int nf_spade_hook_function_first(void *priv, struct sk_buff *skb, const struct nf_hook_state *state){
	nf_spade_log_to_audit(NF_IP_PRI_FIRST, skb, state);
	return NF_ACCEPT;
}

static unsigned int nf_spade_hook_function_last(void *priv, struct sk_buff *skb, const struct nf_hook_state *state){
	nf_spade_log_to_audit(NF_IP_PRI_LAST, skb, state);
	return NF_ACCEPT;
}

static const struct nf_hook_ops nf_hook_ops_spade[] = {
	{
		.hook = nf_spade_hook_function_first, .pf = NFPROTO_IPV4,
		.hooknum = NF_INET_LOCAL_OUT, .priority = NF_IP_PRI_FIRST
	},
	{
		.hook = nf_spade_hook_function_last, .pf = NFPROTO_IPV4,
		.hooknum = NF_INET_LOCAL_OUT, .priority = NF_IP_PRI_LAST
	},
	{
		.hook = nf_spade_hook_function_first, .pf = NFPROTO_IPV6,
		.hooknum = NF_INET_LOCAL_OUT, .priority = NF_IP_PRI_FIRST
	},
	{
		.hook = nf_spade_hook_function_last, .pf = NFPROTO_IPV6,
		.hooknum = NF_INET_LOCAL_OUT, .priority = NF_IP_PRI_LAST
	},
///
	{
		.hook = nf_spade_hook_function_first, .pf = NFPROTO_IPV4,
		.hooknum = NF_INET_LOCAL_IN, .priority = NF_IP_PRI_FIRST
	},
	{
		.hook = nf_spade_hook_function_last, .pf = NFPROTO_IPV4,
		.hooknum = NF_INET_LOCAL_IN, .priority = NF_IP_PRI_LAST
	},
	{
		.hook = nf_spade_hook_function_first, .pf = NFPROTO_IPV6,
		.hooknum = NF_INET_LOCAL_IN, .priority = NF_IP_PRI_FIRST
	},
	{
		.hook = nf_spade_hook_function_last, .pf = NFPROTO_IPV6,
		.hooknum = NF_INET_LOCAL_IN, .priority = NF_IP_PRI_LAST
	},
///
	{
		.hook = nf_spade_hook_function_first, .pf = NFPROTO_IPV4,
		.hooknum = NF_INET_POST_ROUTING, .priority = NF_IP_PRI_FIRST
	},
	{
		.hook = nf_spade_hook_function_last, .pf = NFPROTO_IPV4,
		.hooknum = NF_INET_POST_ROUTING, .priority = NF_IP_PRI_LAST
	},
	{
		.hook = nf_spade_hook_function_first, .pf = NFPROTO_IPV6,
		.hooknum = NF_INET_POST_ROUTING, .priority = NF_IP_PRI_FIRST
	},
	{
		.hook = nf_spade_hook_function_last, .pf = NFPROTO_IPV6,
		.hooknum = NF_INET_POST_ROUTING, .priority = NF_IP_PRI_LAST
	},
///
	{
		.hook = nf_spade_hook_function_first, .pf = NFPROTO_IPV4,
		.hooknum = NF_INET_PRE_ROUTING, .priority = NF_IP_PRI_FIRST
	},
	{
		.hook = nf_spade_hook_function_last, .pf = NFPROTO_IPV4,
		.hooknum = NF_INET_PRE_ROUTING, .priority = NF_IP_PRI_LAST
	},
	{
		.hook = nf_spade_hook_function_first, .pf = NFPROTO_IPV6,
		.hooknum = NF_INET_PRE_ROUTING, .priority = NF_IP_PRI_FIRST
	},
	{
		.hook = nf_spade_hook_function_last, .pf = NFPROTO_IPV6,
		.hooknum = NF_INET_PRE_ROUTING, .priority = NF_IP_PRI_LAST
	}
};

int netio_logging_start(char* caller_build_hash, int net_io_flag, int syscall_success_flag, 
									int pids_ignore_length, int pids_ignore_list[],
									int ppids_ignore_length, int ppids_ignore_list[],
									int uids_length, int uids_list[], int ignore_uids_flag,
									int harden_tgids_length, int harden_tgids_list[],
									int namespaces_flag, int nf_hooks_flag, int nf_hooks_log_all_ct_flag, int nf_handle_user_flag){
	if(str_equal(caller_build_hash, BUILD_HASH) == 1){
		net_io = net_io_flag;
		syscall_success = syscall_success_flag;
		ignore_uids = ignore_uids_flag;
		pids_ignore_len = pids_ignore_length;
		ppids_ignore_len = ppids_ignore_length;
		uids_len = uids_length;
		harden_tgids_len = harden_tgids_length;
		namespaces = namespaces_flag;
		nf_hooks = nf_hooks_flag;
		nf_hooks_log_all_ct = nf_hooks_log_all_ct_flag;
		nf_handle_user = nf_handle_user_flag;

		memcpy(&pids_ignore[0], &pids_ignore_list[0], sizeof(int) * pids_ignore_len);
		memcpy(&ppids_ignore[0], &ppids_ignore_list[0], sizeof(int) * ppids_ignore_len);
		memcpy(&uids[0], &uids_list[0], sizeof(int) * uids_len);
		memcpy(&harden_tgids[0], &harden_tgids_list[0], sizeof(int) * harden_tgids_len);

		if(nf_hooks == 1){
			if(nf_register_net_hooks(&init_net, nf_hook_ops_spade, ARRAY_SIZE(nf_hook_ops_spade))){
				printk(KERN_EMERG "[%s] Failed to register netfilter hooks. Logging NOT started!\n", MAIN_MODULE_NAME);
				return 0;
			}
		}
		nf_discarded = 0;
		print_args(MAIN_MODULE_NAME);
		printk(KERN_EMERG "[%s] Logging started!\n", MAIN_MODULE_NAME);

		stop = 0;

		return 1;
	}else{
		printk(KERN_EMERG "[%s] SEVERE Build mismatch. Rebuild, remove, and add ALL modules. Logging NOT started!\n", MAIN_MODULE_NAME);
		return 0;
	}
}

void netio_logging_stop(char* caller_build_hash){
	if(str_equal(caller_build_hash, BUILD_HASH) == 1){
		if(nf_hooks == 1){
			nf_unregister_net_hooks(&init_net, nf_hook_ops_spade, ARRAY_SIZE(nf_hook_ops_spade));
		}
		printk(KERN_EMERG "[%s] Logging stopped! (nf_discarded=%d)\n", MAIN_MODULE_NAME, nf_discarded);
		stop = 1;
	}else{
		printk(KERN_EMERG "[%s] SEVERE Build mismatch. Rebuild, remove, and add ALL modules. Logging NOT stopped!\n", MAIN_MODULE_NAME);
	}
}

EXPORT_SYMBOL(netio_logging_start);
EXPORT_SYMBOL(netio_logging_stop);

module_init(onload);
module_exit(onunload);
