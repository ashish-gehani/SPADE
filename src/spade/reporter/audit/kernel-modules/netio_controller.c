#include <linux/module.h>  /* Needed by all kernel modules */
#include <linux/moduleparam.h>
#include <linux/kernel.h>  /* Needed for loglevels (KERN_WARNING, KERN_EMERG, KERN_INFO, etc.) */
#include <linux/init.h>    /* Needed for __init and __exit macros. */

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

module_param(syscall_success, int, 0000);
MODULE_PARM_DESC(syscall_success, "0 for failed, 1 for success, don't specify for all");
module_param(net_io, int, 0000);
MODULE_PARM_DESC(net_io, "0 for no, 1 for yes. default=0");
module_param(ignore_uids, int, 0000);
MODULE_PARM_DESC(ignore_uids, "0 for capturing the given uid only, 1 for ignore the given uid only. default=1");
module_param_array(pids_ignore, int, &pids_ignore_len, 0000);
MODULE_PARM_DESC(pids_ignore, "Comma-separated pids to ignore");
module_param_array(ppids_ignore, int, &ppids_ignore_len, 0000);
MODULE_PARM_DESC(ppids_ignore, "Comma-separated ppids to ignore");
module_param_array(uids, int, &uids_len, 0000);
MODULE_PARM_DESC(uids, "Comma-separated uids list (to ignore or capture)");

extern void netio_logging_start(int, int, int, int[], int, int[], int, int[], int); // starts logging
extern void netio_logging_stop(void); // stops logging

static int __init onload(void){
	if(net_io != 0 && net_io != 1){
		printk(KERN_EMERG "Invalid net_io value: %d (Only 0 or 1 allowed)\n", net_io);
		return -1;	
	}
	if(ignore_uids != 0 && ignore_uids != 1){
		printk(KERN_EMERG "Invalid ignore_uids value: %d (Only 0 or 1 allowed)\n", ignore_uids);
		return -1;	
	}
	if(syscall_success != 0 && syscall_success != 1 && syscall_success != -1){
		printk(KERN_EMERG "Invalid syscall_success value: %d (Only 0, 1 or -1 allowed)\n", syscall_success);
		return -1;	
	}
	if(pids_ignore_len >= MAX_FIELDS){
		printk(KERN_EMERG "pids_ignore_len '%d' should be less than %d\n", pids_ignore_len, MAX_FIELDS);
		return -1;
	}
	if(ppids_ignore_len >= MAX_FIELDS){
		printk(KERN_EMERG "ppids_ignore_len '%d' should be less than %d\n", ppids_ignore_len, MAX_FIELDS);
		return -1;
	}
	if(uids_len >= MAX_FIELDS){
		printk(KERN_EMERG "uids_ignore_len '%d' should be less than %d\n", uids_len, MAX_FIELDS);
		return -1;
	}
	netio_logging_start(net_io, syscall_success, pids_ignore_len, pids_ignore,
						ppids_ignore_len, ppids_ignore, uids_len, uids, ignore_uids);
    return 0;
}

static void __exit onunload(void) {
    netio_logging_stop();
}

module_init(onload);
module_exit(onunload);
