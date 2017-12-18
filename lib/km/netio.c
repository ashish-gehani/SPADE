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
static int pids_ignore[MAX_FIELDS];
static int ppids_ignore[MAX_FIELDS];
static int uids_ignore[MAX_FIELDS];

static int pids_ignore_len = 0;
static int ppids_ignore_len = 0;
static int uids_ignore_len = 0;

static volatile int stop = 1;

unsigned long syscall_table_address = 0;

asmlinkage long (*original_bind)(int, const struct sockaddr*, int);
asmlinkage long (*original_connect)(int, const struct sockaddr*, int);
asmlinkage long (*original_accept)(int, struct sockaddr*, unsigned int*);
asmlinkage long (*original_accept4)(int, struct sockaddr*, unsigned int*, int);
asmlinkage long (*original_sendto)(int, const void*, size_t, int, const struct sockaddr*, unsigned int);
asmlinkage long (*original_sendmsg)(int, const struct msghdr*, int);
asmlinkage long (*original_sendmmsg)(int, struct mmsghdr*, unsigned int, unsigned int);
asmlinkage long (*original_recvfrom)(int, void*, size_t, int, struct sockaddr*, unsigned int*);
asmlinkage long (*original_recvmsg)(int, struct msghdr*, int);
asmlinkage long (*original_recvmmsg)(int, struct mmsghdr*, unsigned int, unsigned int, struct timespec*);

