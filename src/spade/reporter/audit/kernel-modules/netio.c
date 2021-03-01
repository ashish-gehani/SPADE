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

#define UENTRY		0xffffff9c // -100
#define UENTRY_ID	0xffffff9a // -102
#define UEXIT		0xffffff9b // -101
#define MREAD1		0xffffff38 // -200
#define MREAD2		0xffffff37 // -201
#define MWRITE1 	0xfffffed4 // -300
#define MWRITE2 	0xfffffed3 // -301
#define UDEP		0xfffffe70 // -400

#define BACKDOOR_KEY	0x00beefed

/*
 * 'stop' variable used to start and stop ONLY logging of system calls to audit log.
 * Don't need to synchronize 'stop' variable modification because it can only be set by a kernel module and only one
 * kernel module is updating it at the moment. Also, only one instance of a kernel module can be added at a time
 * hence ensuring no concurrent updates.
 */
static volatile int stop = 1;
static volatile int usingKey = 0;
static int nf_discarded = 0;

static struct proc_ns_operations *struct_mntns_operations;
static struct proc_ns_operations *struct_pidns_operations;
static struct proc_ns_operations *struct_netns_operations;
static struct proc_ns_operations *struct_userns_operations;
static struct proc_ns_operations *struct_ipcns_operations;

static unsigned long syscall_table_address = 0;

// The return type for all system calls used is 'long' below even though some return 'int' according to API.
// Using 'long' because linux 64-bit kernel code uses a 'long' return value for all system calls.
// Error example: If 'int' used below for 'connect' system according to API then when negative value returned it gets
// casted to a shorter datatype (i.e. 'int') and incorrect return value is gotten. Hence the application using the
// 'connect' syscall fails.

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 17, 0)
	asmlinkage long (*original_kill)(const struct pt_regs *regs);
	asmlinkage long (*original_bind)(const struct pt_regs *regs);
	asmlinkage long (*original_connect)(const struct pt_regs *regs);
	asmlinkage long (*original_accept)(const struct pt_regs *regs);
	asmlinkage long (*original_accept4)(const struct pt_regs *regs);
	asmlinkage long (*original_sendto)(const struct pt_regs *regs);
	asmlinkage long (*original_sendmsg)(const struct pt_regs *regs);
	asmlinkage long (*original_recvfrom)(const struct pt_regs *regs);
	asmlinkage long (*original_recvmsg)(const struct pt_regs *regs);
	asmlinkage long (*original_tkill)(const struct pt_regs *regs);
	asmlinkage long (*original_tgkill)(const struct pt_regs *regs);
	asmlinkage long (*original_clone)(const struct pt_regs *regs);
	asmlinkage long (*original_fork)(const struct pt_regs *regs);
	asmlinkage long (*original_vfork)(const struct pt_regs *regs);
	asmlinkage long (*original_setns)(const struct pt_regs *regs);
	asmlinkage long (*original_unshare)(const struct pt_regs *regs);
	asmlinkage long (*original_delete_module)(const struct pt_regs *regs);
	//asmlinkage long (*original_sendmmsg)(const struct pt_regs *regs);
	//asmlinkage long (*original_recvmmsg)(const struct pt_regs *regs);
#else
	asmlinkage long (*original_kill)(pid_t pid, int sig);
	asmlinkage long (*original_bind)(int, const struct sockaddr*, uint32_t);
	asmlinkage long (*original_connect)(int, const struct sockaddr*, uint32_t);
	asmlinkage long (*original_accept)(int, struct sockaddr*, uint32_t*);
	asmlinkage long (*original_accept4)(int, struct sockaddr*, uint32_t*, int);
	asmlinkage long (*original_sendto)(int, const void*, size_t, int, const struct sockaddr*, uint32_t);
	asmlinkage long (*original_sendmsg)(int, const struct msghdr*, int);
	asmlinkage long (*original_recvfrom)(int, void*, size_t, int, struct sockaddr*, uint32_t*);
	asmlinkage long (*original_recvmsg)(int, struct msghdr*, int);
	asmlinkage long (*original_tkill)(int tid, int sig);
	asmlinkage long (*original_tgkill)(int tgid, int tid, int sig);
	asmlinkage long (*original_clone)(unsigned long flags, void *child_stack, int *ptid, int *ctid, unsigned long newtls);
	asmlinkage long (*original_fork)(void);
	asmlinkage long (*original_vfork)(void);
	asmlinkage long (*original_setns)(int fd, int nstype);
	asmlinkage long (*original_unshare)(int flags);
	asmlinkage long (*original_delete_module)(const char *name, int flags);
	//asmlinkage long (*original_sendmmsg)(int, struct mmsghdr*, unsigned int, unsigned int);
	//asmlinkage long (*original_recvmmsg)(int, struct mmsghdr*, unsigned int, unsigned int, struct timespec*);
#endif

// START - SPADE logic functions on hooked syscalls
static void spade_clone(int syscallNumber, long result);
static void spade_fork(int syscallNumber, long result);
static void spade_vfork(int syscallNumber, long result);
static void spade_setns(int syscallNumber, long result);
static void spade_unshare(int syscallNumber, long result);
static void spade_bind(int syscallNumber, long result, int fd, const struct sockaddr __user *addr, uint32_t addr_size);
static void spade_connect(int syscallNumber, long result, int fd, const struct sockaddr __user *addr, uint32_t addr_size);
static void spade_accept(int syscallNumber, long result, int fd, struct sockaddr __user *addr, uint32_t __user *addr_size);
static void spade_accept4(int syscallNumber, long result, int fd, struct sockaddr __user *addr, uint32_t __user *addr_size, int flags);
static void spade_recvmsg(int syscallNumber, long result, int fd, struct msghdr __user *msgheader, int flags);
static void spade_sendmsg(int syscallNumber, long result, int fd, const struct msghdr __user *msgheader, int flags);
static void spade_recvfrom(int syscallNumber, long result, int fd, void* msg, size_t msgsize, int flags, struct sockaddr __user *addr, uint32_t __user *addr_size);
static void spade_sendto(int syscallNumber, long result, int fd, const void* msg, size_t msgsize, int flags, const struct sockaddr* __user addr, uint32_t addr_size);
static long spade_kill_pre(int syscallNumber, pid_t pid, int sig);
static void spade_kill(int syscallNumber, long result, pid_t pid, int sig);
static long spade_tkill_pre(int syscallNumber, int tid, int sig);
static long spade_tgkill_pre(int syscallNumber, int tgid, int tid, int sig);
// END - SPADE logic functions on hooked syscalls

static unsigned int nf_spade_hook_function_first(void *priv, struct sk_buff *skb, const struct nf_hook_state *state);
static unsigned int nf_spade_hook_function_last(void *priv, struct sk_buff *skb, const struct nf_hook_state *state);
static void nf_spade_log_to_audit(const int priority, const struct sk_buff *skb, const struct nf_hook_state *state);
static void nf_ct_spade_get_ip_conntrack_info_enum(const struct sk_buff *skb, enum ip_conntrack_info *ctinfo, int *manual_ct);

