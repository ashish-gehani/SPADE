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

#include <asm/paravirt.h> // write_cr0
#include <linux/audit.h>
#include <linux/file.h>
#include <linux/kallsyms.h>
#include <linux/mnt_namespace.h>
#include <linux/net.h>
#include <linux/ns_common.h>
#include <linux/nsproxy.h>
#include <linux/proc_ns.h>
#include <linux/uaccess.h>  // copy_from_user
#include <linux/unistd.h>  // __NR_<system-call-name>
#include <linux/version.h>

#include "globals.h"

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

static struct proc_ns_operations *struct_mntns_operations;

static unsigned long syscall_table_address = 0;

// The return type for all system calls used is 'long' below even though some return 'int' according to API.
// Using 'long' because linux 64-bit kernel code uses a 'long' return value for all system calls. 
// Error example: If 'int' used below for 'connect' system according to API then when negative value returned it gets
// casted to a shorter datatype (i.e. 'int') and incorrect return value is gotten. Hence the application using the 
// 'connect' syscall fails.
asmlinkage long (*original_kill)(pid_t pid, int sig);
asmlinkage long (*original_bind)(int, const struct sockaddr*, uint32_t);
asmlinkage long (*original_connect)(int, const struct sockaddr*, uint32_t);
asmlinkage long (*original_accept)(int, struct sockaddr*, uint32_t*);
asmlinkage long (*original_accept4)(int, struct sockaddr*, uint32_t*, int);
asmlinkage long (*original_sendto)(int, const void*, size_t, int, const struct sockaddr*, uint32_t);
asmlinkage long (*original_sendmsg)(int, const struct msghdr*, int);
asmlinkage long (*original_sendmmsg)(int, struct mmsghdr*, unsigned int, unsigned int);
asmlinkage long (*original_recvfrom)(int, void*, size_t, int, struct sockaddr*, uint32_t*);
asmlinkage long (*original_recvmsg)(int, struct msghdr*, int);
asmlinkage long (*original_recvmmsg)(int, struct mmsghdr*, unsigned int, unsigned int, struct timespec*);
asmlinkage long (*original_delete_module)(const char *name, int flags);
asmlinkage long (*original_tkill)(int tid, int sig);
asmlinkage long (*original_tgkill)(int tgid, int tid, int sig);
asmlinkage long (*original_clone)(unsigned long flags, void *child_stack, int *ptid, int *ctid, unsigned long newtls);

static int is_sockaddr_size_valid(uint32_t size);
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
									int harden_tgids_length, int harden_tgids_list[]); // 1 success, 0 failure
static void netio_logging_stop(char* caller_build_hash);
static int get_tgid(int);
static int special_str_equal(const char* hay, const char* constantModuleName);
static int get_hex_saddr_from_fd_getname(char* result, int max_result_size, int *fd_sock_type, int sock_fd, int peer);

