#include <linux/module.h>  /* Needed by all kernel modules */
#include <linux/moduleparam.h>
#include <linux/kernel.h>  /* Needed for loglevels (KERN_WARNING, KERN_EMERG, KERN_INFO, etc.) */
#include <linux/init.h>    /* Needed for __init and __exit macros. */
#include <linux/unistd.h>  /* sys_call_table __NR_* system call function indices */
#include <linux/fs.h>      /* filp_open */
#include <linux/slab.h>    /* kmalloc */
#include <linux/socket.h>
#include <linux/fdtable.h>
#include <linux/file.h>
#include <linux/sched.h>
#include <linux/net.h>
#include <linux/un.h>
#include <linux/audit.h>
#include <linux/string.h>
#include <linux/utsname.h>
#include <linux/delay.h>
#include <linux/time.h>
#include <linux/types.h>
#include <asm/paravirt.h> /* write_cr0 */
#include <asm/uaccess.h>  /* get_fs, set_fs */

#define PROC_V    "/proc/version"
#define BOOT_PATH "/boot/System.map-"
#define MAX_VERSION_LEN   256

MODULE_LICENSE("GPL");

#define MAX_FIELDS 16

static int syscall_success = -1;
static int net_io = 0;
static int ignore_uids = 1;
static int pids_ignore[MAX_FIELDS];
static int ppids_ignore[MAX_FIELDS];
static int uids[MAX_FIELDS];

static int pids_ignore_len = 0;
static int ppids_ignore_len = 0;
static int uids_len = 0;

/* 
 * 'stop' variable used to start and stop ONLY logging of system calls to audit log.
 * Don't need to synchronize 'stop' variable modification because it can only be set by a kernel module and only one
 * kernel module is calling this function at the moment. Also, only one instance of a kernel module can be added at a time
 * hence ensuring no concurrent updates.
 */
static volatile int stop = 1;

unsigned long syscall_table_address = 0;

// The return type for all system calls used is 'long' below even though some return 'int' according to API.
// Using 'long' because linux 64-bit kernel code uses a 'long' return value for all system calls. 
// Error example: If 'int' used below for 'connect' system according to API then when negative value returned it gets
// casted to a shorter datatype (i.e. 'int') and incorrect return value is gotten. Hence the application using the 
// 'connect' syscall fails.
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

static int is_sockaddr_size_valid(uint32_t size);
static void to_hex(unsigned char *dst, uint32_t dst_len, unsigned char *src, uint32_t src_len);
static void sockaddr_to_hex(unsigned char* dst, int dst_len, unsigned char* addr, uint32_t addr_size);
static int copy_msghdr_from_user(struct msghdr *dst, const struct msghdr __user *src);
static int copy_sockaddr_from_user(struct sockaddr_storage *dst, const struct sockaddr __user *src, uint32_t src_size);
static int copy_uint32_t_from_user(uint32_t *dst, const uint32_t __user *src);
static void print_array(const char*, int[], int);
static int exists_in_array(int, int[], int);
static int log_syscall(int, int, int, int);
static int find_sys_call_table(char*);
static void copy_array(int* dst, int* src, int len);
static void netio_logging_start(int net_io_flag, int syscall_success_flag, 
									int pids_ignore_length, int pids_ignore_list[],
									int ppids_ignore_length, int ppids_ignore_list[],
									int uids_len, int uids[], int ignore_uids);
static void netio_logging_stop(void);

static void copy_array(int* dst, int* src, int len){
	int a = 0;
	for(; a < len; a++){
		dst[a] = src[a];	
	}	
}