static void to_hex(unsigned char *dst, const unsigned char *buf, size_t len);
static void print_array(const char*, int[], int);
static int exists_in_array(int, int[], int);
static int log_syscall(int, int, int, int);
static int find_sys_call_table(char*);
static void copy_array(int* dst, int* src, int len);
static void netio_logging_start(int net_io_flag, int syscall_success_flag, 
									int pids_ignore_length, int pids_ignore_list[],
									int ppids_ignore_length, int ppids_ignore_list[],
									int uids_ignore_length, int uids_ignore_list[]);
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
	if(exists_in_array(uid, uids_ignore, uids_ignore_len) > 0){
		return -1;
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

static void to_hex(unsigned char *dst, const unsigned char *buf, size_t len){
	int i;
	for (i = 0; i < len; i++){
		*dst++ = hex_asc_upper[((buf[i]) & 0xf0) >> 4];
		*dst++ = hex_asc_upper[((buf[i]) & 0x0f)];
	}
	*dst = '\0';
}

static void log_to_audit(int syscallNumber, int fd, const struct sockaddr* remaddress, unsigned int remsize, long exit, int success){
	if(log_syscall((int)(current->pid), (int)(current->real_parent->pid), (int)(current->real_cred->uid.val), success) > 0){
		int sockType = -1;
		int family = -1;
		char* local_hex_ptr = NULL;
		char* remote_hex_ptr = NULL;
		struct socket *sock;
		struct sockaddr_storage locaddress;
		struct file* file;
		int err = 0;
		unsigned char comm_hex[(strlen(current->comm) * 2) + 1];
		
		// sock = sockfd_lookup_light(fd, &err);
		// Getting socket from fd directly. TODO
		
		// DOUBLE-CHECK - start
		struct files_struct* files = current->files;
		rcu_read_lock();
		file = files->fdt->fd[fd];
		if(file){
			sock = sock_from_file(file, &err);
		}
		rcu_read_unlock();
		// DOUBLE-CHECK - end
		
		if(sock){
			family = sock->ops->family;
			sockType = sock->type;
			if((family != AF_UNIX && family != AF_LOCAL && family != PF_UNIX && family != PF_LOCAL && 
				family != AF_INET && family != AF_INET6 && family != PF_INET && family != PF_INET6) && // only unix, and inet
					(sockType != SOCK_STREAM && sockType != SOCK_DGRAM)){ // only stream, and dgram
				return;
			}else{
				int locsize = sizeof(locaddress);
				err = sock->ops->getname(sock, (struct sockaddr *)&locaddress, &locsize, 0);
				if(!err){
					if(!(locsize < 0 || locsize >= _K_SS_MAXSIZE)){
						// abstract unix socket check. TODO
						local_hex_ptr = (char*)kmalloc((locsize*2)+1, GFP_KERNEL);
						local_hex_ptr = (char*)memset(local_hex_ptr, 0, (locsize*2)+1);
						to_hex(local_hex_ptr, (void *)&locaddress, locsize);
					}
				}
			}
		}
		
		to_hex(&comm_hex[0], (void *)(&(current->comm)), strlen(current->comm));
		
		if(!(remaddress == NULL || remsize < 0 || remsize >= _K_SS_MAXSIZE)){
			remote_hex_ptr = (char*)kmalloc((remsize*2)+1, GFP_KERNEL);
			remote_hex_ptr = (char*)memset(remote_hex_ptr, 0, (remsize*2)+1);
			to_hex(remote_hex_ptr, (void *)remaddress, remsize);
		}
		
		// TODO other info needed to be logged?
		audit_log(NULL, GFP_KERNEL, AUDIT_USER, 
			"netio_intercepted=\"syscall=%d exit=%ld success=%d fd=%d pid=%d ppid=%d uid=%u euid=%u suid=%u fsuid=%u \
gid=%u egid=%u sgid=%u fsgid=%u comm=%s sock_type=%d local_saddr=%s remote_saddr=%s\"", 
			syscallNumber, exit, success, fd, current->pid, current->real_parent->pid,
			current->real_cred->uid.val, current->real_cred->euid.val, current->real_cred->suid.val, current->real_cred->fsuid.val,
			current->real_cred->gid.val, current->real_cred->egid.val, current->real_cred->sgid.val, current->real_cred->fsgid.val,
			&comm_hex[0], sockType, local_hex_ptr, remote_hex_ptr);
			
		if(remote_hex_ptr != NULL){
			kfree(remote_hex_ptr);	
		}
		
		if(local_hex_ptr != NULL){
			kfree(local_hex_ptr);
		}
	}
}

asmlinkage long new_bind(int fd, const struct sockaddr* remaddress, int remsize){
	long retval = original_bind(fd, remaddress, remsize);
	if(stop == 0){
		int syscallNumber = __NR_bind;
		int success;
		if(retval == 0){
			success = 1;
		}else{
			success = 0;
		}
		log_to_audit(syscallNumber, fd, remaddress, remsize, retval, success);
	}
	return retval;
}

asmlinkage long new_connect(int fd, const struct sockaddr* remaddress, int remsize){
	long retval = original_connect(fd, remaddress, remsize);
	if(stop == 0){
		int syscallNumber = __NR_connect;
		int success;
		if(retval >= 0 || retval == -EINPROGRESS){ // check EINPROGRESS
			success = 1;
		}else{
			success = 0;
		}
		log_to_audit(syscallNumber, fd, remaddress, remsize, retval, success);
	}
	return retval;
}

asmlinkage long new_accept(int fd, struct sockaddr* remaddress, unsigned int* socklen){
	long retval = original_accept(fd, remaddress, socklen);
	if(stop == 0){
		int syscallNumber = __NR_accept;
		int success;
		if(retval >= 0){
			success = 1;
		}else{
			success = 0;
		}
		if(socklen != NULL){
			log_to_audit(syscallNumber, fd, remaddress, *socklen, retval, success);
		}else{
			log_to_audit(syscallNumber, fd, remaddress, 0, retval, success);
		}
	}
	return retval;
}

asmlinkage long new_accept4(int fd, struct sockaddr* remaddress, unsigned int* socklen, int flags){
	long retval = original_accept4(fd, remaddress, socklen, flags);
	if(stop == 0){
		int syscallNumber = __NR_accept4;
		int success;
		if(retval >= 0){
			success = 1;
		}else{
			success = 0;
		}
		if(socklen != NULL){
			log_to_audit(syscallNumber, fd, remaddress, *socklen, retval, success);
		}else{
			log_to_audit(syscallNumber, fd, remaddress, 0, retval, success);
		}
	}
	return retval;
}

asmlinkage long new_recvmsg (int fd, struct msghdr* msgheader, int flags){
	long retval = original_recvmsg(fd, msgheader, flags);
	if(stop == 0){
		if(net_io == 1){
			int syscallNumber = __NR_recvmsg;
			int success;
			if(retval >= 0){
				success = 1;
			}else{
				success = 0;
			}
			if(msgheader != NULL){
				if(msgheader->msg_name != NULL){
					log_to_audit(syscallNumber, fd, msgheader->msg_name, msgheader->msg_namelen, retval, success);
				}else{
					log_to_audit(syscallNumber, fd, NULL, 0, retval, success);
				}
			}else{
				log_to_audit(syscallNumber, fd, NULL, 0, retval, success);
			}
		}
	}
	return retval;
}

asmlinkage long new_sendmsg (int fd, const struct msghdr* msgheader, int flags){
	long retval = original_sendmsg(fd, msgheader, flags);
	if(stop == 0){
		if(net_io == 1){
			int syscallNumber = __NR_sendmsg;
			int success;
			if(retval >= 0){
				success = 1;
			}else{
				success = 0;
			}
			if(msgheader != NULL){
				if(msgheader->msg_name != NULL){
					log_to_audit(syscallNumber, fd, msgheader->msg_name, msgheader->msg_namelen, retval, success);
				}else{
					log_to_audit(syscallNumber, fd, NULL, 0, retval, success);
				}
			}else{
				log_to_audit(syscallNumber, fd, NULL, 0, retval, success);
			}
		}
	}
	return retval;
}

asmlinkage long new_recvfrom (int fd, void* msg, size_t msgsize, int flags, struct sockaddr* remaddress, unsigned int* remsize) {
	long retval = original_recvfrom(fd, msg, msgsize, flags, remaddress, remsize);
	if(stop == 0){
		if(net_io == 1){
			int syscallNumber = __NR_recvfrom;
			int success;
			if(retval >= 0){
				success = 1;
			}else{
				success = 0;
			}
			if(remsize != NULL){
				log_to_audit(syscallNumber, fd, remaddress, *remsize, retval, success);
			}else{
				log_to_audit(syscallNumber, fd, remaddress, 0, retval, success);
			}
		}
	}
	return retval;
}

asmlinkage long new_sendto (int fd, const void* msg, size_t msgsize, int flags, const struct sockaddr* remaddress, unsigned int remsize) {
	long retval = original_sendto(fd, msg, msgsize, flags, remaddress, remsize);
	if(stop == 0){
		if(net_io == 1){
			int syscallNumber = __NR_sendto;
			int success;
			if(retval >= 0){
				success = 1;
			}else{
				success = 0;
			}
			log_to_audit(syscallNumber, fd, remaddress, remsize, retval, success);
		}
	}
    return retval;
}

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
			original_sendmmsg = (void *)syscall_table[__NR_sendmmsg];
			syscall_table[__NR_sendmmsg] = (unsigned long)&new_sendmmsg;
			original_recvfrom = (void *)syscall_table[__NR_recvfrom];
			syscall_table[__NR_recvfrom] = (unsigned long)&new_recvfrom;
			original_recvmsg = (void *)syscall_table[__NR_recvmsg];
			syscall_table[__NR_recvmsg] = (unsigned long)&new_recvmsg;
			original_recvmmsg = (void *)syscall_table[__NR_recvmmsg];
			syscall_table[__NR_recvmmsg] = (unsigned long)&new_recvmmsg;
			
			/*
			printk(KERN_EMERG "kernel symbol Syscall table address: %p\n", syscall_table);
			printk(KERN_EMERG "bind: original -> %p, new -> %p\n", original_bind, new_bind);
			printk(KERN_EMERG "connect: original -> %p, new -> %p\n", original_connect, new_connect);
			printk(KERN_EMERG "accept: original -> %p, new -> %p\n", original_accept, new_accept);
			printk(KERN_EMERG "accept4: original -> %p, new -> %p\n", original_accept4, new_accept4);
			if(net_io == 1){
				printk(KERN_EMERG "sendto: original -> %p, new -> %p\n", original_sendto, new_sendto);
				printk(KERN_EMERG "sendmsg: original -> %p, new -> %p\n", original_sendmsg, new_sendmsg);
				printk(KERN_EMERG "sendmmsg: original -> %p, new -> %p\n", original_sendmmsg, new_sendmmsg);
				printk(KERN_EMERG "recvfrom: original -> %p, new -> %p\n", original_recvfrom, new_recvfrom);
				printk(KERN_EMERG "recvmsg: original -> %p, new -> %p\n", original_recvmsg, new_recvmsg);
				printk(KERN_EMERG "recvmmsg: original -> %p, new -> %p\n", original_recvmmsg, new_recvmmsg);
			}
			printk(KERN_EMERG "print_array: %p\n", print_array);
			printk(KERN_EMERG "to_hex: %p\n", to_hex);
			printk(KERN_EMERG "exists_in_array: %p\n", exists_in_array);
			printk(KERN_EMERG "log_syscall: %p\n", log_syscall);
			printk(KERN_EMERG "find_sys_call_table: %p\n", find_sys_call_table);
			printk(KERN_EMERG "log_to_audit: %p\n", log_to_audit);
			*/
			
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
		syscall_table[__NR_sendmmsg] = (unsigned long)original_sendmmsg;
		syscall_table[__NR_recvfrom] = (unsigned long)original_recvfrom;
		syscall_table[__NR_recvmsg] = (unsigned long)original_recvmsg;
		syscall_table[__NR_recvmmsg] = (unsigned long)original_recvmmsg;
		
        write_cr0 (read_cr0 () | 0x10000);
        printk(KERN_EMERG "[+] onunload: sys_call_table unhooked\n");
    } else {
        printk(KERN_EMERG "[-] onunload: syscall_table is NULL\n");
    }
}

