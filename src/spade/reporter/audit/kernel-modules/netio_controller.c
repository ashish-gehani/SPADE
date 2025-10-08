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
#include "args.h"

module_param(nf_handle_user, int, 0000);
MODULE_PARM_DESC(nf_handle_user, "0 for no, 1 for add");
module_param(nf_hooks, int, 0000);
MODULE_PARM_DESC(nf_hooks, "0 for no, 1 for add");
module_param(nf_hooks_log_all_ct, int, 0000);
MODULE_PARM_DESC(nf_hooks_log_all_ct, "1 for yes, 0 for only NEW");
module_param(syscall_success, int, 0000);
MODULE_PARM_DESC(syscall_success, "0 for failed, 1 for success, don't specify for all");
module_param(net_io, int, 0000);
MODULE_PARM_DESC(net_io, "0 for no, 1 for yes. default=0");
module_param(namespaces, int, 0000);
MODULE_PARM_DESC(namespaces, "0 for no, 1 for yes. default=0");
module_param(ignore_uids, int, 0000);
MODULE_PARM_DESC(ignore_uids, "0 for capturing the given uid only, 1 for ignoring the given uid only. default=1");
module_param_array(pids_ignore, int, &pids_ignore_len, 0000);
MODULE_PARM_DESC(pids_ignore, "Comma-separated pids to ignore");
module_param_array(ppids_ignore, int, &ppids_ignore_len, 0000);
MODULE_PARM_DESC(ppids_ignore, "Comma-separated ppids to ignore");
module_param_array(uids, int, &uids_len, 0000);
MODULE_PARM_DESC(uids, "Comma-separated uids list (to ignore or capture)");
module_param_array(harden_tgids, int, &harden_tgids_len, 0000);
MODULE_PARM_DESC(harden_tgids, "Comma-separated tgids list to harden");

extern int netio_logging_start(char* caller_build_hash, int, int, int, int[], int, int[], int, int[], int, int, int[], int, int, int, int); // starts logging
extern void netio_logging_stop(char* caller_build_hash); // stops logging

static int __init onload(void){
	char* module_name = CONTROLLER_MODULE_NAME;
	int success = 0;
	int total_fields = pids_ignore_len + ppids_ignore_len + uids_len;
	if(total_fields >= ARG_ARRAY_MAX){
		printk(KERN_EMERG "[%s] SEVERE Total pid, ppid, uid fields (%d) must be less than %d\n", module_name, total_fields, ARG_ARRAY_MAX);
		success = -1;
	}
	if(net_io != 0 && net_io != 1){
		printk(KERN_EMERG "[%s] SEVERE Invalid net_io value: %d (Only 0 or 1 allowed)\n", module_name, net_io);
		success = -1;	
	}
	if(namespaces != 0 && namespaces != 1){
		printk(KERN_EMERG "[%s] SEVERE Invalid namespaces value: %d (Only 0 or 1 allowed)\n", module_name, namespaces);
		success = -1;
	}
	if(ignore_uids != 0 && ignore_uids != 1){
		printk(KERN_EMERG "[%s] SEVERE Invalid ignore_uids value: %d (Only 0 or 1 allowed)\n", module_name, ignore_uids);
		success = -1;	
	}
	if(syscall_success != 0 && syscall_success != 1 && syscall_success != -1){
		printk(KERN_EMERG "[%s] SEVERE Invalid syscall_success value: %d (Only 0, 1 or -1 allowed)\n", module_name, syscall_success);
		success = -1;	
	}
	if(pids_ignore_len >= ARG_ARRAY_MAX){
		printk(KERN_EMERG "[%s] SEVERE pids_ignore_len (%d) should be less than %d\n", module_name, pids_ignore_len, ARG_ARRAY_MAX);
		success = -1;
	}
	if(ppids_ignore_len >= ARG_ARRAY_MAX){
		printk(KERN_EMERG "[%s] SEVERE ppids_ignore_len (%d) should be less than %d\n", module_name, ppids_ignore_len, ARG_ARRAY_MAX);
		success = -1;
	}
	if(uids_len >= ARG_ARRAY_MAX){
		printk(KERN_EMERG "[%s] SEVERE uids_ignore_len (%d) should be less than %d\n", module_name, uids_len, ARG_ARRAY_MAX);
		success = -1;
	}
	if(harden_tgids_len >= ARG_ARRAY_MAX){
		printk(KERN_EMERG "[%s] SEVERE harden_tgids_len (%d) should be less than %d\n", module_name, harden_tgids_len, ARG_ARRAY_MAX);
		success = -1;
	}
	if(nf_hooks != 0 && nf_hooks != 1){
		printk(KERN_EMERG "[%s] SEVERE Invalid nf_hooks value: %d (Only 0 or 1 allowed)\n", module_name, nf_hooks);
		success = -1;
	}
	if(nf_hooks_log_all_ct != 0 && nf_hooks_log_all_ct != 1){
		printk(KERN_EMERG "[%s] SEVERE Invalid nf_hooks_log_all_ct value: %d (Only 0 or 1 allowed)\n", module_name, nf_hooks_log_all_ct);
		success = -1;
	}
	if(nf_handle_user != 0 && nf_handle_user != 1){
		printk(KERN_EMERG "[%s] SEVERE Invalid nf_handle_user value: %d (Only 0 or 1 allowed)\n", module_name, nf_handle_user);
		success = -1;
	}

	print_args(module_name);

	if(success == -1){
		return -1;
	}else{
		if(netio_logging_start(BUILD_HASH, net_io, syscall_success, pids_ignore_len, pids_ignore,
						ppids_ignore_len, ppids_ignore, uids_len, uids, ignore_uids, harden_tgids_len, harden_tgids, namespaces,
						nf_hooks, nf_hooks_log_all_ct, nf_handle_user) == 1){
			return 0;
		}else{
			return -1;
		}
	}
}

static void __exit onunload(void) {
    netio_logging_stop(BUILD_HASH);
}

module_init(onload);
module_exit(onunload);