static void print_array(const char* desc, int arr[], int arrlen){
	int MAX_STR_LEN = 1024;
	char strPrint[MAX_STR_LEN];
	if(arrlen == 0){
		sprintf(&(strPrint[0]), "%s (%d) = []", desc, arrlen);
	}else{
		int i = 0;
		int cursor = 0;
		cursor = sprintf(&(strPrint[cursor]), "%s (%d) = [", desc, arrlen);
		for(; i < arrlen; i++){
			cursor += sprintf(&(strPrint[cursor]), "%d, ", arr[i]);
		}
		strPrint[cursor - 2] = ']'; // replace ','
		strPrint[cursor - 1] = '\0'; // replace ' '
	}
	printk(KERN_EMERG "%s\n", strPrint);
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

static int find_sys_call_table (char *kern_ver) {
    char system_map_entry[MAX_VERSION_LEN];
    int i = 0;

    /*
     * Holds the /boot/System.map-<version> file name as we build it
     */
    char *filename;

    /*
     * Length of the System.map filename, terminating NULL included
     */
    size_t filename_length = strlen(kern_ver) + strlen(BOOT_PATH) + 1;

    /*
     * This will point to our /boot/System.map-<version> file
     */
    struct file *f = NULL;
 
    mm_segment_t oldfs;
 
    oldfs = get_fs();
    set_fs (KERNEL_DS);

    printk(KERN_EMERG "Kernel version: %s\n", kern_ver);
     
    filename = kmalloc(filename_length, GFP_KERNEL);
    if (filename == NULL) {
        printk(KERN_EMERG "kmalloc failed on System.map-<version> filename allocation");
        return -1;
    }
     
    /*
     * Zero out memory to be safe
     */
    memset(filename, 0, filename_length);
     
    /*
     * Construct our /boot/System.map-<version> file name
     */
    strncpy(filename, BOOT_PATH, strlen(BOOT_PATH));
    strncat(filename, kern_ver, strlen(kern_ver));
     
    /*
     * Open the System.map file for reading
     */
    f = filp_open(filename, O_RDONLY, 0);
    if (IS_ERR(f) || (f == NULL)) {
        printk(KERN_EMERG "Error opening System.map-<version> file: %s\n", filename);
        return -1;
    }
 
    memset(system_map_entry, 0, MAX_VERSION_LEN);
 
    /*
     * Read one byte at a time from the file until we either max out
     * out our buffer or read an entire line.
     */
    while (vfs_read(f, system_map_entry + i, 1, &f->f_pos) == 1) {
        /*
         * If we've read an entire line or maxed out our buffer,
         * check to see if we've just read the sys_call_table entry.
         */
        if ( system_map_entry[i] == '\n' || i == MAX_VERSION_LEN ) {
            // Reset the "column"/"character" counter for the row
            i = 0;
             
            if (strstr(system_map_entry, "sys_call_table") != NULL) {
                char *sys_string;
                char *system_map_entry_ptr = system_map_entry;
                 
                sys_string = kmalloc(MAX_VERSION_LEN, GFP_KERNEL);  
                if (sys_string == NULL) { 
                    filp_close(f, 0);
                    set_fs(oldfs);

                    kfree(filename);
     
                    return -1;
                }
 
                memset(sys_string, 0, MAX_VERSION_LEN);

                strncpy(sys_string, strsep(&system_map_entry_ptr, " "), MAX_VERSION_LEN);
             
                kstrtoul(sys_string, 16, &syscall_table_address);
                 
                kfree(sys_string);
                 
                break;
            }
             
            memset(system_map_entry, 0, MAX_VERSION_LEN);
            continue;
        }
         
        i++;
    }
 
    filp_close(f, 0);
    set_fs(oldfs);
     
    kfree(filename);
 
    return 0;
}

static int copy_uint32_t_from_user(uint32_t *dst, const uint32_t __user *src){
	return copy_from_user(dst, src, sizeof(uint32_t));
}

// 0 = bad, 1 = good
static int is_sockaddr_size_valid(uint32_t size){
	if(size > 0 && size <= _K_SS_MAXSIZE){
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
	memset(dst, 0, dst_len);
	for (i = 0; i < src_len; i++){
		*dst++ = hex_asc_upper[((src[i]) & 0xf0) >> 4];
		*dst++ = hex_asc_upper[((src[i]) & 0x0f)];
	}
	*dst = 0; // NULL char
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

static void log_to_audit(int syscallNumber, int fd, struct sockaddr_storage* addr, uint32_t addr_size,
		long exit, int success){
	struct task_struct* current_task = current;
	if(log_syscall((int)(current_task->pid), (int)(current_task->real_parent->pid),
			(int)(current_task->real_cred->uid.val), success) > 0){

		struct socket *fd_sock;
		int fd_sock_type = -1;
		int fd_sock_family = -1;
		struct sockaddr_storage fd_addr;
		int fd_addr_size = 0;
		int err = 0;

		int max_hex_sockaddr_size = (_K_SS_MAXSIZE * 2) + 1; // +1 for NULL char
		unsigned char hex_fd_addr[max_hex_sockaddr_size];
		unsigned char hex_addr[max_hex_sockaddr_size];

		char* task_command = current_task->comm;
		int task_command_len = strlen(task_command);
		int hex_task_command_len = (task_command_len * 2) + 1; // +1 for NULL
		unsigned char hex_task_command[hex_task_command_len];
		// get command
		to_hex(&hex_task_command[0], hex_task_command_len, (unsigned char *)task_command, task_command_len);
		// get addr passed in
		sockaddr_to_hex(&hex_addr[0], max_hex_sockaddr_size, (unsigned char *)addr, addr_size);

		// This call places a lock on the fd which prevents it from being released.
		// Releasing the lock using sockfd_put call.
		fd_sock = sockfd_lookup(fd, &err);
		
		if(fd_sock != NULL){
			fd_sock_family = fd_sock->ops->family;
			fd_sock_type = fd_sock->type;
			if((fd_sock_family != AF_UNIX && fd_sock_family != AF_LOCAL && fd_sock_family != PF_UNIX
					&& fd_sock_family != PF_LOCAL && fd_sock_family != AF_INET && fd_sock_family != AF_INET6
					&& fd_sock_family != PF_INET && fd_sock_family != PF_INET6) && // only unix, and inet
					(fd_sock_type != SOCK_STREAM && fd_sock_type != SOCK_DGRAM)){ // only stream, and dgram
				return;
			}else{
				err = fd_sock->ops->getname(fd_sock, (struct sockaddr *)&fd_addr, &fd_addr_size, 0);
				if(err == 0){
					sockaddr_to_hex(&hex_fd_addr[0], max_hex_sockaddr_size, (unsigned char*)&fd_addr, fd_addr_size);
				}
			}
			// Must free the reference to the fd after use otherwise lock won't be released.
			sockfd_put(fd_sock);
		}

		// TODO other info needs to be logged?
		audit_log(NULL, GFP_KERNEL, AUDIT_USER, 
			"netio_intercepted=\"syscall=%d exit=%ld success=%d fd=%d pid=%d ppid=%d uid=%u euid=%u suid=%u fsuid=%u \
gid=%u egid=%u sgid=%u fsgid=%u comm=%s sock_type=%d local_saddr=%s remote_saddr=%s\"",
			syscallNumber, exit, success, fd, current_task->pid, current_task->real_parent->pid,
			current_task->real_cred->uid.val, current_task->real_cred->euid.val, current_task->real_cred->suid.val,
			current_task->real_cred->fsuid.val, current_task->real_cred->gid.val, current_task->real_cred->egid.val,
			current_task->real_cred->sgid.val, current_task->real_cred->fsgid.val,
			hex_task_command, fd_sock_type, hex_fd_addr, hex_addr);
	}
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

static int __init onload(void) {
	int success = -1;
	char* kernel_version = &(utsname()->release[0]);
	printk(KERN_EMERG "Version: %s\n", kernel_version);
	
	if(find_sys_call_table(kernel_version) == 0){
		if (syscall_table_address != 0){
			unsigned long* syscall_table = (unsigned long*)syscall_table_address;
			write_cr0 (read_cr0 () & (~ 0x10000));
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
			write_cr0 (read_cr0 () | 0x10000);
			printk(KERN_EMERG "[+] onload: sys_call_table hooked\n");
			success = 0;
		} else {
			printk(KERN_EMERG "[-] onload: syscall_table is NULL\n");
			success = -1;
		}
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
        write_cr0 (read_cr0 () | 0x10000);
        printk(KERN_EMERG "[+] onunload: sys_call_table unhooked\n");
    } else {
        printk(KERN_EMERG "[-] onunload: syscall_table is NULL\n");
    }
}

static void netio_logging_start(int net_io_flag, int syscall_success_flag, 
									int pids_ignore_length, int pids_ignore_list[],
									int ppids_ignore_length, int ppids_ignore_list[],
									int uids_length, int uids_list[], int ignore_uids_flag){
	int oldStopValue = stop;
	
	net_io = net_io_flag;
	syscall_success = syscall_success_flag;
	ignore_uids = ignore_uids_flag;
	pids_ignore_len = pids_ignore_length;
	ppids_ignore_len = ppids_ignore_length;
	uids_len = uids_length;
	
	copy_array(&pids_ignore[0], &pids_ignore_list[0], pids_ignore_len);
	copy_array(&ppids_ignore[0], &ppids_ignore_list[0], ppids_ignore_len);
	copy_array(&uids[0], &uids_list[0], uids_len);
	
	stop = 0;
	
	printk(KERN_EMERG "[start] Stop value: %d -> %d\n", oldStopValue, stop);
	printk(KERN_EMERG "syscall_success flag value: %d\n", syscall_success);
	printk(KERN_EMERG "net_io flag value: %d\n", net_io);
	printk(KERN_EMERG "ignore_uids flag value: %d\n", ignore_uids);
	print_array("pids_ignore", pids_ignore, pids_ignore_len);
	print_array("ppids_ignore", ppids_ignore, ppids_ignore_len);
	print_array("uids", uids, uids_len);
	
	if(ignore_uids == 1){
		printk(KERN_EMERG "[netio] Mode = ignoring given uids\n");	
	}else{
		printk(KERN_EMERG "[netio] Mode = capturing given uids only\n");		
	}
}

static void netio_logging_stop(void){
	int oldStopValue = stop;
	stop = 1;
	printk(KERN_EMERG "[stop] Stop value: %d -> %d\n", oldStopValue, stop);
}

EXPORT_SYMBOL(netio_logging_start);
EXPORT_SYMBOL(netio_logging_stop);

module_init(onload);
module_exit(onunload);