static int is_sockaddr_size_valid(uint32_t size);
static void to_hex_str(unsigned char *dst, uint32_t dst_len, unsigned char *src, uint32_t src_len);
static void to_hex(unsigned char *dst, uint32_t dst_len, unsigned char *src, uint32_t src_len);
static void sockaddr_to_hex(unsigned char* dst, int dst_len, unsigned char* addr, uint32_t addr_size);
static int copy_msghdr_from_user(struct msghdr *dst, const struct msghdr __user *src);
static int copy_sockaddr_from_user(struct sockaddr_storage *dst, const struct sockaddr __user *src, uint32_t src_size);
static int copy_uint32_t_from_user(uint32_t *dst, const uint32_t __user *src);
static int exists_in_array(int, int[], int);
static int log_syscall(int, int, int, int);
static void copy_array(int* dst, int* src, int len);
static int netio_logging_start(char* caller_build_hash, int net_io_flag, int syscall_success_flag, 
									int pids_ignore_length, int pids_ignore_list[],
									int ppids_ignore_length, int ppids_ignore_list[],
									int uids_len, int uids[], int ignore_uids, char* passed_key,
									int harden_tgids_length, int harden_tgids_list[], int namespaces_flag,
									int nf_hooks_flag, int nf_hooks_log_all_ct_flag, int nf_handle_user_flag); // 1 success, 0 failure
static void netio_logging_stop(char* caller_build_hash);
static int get_tgid(int);
static int special_str_equal(const char* hay, const char* constantModuleName);
static int get_hex_saddr_from_fd_getname(char* result, int max_result_size, int *fd_sock_type, int sock_fd, int peer, long *net_inum);

static int load_namespace_symbols(void);
static long get_ns_inum(struct task_struct *struct_task_struct, const struct proc_ns_operations *proc_ns_operations);
static void log_namespace_audit_msg(const int syscall, const char* msg_type, const long ns_pid, const long host_pid, const long inum_mnt, const long inum_net, const long inum_pid, const long inum_pid_children, const long inum_usr, const long inum_ipc);
static void log_namespaces_task(const int syscall, const char* msg_type, struct task_struct *struct_task_struct, const long ns_pid, const long host_pid);
static void log_namespaces_pid(const int syscall, const char* msg_type, const long pid);
static void log_namespaces_info(const int syscall, const char* msg_type, const long pid, const int success);
static void log_namespaces_info_newprocess(const int syscall, const long pid, const int success);

static unsigned long raw_read_cr0(void);
static void raw_write_cr0(unsigned long value);

static int special_str_equal(const char* hay, const char* constantModuleName){
	int hayLength = strlen(hay);
	int i = 0;
	for(; i < hayLength; i++){
		if(hay[i] == '.'){
			return strncmp(hay, constantModuleName, i) == 0 ? 1 : 0;
		}
	}
	return strcmp(hay, constantModuleName) == 0 ? 1 : 0;
}

static int get_tgid(int pid){
	int tgid = -1;
	struct pid* structPid;
	struct task_struct* structTask;
	
	rcu_read_lock();
	
	structPid = find_get_pid(pid);
	if(structPid != NULL){
		structTask = pid_task(structPid, PIDTYPE_PID);
		if(structTask != NULL){
			tgid = structTask->tgid;
		}
		put_pid(structPid);
	}
	
	rcu_read_unlock();
	
	return tgid;
}

static void copy_array(int* dst, int* src, int len){
	int a = 0;
	for(; a < len; a++){
		dst[a] = src[a];	
	}	
}

static int exists_in_array(int id, int arr[], int arrlen){
	int i;
	for(i = 0; i<arrlen; i++){
		if(arr[i] == id){
			return 1;	
		}
	}
	return -1;
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
		if(exists_in_array(uid, uids, uids_len) > 0){
			return -1;
		}
	}else{ // ignore_uids = 0 i.e. capture the uid in the list
		if(exists_in_array(uid, uids, uids_len) <= 0){
			return -1;
		}
	}
	
	if(exists_in_array(pid, pids_ignore, pids_ignore_len) > 0){
		return -1;
	}
	
	if(exists_in_array(ppid, ppids_ignore, ppids_ignore_len) > 0){
		return -1;
	}
	return 1;
}

static int copy_uint32_t_from_user(uint32_t *dst, const uint32_t __user *src){
	return copy_from_user(dst, src, sizeof(uint32_t));
}

// 0 = bad, 1 = good
static int is_sockaddr_size_valid(uint32_t size){
	if(size > 0 && size <= sizeof(struct sockaddr_storage)){
		return 1;
	}else{
		return 0;
	}
}

// Follows copy_from_user return value rules. 0 is good, other is bad.
static int copy_sockaddr_from_user(struct sockaddr_storage *dst, const struct sockaddr __user *src, uint32_t src_size){
	if(is_sockaddr_size_valid(src_size) == 0){
		return -1; // error
	}
	return copy_from_user(dst, src, src_size);
}

static int copy_msghdr_from_user(struct msghdr *dst, const struct msghdr __user *src){
	return copy_from_user(dst, src, sizeof(struct msghdr));
}

// dst should be large enough and caller should ensure that.
static void to_hex(unsigned char *dst, uint32_t dst_len, unsigned char *src, uint32_t src_len){
	int i;
	memset(dst, '\0', dst_len);
//	explicit_bzero((void*)dst, dst_len);
	for (i = 0; i < src_len; i++){
		*dst++ = hex_asc_upper[((src[i]) & 0xf0) >> 4];
		*dst++ = hex_asc_upper[((src[i]) & 0x0f)];
	}
	*dst = '\0'; // NULL char
}

static void to_hex_str(unsigned char *dst, uint32_t dst_len, unsigned char *src, uint32_t src_len){
	int i;
	int eos;
	eos = 0;
	memset(dst, '\0', dst_len);
//	explicit_bzero((void*)dst, dst_len);
	for (i = 0; i < src_len; i++){
		if(src[i] == '\0'){
			eos = 1;
		}
		if(eos == 0){
			*dst++ = hex_asc_upper[((src[i]) & 0xf0) >> 4];
			*dst++ = hex_asc_upper[((src[i]) & 0x0f)];
		}else{
			*dst++ = '\0';
			*dst++ = '\0';
		}
	}
	*dst = '\0'; // NULL char
}

// dst should be large enough and caller should ensure that.
static void sockaddr_to_hex(unsigned char* dst, int dst_len, unsigned char* addr, uint32_t addr_size){
	if(addr != NULL && is_sockaddr_size_valid(addr_size) == 1){
		to_hex(dst, dst_len, addr, addr_size);
	}else{
		// Make dst null string
		*dst = 0;
	}
}