static void netio_logging_start(int net_io_flag, int syscall_success_flag, 
									int pids_ignore_length, int pids_ignore_list[],
									int ppids_ignore_length, int ppids_ignore_list[],
									int uids_ignore_length, int uids_ignore_list[]){
	int oldStopValue = stop;
	
	net_io = net_io_flag;
	syscall_success = syscall_success_flag;
	pids_ignore_len = pids_ignore_length;
	ppids_ignore_len = ppids_ignore_length;
	uids_ignore_len = uids_ignore_length;
	
	copy_array(&pids_ignore[0], &pids_ignore_list[0], pids_ignore_len);
	copy_array(&ppids_ignore[0], &ppids_ignore_list[0], ppids_ignore_len);
	copy_array(&uids_ignore[0], &uids_ignore_list[0], uids_ignore_len);
	
	stop = 0;
	
	printk(KERN_EMERG "[start] Stop value: %d -> %d\n", oldStopValue, stop);
	printk(KERN_EMERG "syscall_success flag value: %d\n", syscall_success);
	printk(KERN_EMERG "net_io flag value: %d\n", net_io);
	print_array("pids_ignore", pids_ignore, pids_ignore_len);
	print_array("ppids_ignore", ppids_ignore, ppids_ignore_len);
	print_array("uids_ignore", uids_ignore, uids_ignore_len);
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
