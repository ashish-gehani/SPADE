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
#include <linux/module.h>
#include <linux/moduleparam.h>
#include <linux/kernel.h>
#include <linux/init.h>
#include <linux/string.h>

#include "build.h"

MODULE_LICENSE("GPL");

#define MAX_FIELDS 64
#define NO_KEY "0"
#define args_string_len 512

static int nf_handle_user = 0;
static int nf_hooks = 0;
static int nf_hooks_log_all_ct = 0;
static int syscall_success = -1;
static int net_io = 0;
static int namespaces = 0;
static int ignore_uids = 1;
static int pids_ignore[MAX_FIELDS];
static int ppids_ignore[MAX_FIELDS];
static int uids[MAX_FIELDS];
static int harden_tgids[MAX_FIELDS];

static char* key = NO_KEY; // means not set

static int pids_ignore_len = 0;
static int ppids_ignore_len = 0;
static int uids_len = 0;
static int harden_tgids_len = 0;

void print_args(const char*);
int str_equal(const char* h1, const char* h2); // 1 = equal, 0 = not equal

int str_equal(const char* h1, const char* h2){
	if(h1 == NULL && h2 == NULL){
		return 1;
	}else if((h1 == NULL && h2 != NULL) || (h1 != NULL && h2 == NULL)){
		return 0;
	}else{
		return strcmp(h1, h2) == 0 ? 1 : 0;
	}
}

void print_args(const char* module_name){
	int a = 0;
	int args_string_index = 0;
	char args_string[args_string_len];

	memset(&args_string[args_string_index], 0, args_string_len);

	args_string_index += sprintf(&args_string[args_string_index],
				"[%s] ARGS : syscall_success = %d, net_io = %d, uid_mode = %s, namespaces = %d, nf_hooks = %d, nf_hooks_log_all_ct = %d, nf_handle_user = %d, ",
				module_name, syscall_success, net_io, (ignore_uids == 1 ? "ignore" : "capture"), namespaces, nf_hooks, nf_hooks_log_all_ct, nf_handle_user);

	args_string_index += sprintf(&args_string[args_string_index], "pids_ignore = [ ");
	
	for(a = 0; a<pids_ignore_len; a++){
		args_string_index += sprintf(&args_string[args_string_index], "%d,", pids_ignore[a]);
	}
	args_string_index--; // delete comma
	args_string_index += sprintf(&args_string[args_string_index], " ], ppids_ignore = [ ");
	
	for(a = 0; a<ppids_ignore_len; a++){
		args_string_index += sprintf(&args_string[args_string_index], "%d,", ppids_ignore[a]);
	}
	args_string_index--; // delete comma
	args_string_index += sprintf(&args_string[args_string_index], " ], uids = [ ");
	
	for(a = 0; a<uids_len; a++){
		args_string_index += sprintf(&args_string[args_string_index], "%d,", uids[a]);
	}
	args_string_index--; // delete comma
	args_string_index += sprintf(&args_string[args_string_index], " ], harden_tgids = [ ");
	
	for(a = 0; a<harden_tgids_len; a++){
		args_string_index += sprintf(&args_string[args_string_index], "%d,", harden_tgids[a]);
	}
	args_string_index--; // delete comma
	args_string_index += sprintf(&args_string[args_string_index], " ]\n");
	
	printk(KERN_EMERG "%s", &args_string[0]);
	
	// TODO remove
	// printk(KERN_EMERG "[%s] DEBUG. key = '%s', keylen = '%ld'\n", module_name, key, strlen(key));	
}