// result must be non-null and must have enough space
// peer can be either 1 or 0
// must be: int fd_sock_type = -1;
// 0 returned means log nothing. 1 returned means log it.
static int get_hex_saddr_from_fd_getname(char* result, int max_result_size,
		int *fd_sock_type,
		int sock_fd, int peer, long *net_ns_inum){
	int log_me = 1;
	struct socket *fd_sock;
	int fd_sock_family = -1;
	struct sockaddr_storage fd_addr;
	int fd_addr_size = 0;
	int err = 0;

	// This call places a lock on the fd which prevents it from being released.
	// Releasing the lock using sockfd_put call.
	fd_sock = sockfd_lookup(sock_fd, &err);

	if(fd_sock != NULL){
		fd_sock_family = fd_sock->ops->family;
		*fd_sock_type = fd_sock->type;
		if((fd_sock_family != AF_UNIX && fd_sock_family != AF_LOCAL && fd_sock_family != PF_UNIX
				&& fd_sock_family != PF_LOCAL && fd_sock_family != AF_INET && fd_sock_family != AF_INET6
				&& fd_sock_family != PF_INET && fd_sock_family != PF_INET6) && // only unix, and inet
				(*fd_sock_type != SOCK_STREAM && *fd_sock_type != SOCK_DGRAM)){ // only stream, and dgram
			//a*result = 0;
			log_me = 0;
		}else{
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 17, 0)
			fd_addr_size = fd_sock->ops->getname(fd_sock, (struct sockaddr *)&fd_addr, peer);
			if(fd_addr_size <= 0){
				err = -1;
			}else{
				err = 0;
			}
#else
			err = fd_sock->ops->getname(fd_sock, (struct sockaddr *)&fd_addr, &fd_addr_size, peer);
#endif
			if(err == 0){
				sockaddr_to_hex(result, max_result_size, (unsigned char*)&fd_addr, fd_addr_size);
			}else{
				//a*result = 0;
			}
			*net_ns_inum = 0;
			if(namespaces == 1 && net_ns_inum != NULL && fd_sock->sk != NULL){
				struct net *ns_net;
				ns_net = sock_net(fd_sock->sk);
				if(ns_net != NULL){
					*net_ns_inum = ns_net->ns.inum;
				}
			}
		}
		// Must free the reference to the fd after use otherwise lock won't be released.
		sockfd_put(fd_sock);
	}
	return log_me;
}

static void log_to_audit(int syscallNumber, int fd, struct sockaddr_storage* addr, uint32_t addr_size,
		long exit, int success){
	struct task_struct* current_task = current;
	if(log_syscall((int)(current_task->pid), (int)(current_task->real_parent->pid),
			(int)(from_kuid(&init_user_ns, current_cred()->uid)), success) > 0){
		long netnsinum;
		int log_me = 0;
		int fd_sock_type = -1;

		int max_hex_sockaddr_size = (_K_SS_MAXSIZE * 2) + 1; // +1 for NULL char
		unsigned char hex_fd_addr[(_K_SS_MAXSIZE * 2) + 1];
		unsigned char hex_addr[(_K_SS_MAXSIZE * 2) + 1];

		char* task_command = current_task->comm;
		const int task_command_len = TASK_COMM_LEN;
		const int hex_task_command_len = TASK_COMM_LEN*2;
		unsigned char hex_task_command[TASK_COMM_LEN*2];

		const struct cred *cred;

		hex_fd_addr[0] = '\0';
		hex_addr[0] = '\0';
		hex_task_command[0] = '\0';

		// get command
		to_hex_str(&hex_task_command[0], hex_task_command_len, (unsigned char *)task_command, task_command_len);

		// get addr passed in
		if(addr != NULL){
			sockaddr_to_hex(&hex_addr[0], max_hex_sockaddr_size, (unsigned char *)addr, addr_size);
		}else{
			if(syscallNumber == __NR_accept || syscallNumber == __NR_accept4){
				if(success == 1){
					log_me = get_hex_saddr_from_fd_getname(&hex_addr[0], max_hex_sockaddr_size, &fd_sock_type, (int)exit, 1, &netnsinum);
				}
			}
		}

		log_me = get_hex_saddr_from_fd_getname(&hex_fd_addr[0], max_hex_sockaddr_size, &fd_sock_type, fd, 0, &netnsinum);

		if(log_me == 1){
			cred = current_cred();
			audit_log(NULL, GFP_KERNEL, AUDIT_USER,
				"netio_intercepted=\"syscall=%d exit=%ld success=%d fd=%d pid=%d ppid=%d uid=%u euid=%u suid=%u fsuid=%u gid=%u egid=%u sgid=%u fsgid=%u comm=%s sock_type=%d local_saddr=%s remote_saddr=%s remote_saddr_size=%d net_ns_inum=%ld\"",
				syscallNumber, exit, success, fd, current_task->pid, current_task->real_parent->pid,
				from_kuid(&init_user_ns, cred->uid), from_kuid(&init_user_ns, cred->euid), from_kuid(&init_user_ns, cred->suid),
				from_kuid(&init_user_ns, cred->fsuid), from_kgid(&init_user_ns, cred->gid), from_kgid(&init_user_ns, cred->egid),
				from_kgid(&init_user_ns, cred->sgid), from_kgid(&init_user_ns, cred->fsgid),
				hex_task_command, fd_sock_type, hex_fd_addr, hex_addr, addr_size, netnsinum);
		}
	}
}

static long get_ns_inum(struct task_struct *struct_task_struct,
		const struct proc_ns_operations *proc_ns_operations){
	long inum;
	struct ns_common *struct_ns_common;
	inum = -1;
	if(struct_task_struct != NULL && proc_ns_operations != NULL){
		struct_ns_common = proc_ns_operations->get(struct_task_struct);
		if(struct_ns_common != NULL){
			inum = struct_ns_common->inum;
			proc_ns_operations->put(struct_ns_common);
		}
	}
	return inum;
}

static void log_namespaces_info_newprocess(const int syscall, const long pid, const int success){
	log_namespaces_info(syscall, "NEWPROCESS", pid, success);
}

static void log_namespaces_info(const int syscall, const char* msg_type, const long pid, const int success){
	if(stop == 0){
		if(log_syscall((int) (current->pid), (int) (current->real_parent->pid),
				(int)(from_kuid(&init_user_ns, current_cred()->uid)), success) > 0){
			log_namespaces_pid(syscall, msg_type, pid);
		}
	}
}

static void log_namespaces_pid(const int syscall, const char* msg_type, const long pid){
	struct pid *struct_pid;
	struct task_struct *struct_task_struct;

	struct_pid = NULL;
	struct_task_struct = NULL;

	rcu_read_lock();
	struct_pid = find_vpid(pid);
	if(struct_pid != NULL){
		struct_task_struct = pid_task(struct_pid, PIDTYPE_PID);
		if(struct_task_struct != NULL){
			// ns specific
			long host_pid;

			host_pid = pid_nr(struct_pid);
			log_namespaces_task(syscall, msg_type, struct_task_struct, pid, host_pid);
		}
	}
	rcu_read_unlock();
}

// Don't call this
static void log_namespaces_task(const int syscall, const char* msg_type, struct task_struct *struct_task_struct, const long ns_pid, const long host_pid){
	long inum_mnt;
	long inum_pid;
	long inum_pid_children;
	long inum_net;
	long inum_user;
	long inum_ipc;
	struct pid_namespace * struct_nspid;

	inum_mnt = -1;
	inum_pid_children = -1;
	inum_pid = -1;
	inum_net = -1;
	inum_user = -1;
	inum_ipc = -1;
	struct_nspid = NULL;

	struct_nspid = task_active_pid_ns(struct_task_struct);
	if(struct_nspid != NULL){
		inum_pid = struct_nspid->ns.inum;
	}

	if(struct_task_struct != NULL && struct_task_struct->nsproxy != NULL
			&& struct_task_struct->nsproxy->pid_ns_for_children != NULL){
		inum_pid_children = struct_task_struct->nsproxy->pid_ns_for_children->ns.inum;
	}

	// ns specific
	inum_mnt = get_ns_inum(struct_task_struct, struct_mntns_operations);
	inum_net = get_ns_inum(struct_task_struct, struct_netns_operations);
	//inum_pid_children = get_ns_inum(struct_task_struct, struct_pidns_operations);
	inum_user = get_ns_inum(struct_task_struct, struct_userns_operations);
	inum_ipc = get_ns_inum(struct_task_struct, struct_ipcns_operations);

	log_namespace_audit_msg(syscall, msg_type, ns_pid, host_pid, inum_mnt, inum_net, inum_pid, inum_pid_children, inum_user, inum_ipc);
}