static long get_ns_inum(struct task_struct *struct_task_struct, const struct proc_ns_operations *proc_ns_operations);
static void log_namespace_audit_msg(const long pid, const long inum_mnt, const long inum_net, const long inum_pid, const long inum_usr);
static void log_namespaces_task(struct task_struct *struct_task_struct);
static void log_namespaces_pid(const long pid);

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
		int sock_fd, int peer){
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
			(int)(current_task->real_cred->uid.val), success) > 0){

		int log_me = 0;
		int fd_sock_type = -1;

		int max_hex_sockaddr_size = (_K_SS_MAXSIZE * 2) + 1; // +1 for NULL char
		unsigned char hex_fd_addr[max_hex_sockaddr_size];
		unsigned char hex_addr[max_hex_sockaddr_size];

		char* task_command = current_task->comm;
		int task_command_len = strlen(task_command);
		int hex_task_command_len = (task_command_len * 2) + 1; // +1 for NULL
		unsigned char hex_task_command[hex_task_command_len];

		hex_fd_addr[0] = '\0';
		hex_addr[0] = '\0';
		hex_task_command[0] = '\0';

		// get command
		to_hex(&hex_task_command[0], hex_task_command_len, (unsigned char *)task_command, task_command_len);

		// get addr passed in
		if(addr != NULL){
			sockaddr_to_hex(&hex_addr[0], max_hex_sockaddr_size, (unsigned char *)addr, addr_size);
		}else{
			if(syscallNumber == __NR_accept || syscallNumber == __NR_accept4){
				if(success == 1){
					log_me = get_hex_saddr_from_fd_getname(&hex_addr[0], max_hex_sockaddr_size, &fd_sock_type, (int)exit, 1);
				}
			}
		}

		log_me = get_hex_saddr_from_fd_getname(&hex_fd_addr[0], max_hex_sockaddr_size, &fd_sock_type, fd, 0);
		
		if(log_me == 1){
		// TODO other info needs to be logged?
			audit_log(NULL, GFP_KERNEL, AUDIT_USER,
				"netio_intercepted=\"syscall=%d exit=%ld success=%d fd=%d pid=%d ppid=%d uid=%u euid=%u suid=%u fsuid=%u \
	gid=%u egid=%u sgid=%u fsgid=%u comm=%s sock_type=%d local_saddr=%s remote_saddr=%s remote_saddr_size=%d\"",
				syscallNumber, exit, success, fd, current_task->pid, current_task->real_parent->pid,
				current_task->real_cred->uid.val, current_task->real_cred->euid.val, current_task->real_cred->suid.val,
				current_task->real_cred->fsuid.val, current_task->real_cred->gid.val, current_task->real_cred->egid.val,
				current_task->real_cred->sgid.val, current_task->real_cred->fsgid.val,
				hex_task_command, fd_sock_type, hex_fd_addr, hex_addr, addr_size);
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

static void log_namespaces_pid(const long pid){
	struct pid *struct_pid;
	struct task_struct *struct_task_struct;

	struct_pid = NULL;
	struct_task_struct = NULL;

	rcu_read_lock();
	struct_pid = find_vpid(pid); // TODO
	if(struct_pid != NULL){
		struct_task_struct = pid_task(struct_pid, PIDTYPE_PID);
		if(struct_task_struct != NULL){
			// ns specific
			log_namespaces_task(struct_task_struct);
		}
	}
	rcu_read_unlock();
}

static void log_namespaces_task(struct task_struct *struct_task_struct){
	long inum_mnt;

	inum_mnt = -1;

	rcu_read_lock();
	// ns specific
	inum_mnt = get_ns_inum(struct_task_struct, struct_mntns_operations);
	rcu_read_unlock();

	log_namespace_audit_msg(struct_task_struct->pid, inum_mnt, -1, -1, -1);
}

static void log_namespace_audit_msg(const long pid,
		const long inum_mnt, const long inum_net, const long inum_pid, const long inum_usr){
	audit_log(current->audit_context,
					GFP_KERNEL, AUDIT_USER,
					"subtype=namespaces pid=%ld inum_mnt=%ld inum_net=%ld inum_pid=%ld inum_usr=%ld",
					pid,
					inum_mnt, inum_net, inum_pid, inum_usr);
}

asmlinkage long new_clone(unsigned long flags, void *child_stack, int *ptid, int *ctid, unsigned long newtls){
	long childPid = original_clone(flags, child_stack, ptid, ctid, newtls);
	if(stop == 0){
		int success;
		success = childPid == -1 ? 0 : 1;
		if(log_syscall((int)(current->pid), (int)(current->real_parent->pid),
								(int)(current->real_cred->uid.val), success) > 0){
			log_namespaces_pid(childPid);
		}
	}
	return childPid;
}

asmlinkage long new_bind(int fd, const struct sockaddr __user *addr, uint32_t addr_size){
	long retval = original_bind(fd, addr, addr_size);
	if(stop == 0){
		struct sockaddr_storage k_addr;
		int syscallNumber = __NR_bind;
		int success = retval == 0 ? 1 : 0;

		// Order of conditions matters!
		if(addr != NULL && is_sockaddr_size_valid(addr_size) == 1
				&& copy_sockaddr_from_user(&k_addr, addr, addr_size) == 0){
			log_to_audit(syscallNumber, fd, &k_addr, addr_size, retval, success);
		}else{
			log_to_audit(syscallNumber, fd, NULL, 0, retval, success);
		}
	}
	return retval;
}

asmlinkage long new_connect(int fd, const struct sockaddr __user *addr, uint32_t addr_size){
	long retval = original_connect(fd, addr, addr_size);
	if(stop == 0){
		struct sockaddr_storage k_addr;
		int syscallNumber = __NR_connect;
		int success = (retval >= 0 || retval == -EINPROGRESS) ? 1 : 0;

		// Order of conditions matters!
		if(addr != NULL && is_sockaddr_size_valid(addr_size) == 1
				&& copy_sockaddr_from_user(&k_addr, addr, addr_size) == 0){
			log_to_audit(syscallNumber, fd, &k_addr, addr_size, retval, success);
		}else{
			log_to_audit(syscallNumber, fd, NULL, 0, retval, success);
		}
	}
	return retval;
}

asmlinkage long new_accept(int fd, struct sockaddr __user *addr, uint32_t __user *addr_size){
	long retval = original_accept(fd, addr, addr_size);
	if(stop == 0){
		uint32_t k_addr_size;
		struct sockaddr_storage k_addr;
		int syscallNumber = __NR_accept;
		int success = retval >= 0 ? 1 : 0;

		// Order of conditions matters!
		if(addr != NULL && addr_size != NULL
				&& copy_uint32_t_from_user(&k_addr_size, addr_size) == 0
				&& is_sockaddr_size_valid(k_addr_size) == 1
				&& copy_sockaddr_from_user(&k_addr, addr, k_addr_size) == 0){
			log_to_audit(syscallNumber, fd, &k_addr, k_addr_size, retval, success);
		}else{
			log_to_audit(syscallNumber, fd, NULL, 0, retval, success);
		}
	}
	return retval;
}

asmlinkage long new_accept4(int fd, struct sockaddr __user *addr, uint32_t __user *addr_size, int flags){
	long retval = original_accept4(fd, addr, addr_size, flags);
	if(stop == 0){
		uint32_t k_addr_size;
		struct sockaddr_storage k_addr;
		int syscallNumber = __NR_accept4;
		int success = retval >= 0 ? 1 : 0;

		// Order of conditions matters!
		if(addr != NULL && addr_size != NULL
				&& copy_uint32_t_from_user(&k_addr_size, addr_size) == 0
				&& is_sockaddr_size_valid(k_addr_size) == 1
				&& copy_sockaddr_from_user(&k_addr, addr, k_addr_size) == 0){
			log_to_audit(syscallNumber, fd, &k_addr, k_addr_size, retval, success);
		}else{
			log_to_audit(syscallNumber, fd, NULL, 0, retval, success);
		}
	}
	return retval;
}

asmlinkage long new_recvmsg (int fd, struct msghdr __user *msgheader, int flags){
	long retval = original_recvmsg(fd, msgheader, flags);
	if(stop == 0){
		if(net_io == 1){
			struct msghdr k_msgheader;
			struct sockaddr_storage k_addr;
			int syscallNumber = __NR_recvmsg;
			int success = retval >= 0 ? 1 : 0;

			// Order of conditions matters!
			if(msgheader != NULL
					&& copy_msghdr_from_user(&k_msgheader, msgheader) == 0
					&& k_msgheader.msg_name != NULL
					&& is_sockaddr_size_valid(k_msgheader.msg_namelen) == 1
					&& copy_sockaddr_from_user(&k_addr, k_msgheader.msg_name, k_msgheader.msg_namelen) == 0){
				log_to_audit(syscallNumber, fd, &k_addr, k_msgheader.msg_namelen, retval, success);
			}else{
				log_to_audit(syscallNumber, fd, NULL, 0, retval, success);
			}
		}
	}
	return retval;
}

asmlinkage long new_sendmsg (int fd, const struct msghdr __user *msgheader, int flags){
	long retval = original_sendmsg(fd, msgheader, flags);
	if(stop == 0){
		if(net_io == 1){
			struct msghdr k_msgheader;
			struct sockaddr_storage k_addr;
			int syscallNumber = __NR_sendmsg;
			int success = retval >= 0 ? 1 : 0;

			// Order of conditions matters!
			if(msgheader != NULL
					&& copy_msghdr_from_user(&k_msgheader, msgheader) == 0
					&& k_msgheader.msg_name != NULL
					&& is_sockaddr_size_valid(k_msgheader.msg_namelen) == 1
					&& copy_sockaddr_from_user(&k_addr, k_msgheader.msg_name, k_msgheader.msg_namelen) == 0){
				log_to_audit(syscallNumber, fd, &k_addr, k_msgheader.msg_namelen, retval, success);
			}else{
				log_to_audit(syscallNumber, fd, NULL, 0, retval, success);
			}
		}
	}
	return retval;
}

asmlinkage long new_recvfrom (int fd, void* msg, size_t msgsize, int flags, struct sockaddr __user *addr,
		uint32_t __user *addr_size) {
	long retval = original_recvfrom(fd, msg, msgsize, flags, addr, addr_size);
	if(stop == 0){
		if(net_io == 1){
			uint32_t k_addr_size;
			struct sockaddr_storage k_addr;
			int syscallNumber = __NR_recvfrom;
			int success = retval >= 0 ? 1 : 0;

			// Order of conditions matters!
			if(addr != NULL && addr_size != NULL
					&& copy_uint32_t_from_user(&k_addr_size, addr_size) == 0
					&& is_sockaddr_size_valid(k_addr_size) == 1
					&& copy_sockaddr_from_user(&k_addr, addr, k_addr_size) == 0){
				log_to_audit(syscallNumber, fd, &k_addr, k_addr_size, retval, success);
			}else{
				log_to_audit(syscallNumber, fd, NULL, 0, retval, success);
			}
		}
	}
	return retval;
}

asmlinkage long new_sendto(int fd, const void* msg, size_t msgsize, int flags, const struct sockaddr* __user addr,
		uint32_t addr_size) {
	long retval = original_sendto(fd, msg, msgsize, flags, addr, addr_size);
	if(stop == 0){
		if(net_io == 1){
			struct sockaddr_storage k_addr;
			int syscallNumber = __NR_sendto;
			int success = retval >= 0 ? 1 : 0;

			// Order of conditions matters!
			if(addr != NULL && is_sockaddr_size_valid(addr_size) == 1
					&& copy_sockaddr_from_user(&k_addr, addr, addr_size) == 0){
				log_to_audit(syscallNumber, fd, &k_addr, addr_size, retval, success);
			}else{
				log_to_audit(syscallNumber, fd, NULL, 0, retval, success);
			}
		}
	}
    return retval;
}

asmlinkage long new_kill(pid_t pid, int sig){
	long retval;
	
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
	
	retval = original_kill(pid, sig);
	if(stop == 0){
		int isUBSIEvent = 0;
		int syscallNumber = __NR_kill;
		int success = retval == 0 ? 1 : 0;
		struct task_struct* current_task = current;
		
		if(pid == UENTRY || pid == UENTRY_ID || pid == UEXIT || pid == MREAD1 || pid == MREAD2 || pid == MWRITE1 || pid == MWRITE2 || pid == UDEP){
			success = 1; // Need to always handle this
			isUBSIEvent = 1;
		}
		
		if(log_syscall((int)(current_task->pid), (int)(current_task->real_parent->pid),
			(int)(current_task->real_cred->uid.val), success) > 0){// && isUBSIEvent == 1){
			char* task_command = current_task->comm;
			int task_command_len = strlen(task_command);
			int hex_task_command_len = (task_command_len * 2) + 1; // +1 for NULL
			unsigned char hex_task_command[hex_task_command_len];
			
			// get command
			to_hex(&hex_task_command[0], hex_task_command_len, (unsigned char *)task_command, task_command_len);
			
			audit_log(NULL, GFP_KERNEL, AUDIT_USER, 
			"ubsi_intercepted=\"syscall=%d success=%s exit=%ld a0=%x a1=%x a2=0 a3=0 items=0 ppid=%d pid=%d uid=%d gid=%d euid=%d suid=%d fsuid=%d egid=%d sgid=%d fsgid=%d comm=%s\"",
			syscallNumber, retval == 0 ? "yes" : "no", retval, pid, sig, current_task->real_parent->pid, current_task->pid,
			current_task->real_cred->uid.val, current_task->real_cred->gid.val, current_task->real_cred->euid.val,
			current_task->real_cred->suid.val, current_task->real_cred->fsuid.val, current_task->real_cred->egid.val,
			current_task->real_cred->sgid.val, current_task->real_cred->fsgid.val, hex_task_command);
		}
	}
	return retval;
}

// Not being logged yet
asmlinkage long new_sendmmsg(int sockfd, struct mmsghdr* msgvec, unsigned int vlen, unsigned int flags){
	long retval = original_sendmmsg(sockfd, msgvec, vlen, flags);
	/*
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
	*/
	return retval;
}

// Not being logged yet
asmlinkage long new_recvmmsg(int sockfd, struct mmsghdr* msgvec, unsigned int vlen, unsigned int flags, struct timespec* timeout){
	long retval = original_recvmmsg(sockfd, msgvec, vlen, flags, timeout);
	/*
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
	*/
	return retval;
}

asmlinkage long new_tkill(int tid, int sig){
	if(stop == 0){
		if(usingKey == 1){
			int tgid = get_tgid(tid);
			if(exists_in_array(tgid, harden_tgids, harden_tgids_len) == 1){
				// don't kill
				return -1;
			}
		}
	}
	return original_tkill(tid, sig);
}

asmlinkage long new_tgkill(int tgid, int tid, int sig){
	if(stop == 0){
		if(usingKey == 1){
			if(exists_in_array(tgid, harden_tgids, harden_tgids_len) == 1){
				// don't kill
				return -1;
			}
		}
	}
	return original_tgkill(tgid, tid, sig);
}

asmlinkage long new_delete_module(const char* name, int flags){
	/*
	 * Check if key is set. if not set then execute normal functionality
	 * If set then check if the name matches the module name that are special
	 * If special modules then don't remove
	 * If not special modules then check if the name equals key. that means remove the special modules
	 * If not special and not equals key then execute normal functionality
	 */
	if(stop == 0){
		//printk(KERN_EMERG "delete_module stop=0\n");
		if(name == NULL){
			//printk(KERN_EMERG "delete_module name not null\n");
			return -1;
		}
		if(usingKey == 0){
			// original code
			//printk(KERN_EMERG "delete_module not using key\n");
			return original_delete_module(name, flags);
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
							retval = original_delete_module(name, flags);
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
						return original_delete_module(name, flags);
					}
				}
			}else{
				printk(KERN_EMERG "[netio] Failed to copy module name from userspace\n");
				return -1;
			}
		}
	}else{
		return original_delete_module(name, flags);
	}
}