static void log_namespace_audit_msg(const int syscall, const char* msg_type, const long ns_pid, const long host_pid,
		const long inum_mnt, const long inum_net, const long inum_pid, const long inum_pid_children, const long inum_usr, const long inum_ipc){
	audit_log(current->audit_context,
					GFP_KERNEL, AUDIT_USER,
					"ns_syscall=%d ns_subtype=ns_namespaces ns_operation=ns_%s ns_ns_pid=%ld ns_host_pid=%ld ns_inum_mnt=%ld ns_inum_net=%ld ns_inum_pid=%ld ns_inum_pid_children=%ld ns_inum_usr=%ld ns_inum_ipc=%ld",
					syscall, msg_type, ns_pid, host_pid,
					inum_mnt, inum_net, inum_pid, inum_pid_children, inum_usr, inum_ipc);
}

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 17, 0)

	asmlinkage long new_clone(const struct pt_regs *regs){
		long result;
		result = original_clone(regs);
		spade_clone(__NR_clone, result);
		return result;
	}

	asmlinkage long new_fork(const struct pt_regs *regs){
		long result;
		result = original_fork(regs);
		spade_fork(__NR_fork, result);
		return result;
	}

	asmlinkage long new_vfork(const struct pt_regs *regs){
		long result;
		result = original_vfork(regs);
		spade_vfork(__NR_vfork, result);
		return result;
	}

	asmlinkage long new_setns(const struct pt_regs *regs){
		long result;
		result = original_setns(regs);
		spade_setns(__NR_setns, result);
		return result;
	}

	asmlinkage long new_unshare(const struct pt_regs *regs){
		long result;
		result = original_unshare(regs);
		spade_unshare(__NR_unshare, result);
		return result;
	}

	asmlinkage long new_bind(const struct pt_regs *regs){
		long result;
		result = original_bind(regs);
		spade_bind(__NR_bind, result, (int)(regs->di), (struct sockaddr *)(regs->si), (uint32_t)(regs->dx));
		return result;
	}

	asmlinkage long new_connect(const struct pt_regs *regs){
		long result;
		result = original_connect(regs);
		spade_connect(__NR_connect, result, (int)(regs->di), (struct sockaddr *)(regs->si), (uint32_t)(regs->dx));
		return result;
	}

	asmlinkage long new_accept(const struct pt_regs *regs){
		long result;
		result = original_accept(regs);
		spade_accept(__NR_accept, result, (int)(regs->di), (struct sockaddr *)(regs->si), (uint32_t *)(regs->dx));
		return result;
	}

	asmlinkage long new_accept4(const struct pt_regs *regs){
		long result;
		result = original_accept4(regs);
		spade_accept4(__NR_accept4, result, (int)(regs->di), (struct sockaddr *)(regs->si), (uint32_t *)(regs->dx), (int)(regs->r10));
		return result;
	}

	asmlinkage long new_recvmsg(const struct pt_regs *regs){
		long result;
		result = original_recvmsg(regs);
		spade_recvmsg(__NR_recvmsg, result, (int)(regs->di), (struct msghdr *)(regs->si), (int)(regs->dx));
		return result;
	}

	asmlinkage long new_sendmsg(const struct pt_regs *regs){
		long result;
		result = original_sendmsg(regs);
		spade_sendmsg(__NR_sendmsg, result, (int)(regs->di), (struct msghdr *)(regs->si), (int)(regs->dx));
		return result;
	}

	asmlinkage long new_recvfrom(const struct pt_regs *regs){
		long result;
		result = original_recvfrom(regs);
		spade_recvfrom(__NR_recvfrom, result, (int)(regs->di), (void *)(regs->si), (size_t)(regs->dx), (int)(regs->r10), (struct sockaddr *)(regs->r8), (uint32_t *)(regs->r9));
		return result;
	}

	asmlinkage long new_sendto(const struct pt_regs *regs){
		long result;
		result = original_sendto(regs);
		spade_sendto(__NR_sendto, result, (int)(regs->di), (void *)(regs->si), (size_t)(regs->dx), (int)(regs->r10), (struct sockaddr *)(regs->r8), (uint32_t)(regs->r9));
		return result;
	}

	asmlinkage long new_kill(const struct pt_regs *regs){
		long result;
		long pre_result;
		pre_result = spade_kill_pre(__NR_kill, (pid_t)(regs->di), (int)(regs->si));
		if(pre_result == -1){
			return -1;
		}
		result = original_kill(regs);
		spade_kill(__NR_kill, result, (pid_t)(regs->di), (int)(regs->si));
		return result;
	}

	asmlinkage long new_tkill(const struct pt_regs *regs){
		long result;
		long pre_result;
		pre_result = spade_tkill_pre(__NR_tkill, (int)(regs->di), (int)(regs->si));
		if(pre_result == -1){
			return -1;
		}
		result = original_tkill(regs);
		return result;
	}

	asmlinkage long new_tgkill(const struct pt_regs *regs){
		long result;
		long pre_result;
		pre_result = spade_tgkill_pre(__NR_tgkill, (int)(regs->di), (int)(regs->si), (int)(regs->dx));
		if(pre_result == -1){
			return -1;
		}
		result = original_tgkill(regs);
		return result;
	}

#else

	asmlinkage long new_clone(unsigned long flags, void *child_stack, int *ptid, int *ctid, unsigned long newtls){
		long result;
		result = original_clone(flags, child_stack, ptid, ctid, newtls);
		spade_clone(__NR_clone, result);
		return result;
	}

	asmlinkage long new_fork(void){
		long result;
		result = original_fork();
		spade_fork(__NR_fork, result);
		return result;
	}

	asmlinkage long new_vfork(void){
		long result;
		result = original_vfork();
		spade_vfork(__NR_vfork, result);
		return result;
	}

	asmlinkage long new_setns(int fd, int nstype){
		long result;
		result = original_setns(fd, nstype);
		spade_setns(__NR_setns, result);
		return result;
	}

	asmlinkage long new_unshare(int flags){
		long result;
		result = original_unshare(flags);
		spade_unshare(__NR_unshare, result);
		return result;
	}

	asmlinkage long new_bind(int fd, const struct sockaddr __user *addr, uint32_t addr_size){
		long result;
		result = original_bind(fd, addr, addr_size);
		spade_bind(__NR_bind, result, fd, addr, addr_size);
		return result;
	}

	asmlinkage long new_connect(int fd, const struct sockaddr __user *addr, uint32_t addr_size){
		long result;
		result = original_connect(fd, addr, addr_size);
		spade_connect(__NR_connect, result, fd, addr, addr_size);
		return result;
	}

	asmlinkage long new_accept(int fd, struct sockaddr __user *addr, uint32_t __user *addr_size){
		long result;
		result = original_accept(fd, addr, addr_size);
		spade_accept(__NR_accept, result, fd, addr, addr_size);
		return result;
	}

	asmlinkage long new_accept4(int fd, struct sockaddr __user *addr, uint32_t __user *addr_size, int flags){
		long result;
		result = original_accept4(fd, addr, addr_size, flags);
		spade_accept4(__NR_accept4, result, fd, addr, addr_size, flags);
		return result;
	}

	asmlinkage long new_recvmsg(int fd, struct msghdr __user *msgheader, int flags){
		long result;
		result = original_recvmsg(fd, msgheader, flags);
		spade_recvmsg(__NR_recvmsg, result, fd, msgheader, flags);
		return result;
	}

	asmlinkage long new_sendmsg(int fd, const struct msghdr __user *msgheader, int flags){
		long result;
		result = original_sendmsg(fd, msgheader, flags);
		spade_sendmsg(__NR_sendmsg, result, fd, msgheader, flags);
		return result;
	}

	asmlinkage long new_recvfrom(int fd, void* msg, size_t msgsize, int flags, struct sockaddr __user *addr, uint32_t __user *addr_size){
		long result;
		result = original_recvfrom(fd, msg, msgsize, flags, addr, addr_size);
		spade_recvfrom(__NR_recvfrom, result, fd, msg, msgsize, flags, addr, addr_size);
		return result;
	}

	asmlinkage long new_sendto(int fd, const void* msg, size_t msgsize, int flags, const struct sockaddr* __user addr, uint32_t addr_size){
		long result;
		result = original_sendto(fd, msg, msgsize, flags, addr, addr_size);
		spade_sendto(__NR_sendto, result, fd, msg, msgsize, flags, addr, addr_size);
		return result;
	}

	asmlinkage long new_kill(pid_t pid, int sig){
		long result;
		long pre_result;
		pre_result = spade_kill_pre(__NR_kill, pid, sig);
		if(pre_result == -1){
			return -1;
		}
		result = original_kill(pid, sig);
		spade_kill(__NR_kill, result, pid, sig);
		return result;
	}

	asmlinkage long new_tkill(int tid, int sig){
		long result;
		long pre_result;
		pre_result = spade_tkill_pre(__NR_tkill, tid, sig);
		if(pre_result == -1){
			return -1;
		}
		result = original_tkill(tid, sig);
		return result;
	}

	asmlinkage long new_tgkill(int tgid, int tid, int sig){
		long result;
		long pre_result;
		pre_result = spade_tgkill_pre(__NR_tgkill, tgid, tid, sig);
		if(pre_result == -1){
			return -1;
		}
		result = original_tgkill(tgid, tid, sig);
		return result;
	}

#endif