static int __init onload(void) {
	int success;
	unsigned long mntns_operations_address;

	success = -1;
	mntns_operations_address = 0;
	struct_mntns_operations = NULL;

	mntns_operations_address = kallsyms_lookup_name("mntns_operations");
	if(mntns_operations_address != 0){
		struct_mntns_operations = (struct proc_ns_operations*)mntns_operations_address;
//		printk(KERN_EMERG "mnt_operations address = %lx\n", mntns_operations_address);
	}

	if(struct_mntns_operations == NULL){
		printk(KERN_EMERG "[%s] mount namespace inaccessible\n", MAIN_MODULE_NAME);
		return success;
	}

	syscall_table_address = kallsyms_lookup_name("sys_call_table");
	//printk(KERN_EMERG "sys_call_table address = %lx\n", syscall_table_address);
	if(syscall_table_address != 0){
		unsigned long* syscall_table = (unsigned long*)syscall_table_address;
		write_cr0 (read_cr0 () & (~ 0x10000));
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
		
		write_cr0 (read_cr0 () | 0x10000);
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
    if (syscall_table_address != 0) {
		unsigned long* syscall_table = (unsigned long*)syscall_table_address;
        write_cr0 (read_cr0 () & (~ 0x10000));
        syscall_table[__NR_clone] = (unsigned long)original_clone;
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
		
        write_cr0 (read_cr0 () | 0x10000);
        printk(KERN_EMERG "[%s] system call table unhooked\n", MAIN_MODULE_NAME);
    } else {
        printk(KERN_EMERG "[%s] system call table address not initialized\n", MAIN_MODULE_NAME);
    }
}

static int netio_logging_start(char* caller_build_hash, int net_io_flag, int syscall_success_flag, 
									int pids_ignore_length, int pids_ignore_list[],
									int ppids_ignore_length, int ppids_ignore_list[],
									int uids_length, int uids_list[], int ignore_uids_flag, char* passed_key,
									int harden_tgids_length, int harden_tgids_list[]){
	if(str_equal(caller_build_hash, BUILD_HASH) == 1){
		net_io = net_io_flag;
		syscall_success = syscall_success_flag;
		ignore_uids = ignore_uids_flag;
		pids_ignore_len = pids_ignore_length;
		ppids_ignore_len = ppids_ignore_length;
		uids_len = uids_length;
		key = passed_key;
		harden_tgids_len = harden_tgids_length;
		
		copy_array(&pids_ignore[0], &pids_ignore_list[0], pids_ignore_len);
		copy_array(&ppids_ignore[0], &ppids_ignore_list[0], ppids_ignore_len);
		copy_array(&uids[0], &uids_list[0], uids_len);
		copy_array(&harden_tgids[0], &harden_tgids_list[0], harden_tgids_len);
		
		if(str_equal(NO_KEY, key) != 1){
			usingKey = 1;	
		}
		
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
		printk(KERN_EMERG "[%s] Logging stopped!\n", MAIN_MODULE_NAME);
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