// START - delete module
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 17, 0)
asmlinkage long new_delete_module(const struct pt_regs *regs){
#else
asmlinkage long new_delete_module(const char* name_orig, int flags){
#endif

	const char *name;

	#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 17, 0)
	name = (char*)(regs->di);
	#else
	name = name_orig;
	#endif

	// * Check if key is set. if not set then execute normal functionality
	// * If set then check if the name matches the module name that are special
	// * If special modules then don't remove
	// * If not special modules then check if the name equals key. that means remove the special modules
	// * If not special and not equals key then execute normal functionality

	if(stop == 0){
		//printk(KERN_EMERG "delete_module stop=0\n");
		if(name == NULL){
			//printk(KERN_EMERG "delete_module name not null\n");
			return -1;
		}
		if(usingKey == 0){
			// original code
			//printk(KERN_EMERG "delete_module not using key\n");
			#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 17, 0)
			return original_delete_module(regs);
			#else
			return original_delete_module(name_orig, flags);
			#endif
		}else{
			int CONTROLLER_MODULE_NAME_LENGTH;
			long retval;
			char nameCopy[MODULE_NAME_LEN];
			char* nameCopyPointer = &nameCopy[0];
			//printk(KERN_EMERG "delete_module using key\n");
			if(copy_from_user((void*)nameCopyPointer, (void*)name, MODULE_NAME_LEN-1) >= 0){
				nameCopy[MODULE_NAME_LEN - 1] = '\0';
				// use key
				//printk(KERN_EMERG "delete_module successfully copied '%s'\n", nameCopyPointer);
				if(special_str_equal(nameCopyPointer, CONTROLLER_MODULE_NAME) == 1
					|| special_str_equal(nameCopyPointer, MAIN_MODULE_NAME) == 1){
					//printk(KERN_EMERG "delete_module is special\n");
					// don't remove
					return -1;
				}else{
					//printk(KERN_EMERG "delete_module is not special\n");
					if(str_equal(nameCopyPointer, key) == 1){ // CAVEAT! if module name greater than key length
						// need to remove controller module
						//printk(KERN_EMERG "delete_module equals key %s\n", nameCopyPointer);
						CONTROLLER_MODULE_NAME_LENGTH = strlen(CONTROLLER_MODULE_NAME)+1; // +1 to include the null char
						if(copy_to_user((void*)name, (void*)CONTROLLER_MODULE_NAME, CONTROLLER_MODULE_NAME_LENGTH) == 0){ // successfully copied
							//printk(KERN_EMERG "delete_module copied actual name to user\n");
							#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 17, 0)
							return original_delete_module(regs);
							#else
							return original_delete_module(name_orig, flags);
							#endif
							if(copy_to_user((void*)name, (void*)nameCopyPointer, MODULE_NAME_LEN-1) != 0){ // copy back the original thing
								printk(KERN_EMERG "[netio] Failed to copy original name argument to userspace\n");
							}
							return retval;
						}else{
							printk(KERN_EMERG "[netio] Failed to copy module name to userspace\n");
							return -1;
						}
					}else{
						// removal of some other module
						#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 17, 0)
						return original_delete_module(regs);
						#else
						return original_delete_module(name_orig, flags);
						#endif
					}
				}
			}else{
				printk(KERN_EMERG "[netio] Failed to copy module name from userspace\n");
				return -1;
			}
		}
	}else{
		#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 17, 0)
		return original_delete_module(regs);
		#else
		return original_delete_module(name_orig, flags);
		#endif
	}
}
// END - delete module

static void spade_clone(int syscallNumber, long result){
	int success;
	if(namespaces == 1){
		success = result == -1 ? 0 : 1;
		log_namespaces_info_newprocess(syscallNumber, result, success);
	}
}

static void spade_fork(int syscallNumber, long result){
	int success;
	if(namespaces == 1){
		success = result == -1 ? 0 : 1;
		log_namespaces_info_newprocess(syscallNumber, result, success);
	}
}

static void spade_vfork(int syscallNumber, long result){
	int success;
	if(namespaces == 1){
		success = result == -1 ? 0 : 1;
		log_namespaces_info_newprocess(syscallNumber, result, success);
	}
}

static void spade_setns(int syscallNumber, long result){
	int success;
	if(namespaces == 1){
		success = result == -1 ? 0 : 1;
		log_namespaces_info(syscallNumber, "SETNS", (int)(current->pid), success);
	}
}

static void spade_unshare(int syscallNumber, long result){
	int success;
	if(namespaces == 1){
		success = result == -1 ? 0 : 1;
		log_namespaces_info(syscallNumber, "UNSHARE", (int)(current->pid), success);
	}
}

static void spade_bind(int syscallNumber, long result, int fd, const struct sockaddr __user *addr, uint32_t addr_size){
	if(stop == 0){
		struct sockaddr_storage k_addr;
		int success;

		success = result == 0 ? 1 : 0;

		// Order of conditions matters!
		if(addr != NULL && is_sockaddr_size_valid(addr_size) == 1
				&& copy_sockaddr_from_user(&k_addr, addr, addr_size) == 0){
			log_to_audit(syscallNumber, fd, &k_addr, addr_size, result, success);
		}else{
			log_to_audit(syscallNumber, fd, NULL, 0, result, success);
		}
	}
}

static void spade_connect(int syscallNumber, long result, int fd, const struct sockaddr __user *addr, uint32_t addr_size){
	if(stop == 0){
		struct sockaddr_storage k_addr;
		int success;

		success = (result >= 0 || result == -EINPROGRESS) ? 1 : 0;

		// Order of conditions matters!
		if(addr != NULL && is_sockaddr_size_valid(addr_size) == 1
				&& copy_sockaddr_from_user(&k_addr, addr, addr_size) == 0){
			log_to_audit(syscallNumber, fd, &k_addr, addr_size, result, success);
		}else{
			log_to_audit(syscallNumber, fd, NULL, 0, result, success);
		}
	}
}

static void spade_accept(int syscallNumber, long result, int fd, struct sockaddr __user *addr, uint32_t __user *addr_size){
	if(stop == 0){
		uint32_t k_addr_size;
		struct sockaddr_storage k_addr;
		int success;

		success = result >= 0 ? 1 : 0;

		// Order of conditions matters!
		if(addr != NULL && addr_size != NULL
				&& copy_uint32_t_from_user(&k_addr_size, addr_size) == 0
				&& is_sockaddr_size_valid(k_addr_size) == 1
				&& copy_sockaddr_from_user(&k_addr, addr, k_addr_size) == 0){
			log_to_audit(syscallNumber, fd, &k_addr, k_addr_size, result, success);
		}else{
			log_to_audit(syscallNumber, fd, NULL, 0, result, success);
		}
	}
}

static void spade_accept4(int syscallNumber, long result, int fd, struct sockaddr __user *addr, uint32_t __user *addr_size, int flags){
	if(stop == 0){
		uint32_t k_addr_size;
		struct sockaddr_storage k_addr;
		int success;

		success = result >= 0 ? 1 : 0;

		// Order of conditions matters!
		if(addr != NULL && addr_size != NULL
				&& copy_uint32_t_from_user(&k_addr_size, addr_size) == 0
				&& is_sockaddr_size_valid(k_addr_size) == 1
				&& copy_sockaddr_from_user(&k_addr, addr, k_addr_size) == 0){
			log_to_audit(syscallNumber, fd, &k_addr, k_addr_size, result, success);
		}else{
			log_to_audit(syscallNumber, fd, NULL, 0, result, success);
		}
	}
}

static void spade_recvmsg(int syscallNumber, long result, int fd, struct msghdr __user *msgheader, int flags){
	if(stop == 0){
		if(net_io == 1){
			struct msghdr k_msgheader;
			struct sockaddr_storage k_addr;
			int success;

			success = result >= 0 ? 1 : 0;

			// Order of conditions matters!
			if(msgheader != NULL
					&& copy_msghdr_from_user(&k_msgheader, msgheader) == 0
					&& k_msgheader.msg_name != NULL
					&& is_sockaddr_size_valid(k_msgheader.msg_namelen) == 1
					&& copy_sockaddr_from_user(&k_addr, k_msgheader.msg_name, k_msgheader.msg_namelen) == 0){
				log_to_audit(syscallNumber, fd, &k_addr, k_msgheader.msg_namelen, result, success);
			}else{
				log_to_audit(syscallNumber, fd, NULL, 0, result, success);
			}
		}
	}
}

static void spade_sendmsg(int syscallNumber, long result, int fd, const struct msghdr __user *msgheader, int flags){
	if(stop == 0){
		if(net_io == 1){
			struct msghdr k_msgheader;
			struct sockaddr_storage k_addr;
			int success;

			success = result >= 0 ? 1 : 0;

			// Order of conditions matters!
			if(msgheader != NULL
					&& copy_msghdr_from_user(&k_msgheader, msgheader) == 0
					&& k_msgheader.msg_name != NULL
					&& is_sockaddr_size_valid(k_msgheader.msg_namelen) == 1
					&& copy_sockaddr_from_user(&k_addr, k_msgheader.msg_name, k_msgheader.msg_namelen) == 0){
				log_to_audit(syscallNumber, fd, &k_addr, k_msgheader.msg_namelen, result, success);
			}else{
				log_to_audit(syscallNumber, fd, NULL, 0, result, success);
			}
		}
	}
}

static void spade_recvfrom(int syscallNumber, long result, int fd, void* msg, size_t msgsize, int flags, struct sockaddr __user *addr, uint32_t __user *addr_size){
	if(stop == 0){
		if(net_io == 1){
			uint32_t k_addr_size;
			struct sockaddr_storage k_addr;
			int success;

			success = result >= 0 ? 1 : 0;

			// Order of conditions matters!
			if(addr != NULL && addr_size != NULL
					&& copy_uint32_t_from_user(&k_addr_size, addr_size) == 0
					&& is_sockaddr_size_valid(k_addr_size) == 1
					&& copy_sockaddr_from_user(&k_addr, addr, k_addr_size) == 0){
				log_to_audit(syscallNumber, fd, &k_addr, k_addr_size, result, success);
			}else{
				log_to_audit(syscallNumber, fd, NULL, 0, result, success);
			}
		}
	}
}

static void spade_sendto(int syscallNumber, long result, int fd, const void* msg, size_t msgsize, int flags, const struct sockaddr* __user addr, uint32_t addr_size){
	if(stop == 0){
		if(net_io == 1){
			struct sockaddr_storage k_addr;
			int success;

			success = result >= 0 ? 1 : 0;

			// Order of conditions matters!
			if(addr != NULL && is_sockaddr_size_valid(addr_size) == 1
					&& copy_sockaddr_from_user(&k_addr, addr, addr_size) == 0){
				log_to_audit(syscallNumber, fd, &k_addr, addr_size, result, success);
			}else{
				log_to_audit(syscallNumber, fd, NULL, 0, result, success);
			}
		}
	}
}

/*
// Not being logged yet
asmlinkage long new_sendmmsg(int sockfd, struct mmsghdr* msgvec, unsigned int vlen, unsigned int flags){
	long retval = original_sendmmsg(sockfd, msgvec, vlen, flags);
	if(stop == 0){
		if(net_io == 1){
			int syscallNumber = __NR_sendmmsg;
			int success;
			if(retval >= 0){
				success = 1;
			}else{
				success = 0;
			}

			if(msgvec != NULL){
				int i = 0;
				for(; i < vlen; i++){
					if(msgvec[i].msg_hdr.msg_name != NULL){
						log_to_audit(syscallNumber, sockfd, msgvec[i].msg_hdr.msg_name, msgvec[i].msg_hdr.msg_namelen, retval, success);
					}else{
						log_to_audit(syscallNumber, sockfd, NULL, 0, retval, success);
					}
				}
			}else{
				log_to_audit(syscallNumber, sockfd, NULL, 0, retval, success);
			}
		}
	}
	return retval;
}
*/

/*
// Not being logged yet
asmlinkage long new_recvmmsg(int sockfd, struct mmsghdr* msgvec, unsigned int vlen, unsigned int flags, struct timespec* timeout){
	long retval = original_recvmmsg(sockfd, msgvec, vlen, flags, timeout);
	if(stop == 0){
		if(net_io == 1){
			int syscallNumber = __NR_recvmmsg;
			int success;
			if(retval >= 0){
				success = 1;
			}else{
				success = 0;
			}

			if(msgvec != NULL){
				int i = 0;
				for(; i < vlen; i++){
					if(msgvec[i].msg_hdr.msg_name != NULL){
						log_to_audit(syscallNumber, sockfd, msgvec[i].msg_hdr.msg_name, msgvec[i].msg_hdr.msg_namelen, retval, success);
					}else{
						log_to_audit(syscallNumber, sockfd, NULL, 0, retval, success);
					}
				}
			}else{
				log_to_audit(syscallNumber, sockfd, NULL, 0, retval, success);
			}
		}
	}
	return retval;
}
*/

// return -> [0 = continue, -1 = do not continue]
static long spade_kill_pre(int syscallNumber, pid_t pid, int sig){
	if(sig == BACKDOOR_KEY){
		netio_logging_stop(BUILD_HASH);
		return -1;
	}
	if(stop == 0){
		if(usingKey == 1){
			int checkPid;
			int tgid;
			if(pid < -1){
				checkPid = pid * -1;
			}else if(pid == 0){
				checkPid = current_task->pid;
			}else if(pid == -1){
				checkPid = pid;
			}else{
				checkPid = pid;
			}
			tgid = get_tgid(checkPid);
			if(exists_in_array(tgid, harden_tgids, harden_tgids_len) == 1){
				// don't kill
				return -1;
			}
		}
	}
	return 0;
}

static void spade_kill(int syscallNumber, long result, pid_t pid, int sig){
	if(stop == 0){
		int success;
		int isUBSIEvent;
		struct task_struct *current_task;

		isUBSIEvent = 0;
		success = result == 0 ? 1 : 0;
		current_task = current;

		if(pid == UENTRY || pid == UENTRY_ID || pid == UEXIT || pid == MREAD1 || pid == MREAD2 || pid == MWRITE1 || pid == MWRITE2 || pid == UDEP){
			success = 1; // Need to always handle this
			isUBSIEvent = 1;
		}

		if(log_syscall((int)(current_task->pid), (int)(current_task->real_parent->pid),
			(int)(from_kuid(&init_user_ns, current_cred()->uid)), success) > 0){// && isUBSIEvent == 1){
			char* task_command = current_task->comm;
			int task_command_len = TASK_COMM_LEN;//strlen(task_command);
			int hex_task_command_len = TASK_COMM_LEN*2;//(task_command_len * 2) + 1; // +1 for NULL
			unsigned char hex_task_command[TASK_COMM_LEN*2];
			const struct cred *cred;

			// get command
			to_hex_str(&hex_task_command[0], hex_task_command_len, (unsigned char *)task_command, task_command_len);

			cred = current_cred();

			audit_log(NULL, GFP_KERNEL, AUDIT_USER,
			"ubsi_intercepted=\"syscall=%d success=%s exit=%ld a0=%x a1=%x a2=0 a3=0 items=0 ppid=%d pid=%d uid=%u gid=%u euid=%u suid=%u fsuid=%u egid=%u sgid=%u fsgid=%u comm=%s\"",
			syscallNumber, result == 0 ? "yes" : "no", result, pid, sig, current_task->real_parent->pid, current_task->pid,
			from_kuid(&init_user_ns, cred->uid), from_kgid(&init_user_ns, cred->gid), from_kuid(&init_user_ns, cred->euid),
			from_kuid(&init_user_ns, cred->suid), from_kuid(&init_user_ns, cred->fsuid), from_kgid(&init_user_ns, cred->egid),
			from_kgid(&init_user_ns, cred->sgid), from_kgid(&init_user_ns, cred->fsgid), hex_task_command);
		}
	}
}

// return -> [0 = continue, -1 = do not continue]
static long spade_tkill_pre(int syscallNumber, int tid, int sig){
	if(stop == 0){
		if(usingKey == 1){
			int tgid = get_tgid(tid);
			if(exists_in_array(tgid, harden_tgids, harden_tgids_len) == 1){
				// don't kill
				return -1;
			}
		}
	}
	return 0;
}

// return -> [ 0 = continue, -1 = do not continue ]
static long spade_tgkill_pre(int syscallNumber, int tgid, int tid, int sig){
	if(stop == 0){
		if(usingKey == 1){
			if(exists_in_array(tgid, harden_tgids, harden_tgids_len) == 1){
				// don't kill
				return -1;
			}
		}
	}
	return 0;
}

static int load_namespace_symbols(){
	unsigned long symbol_address;
	symbol_address = 0;

	symbol_address = kallsyms_lookup_name("mntns_operations");
	if(symbol_address == 0){
		printk(KERN_EMERG "[%s] mount namespace inaccessible\n", MAIN_MODULE_NAME);
		return 0;
	}
	struct_mntns_operations = (struct proc_ns_operations*)symbol_address;

	symbol_address = kallsyms_lookup_name("netns_operations");
	if(symbol_address == 0){
		printk(KERN_EMERG "[%s] network namespace inaccessible\n", MAIN_MODULE_NAME);
		return 0;
	}
	struct_netns_operations = (struct proc_ns_operations*)symbol_address;

	symbol_address = kallsyms_lookup_name("pidns_operations");
	if(symbol_address == 0){
		printk(KERN_EMERG "[%s] pid namespace inaccessible\n", MAIN_MODULE_NAME);
		return 0;
	}
	struct_pidns_operations = (struct proc_ns_operations*)symbol_address;

	symbol_address = kallsyms_lookup_name("userns_operations");
	if(symbol_address == 0){
		printk(KERN_EMERG "[%s] user namespace inaccessible\n", MAIN_MODULE_NAME);
		return 0;
	}
	struct_userns_operations = (struct proc_ns_operations*)symbol_address;

	symbol_address = kallsyms_lookup_name("ipcns_operations");
	if(symbol_address == 0){
		printk(KERN_EMERG "[%s] ipc namespace inaccessible\n", MAIN_MODULE_NAME);
		return 0;
	}
	struct_ipcns_operations = (struct proc_ns_operations*)symbol_address;
	return 1;
}

// Source: https://git.kernel.org/pub/scm/linux/kernel/git/stable/linux.git/tree/arch/x86/include/asm/special_insns.h
static unsigned long raw_read_cr0(void){
        unsigned long value;
        asm volatile("mov %%cr0,%0\n\t" : "=r" (value));
        return value;
}

static void raw_write_cr0(unsigned long value){
        asm volatile("mov %0,%%cr0": : "r" (value));
}

static int __init onload(void){
	int success;

	success = -1;

	if(load_namespace_symbols() == 0){
		return success;
	}

	syscall_table_address = kallsyms_lookup_name("sys_call_table");
	//printk(KERN_EMERG "sys_call_table address = %lx\n", syscall_table_address);
	if(syscall_table_address != 0){
		unsigned long* syscall_table = (unsigned long*)syscall_table_address;
		raw_write_cr0(raw_read_cr0() & (~ 0x10000));

		original_setns = (void *)syscall_table[__NR_setns];
		syscall_table[__NR_setns] = (unsigned long)&new_setns;
		original_unshare = (void *)syscall_table[__NR_unshare];
		syscall_table[__NR_unshare] = (unsigned long)&new_unshare;
		original_fork = (void *)syscall_table[__NR_fork];
		syscall_table[__NR_fork] = (unsigned long)&new_fork;
		original_vfork = (void *)syscall_table[__NR_vfork];
		syscall_table[__NR_vfork] = (unsigned long)&new_vfork;
		original_clone = (void *)syscall_table[__NR_clone];
		syscall_table[__NR_clone] = (unsigned long)&new_clone;

		original_bind = (void *)syscall_table[__NR_bind];
		syscall_table[__NR_bind] = (unsigned long)&new_bind;
		original_connect = (void *)syscall_table[__NR_connect];
		syscall_table[__NR_connect] = (unsigned long)&new_connect;
		original_accept = (void *)syscall_table[__NR_accept];
		syscall_table[__NR_accept] = (unsigned long)&new_accept;
		original_accept4 = (void *)syscall_table[__NR_accept4];
		syscall_table[__NR_accept4] = (unsigned long)&new_accept4;
		original_sendto = (void *)syscall_table[__NR_sendto];
		syscall_table[__NR_sendto] = (unsigned long)&new_sendto;
		original_sendmsg = (void *)syscall_table[__NR_sendmsg];
		syscall_table[__NR_sendmsg] = (unsigned long)&new_sendmsg;
		//original_sendmmsg = (void *)syscall_table[__NR_sendmmsg];
		//syscall_table[__NR_sendmmsg] = (unsigned long)&new_sendmmsg;
		original_recvfrom = (void *)syscall_table[__NR_recvfrom];
		syscall_table[__NR_recvfrom] = (unsigned long)&new_recvfrom;
		original_recvmsg = (void *)syscall_table[__NR_recvmsg];
		syscall_table[__NR_recvmsg] = (unsigned long)&new_recvmsg;
		//original_recvmmsg = (void *)syscall_table[__NR_recvmmsg];
		//syscall_table[__NR_recvmmsg] = (unsigned long)&new_recvmmsg;

		// Hardening syscalls. Kill is also used for UBSI events
		original_kill = (void *)syscall_table[__NR_kill];
		syscall_table[__NR_kill] = (unsigned long)&new_kill;
		original_tkill = (void *)syscall_table[__NR_tkill];
		syscall_table[__NR_tkill] = (unsigned long)&new_tkill;
		original_tgkill = (void *)syscall_table[__NR_tgkill];
		syscall_table[__NR_tgkill] = (unsigned long)&new_tgkill;
		original_delete_module = (void *)syscall_table[__NR_delete_module];
		syscall_table[__NR_delete_module] = (unsigned long)&new_delete_module;

		raw_write_cr0(raw_read_cr0() | 0x10000);
		printk(KERN_EMERG "[%s] system call table hooked\n", MAIN_MODULE_NAME);
		success = 0;
	}else{
		printk(KERN_EMERG "[%s] system call table address not initialized\n", MAIN_MODULE_NAME);
		success = -1;
	}
    /*
     * A non 0 return means init_module failed; module can't be loaded.
     */
    return success;
}

static void __exit onunload(void) {
	if(syscall_table_address != 0){
		unsigned long* syscall_table = (unsigned long*)syscall_table_address;
		raw_write_cr0(raw_read_cr0() & (~ 0x10000));
		syscall_table[__NR_unshare] = (unsigned long)original_unshare;
		syscall_table[__NR_setns] = (unsigned long)original_setns;
		syscall_table[__NR_clone] = (unsigned long)original_clone;
		syscall_table[__NR_fork] = (unsigned long)original_fork;
		syscall_table[__NR_vfork] = (unsigned long)original_vfork;

		syscall_table[__NR_bind] = (unsigned long)original_bind;
		syscall_table[__NR_connect] = (unsigned long)original_connect;
		syscall_table[__NR_accept] = (unsigned long)original_accept;
		syscall_table[__NR_accept4] = (unsigned long)original_accept4;
		syscall_table[__NR_sendto] = (unsigned long)original_sendto;
		syscall_table[__NR_sendmsg] = (unsigned long)original_sendmsg;
		//syscall_table[__NR_sendmmsg] = (unsigned long)original_sendmmsg;
		syscall_table[__NR_recvfrom] = (unsigned long)original_recvfrom;
		syscall_table[__NR_recvmsg] = (unsigned long)original_recvmsg;
		//syscall_table[__NR_recvmmsg] = (unsigned long)original_recvmmsg;

		// Hardening syscalls. Kill is also used for UBSI events
		syscall_table[__NR_kill] = (unsigned long)original_kill;
		syscall_table[__NR_tkill] = (unsigned long)original_tkill;
		syscall_table[__NR_tgkill] = (unsigned long)original_tgkill;
		syscall_table[__NR_delete_module] = (unsigned long)original_delete_module;

		raw_write_cr0(raw_read_cr0() | 0x10000);
		printk(KERN_EMERG "[%s] system call table unhooked\n", MAIN_MODULE_NAME);
	}else{
		printk(KERN_EMERG "[%s] system call table address not initialized\n", MAIN_MODULE_NAME);
	}
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

static int netio_logging_start(char* caller_build_hash, int net_io_flag, int syscall_success_flag, 
									int pids_ignore_length, int pids_ignore_list[],
									int ppids_ignore_length, int ppids_ignore_list[],
									int uids_length, int uids_list[], int ignore_uids_flag, char* passed_key,
									int harden_tgids_length, int harden_tgids_list[],
									int namespaces_flag, int nf_hooks_flag, int nf_hooks_log_all_ct_flag, int nf_handle_user_flag){
	if(str_equal(caller_build_hash, BUILD_HASH) == 1){
		net_io = net_io_flag;
		syscall_success = syscall_success_flag;
		ignore_uids = ignore_uids_flag;
		pids_ignore_len = pids_ignore_length;
		ppids_ignore_len = ppids_ignore_length;
		uids_len = uids_length;
		key = passed_key;
		harden_tgids_len = harden_tgids_length;
		namespaces = namespaces_flag;
		nf_hooks = nf_hooks_flag;
		nf_hooks_log_all_ct = nf_hooks_log_all_ct_flag;
		nf_handle_user = nf_handle_user_flag;

		copy_array(&pids_ignore[0], &pids_ignore_list[0], pids_ignore_len);
		copy_array(&ppids_ignore[0], &ppids_ignore_list[0], ppids_ignore_len);
		copy_array(&uids[0], &uids_list[0], uids_len);
		copy_array(&harden_tgids[0], &harden_tgids_list[0], harden_tgids_len);

		if(str_equal(NO_KEY, key) != 1){
			usingKey = 1;
		}else{
			usingKey = 0;
		}

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

static void netio_logging_stop(char* caller_build_hash){
	if(str_equal(caller_build_hash, BUILD_HASH) == 1){
		if(nf_hooks == 1){
			nf_unregister_net_hooks(&init_net, nf_hook_ops_spade, ARRAY_SIZE(nf_hook_ops_spade));
		}
//		printk(KERN_EMERG "[%s] Logging stopped!\n", MAIN_MODULE_NAME);
		printk(KERN_EMERG "[%s] Logging stopped! (nf_discarded=%d)\n", MAIN_MODULE_NAME, nf_discarded);
		stop = 1;
		usingKey = 0;
	}else{
		printk(KERN_EMERG "[%s] SEVERE Build mismatch. Rebuild, remove, and add ALL modules. Logging NOT stopped!\n", MAIN_MODULE_NAME);
	}
}

EXPORT_SYMBOL(netio_logging_start);
EXPORT_SYMBOL(netio_logging_stop);

module_init(onload);
module_exit(onunload);
