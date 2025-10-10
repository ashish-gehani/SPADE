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

#include <linux/errno.h>
#include <linux/kernel.h>
#include <linux/param.h>
#include <linux/string.h>
#include <linux/moduleparam.h>

#include "spade/arg/constant.h"
#include "spade/arg/parse.h"
#include "spade/audit/param/param.h"


/*
	Default arguments
*/
static struct arg default_arg = {
	.nf = {
		.use_user = false,
		.hooks = false,
		.monitor_ct = AMMC_ALL
	},
	.monitor_syscalls = AMMS_ONLY_SUCCESSFUL,
	.network_io = false,
	.include_ns_info = false,
	.ignore_pids = {
		.len = 0
	},
	.ignore_ppids = {
		.len = 0
	},
	.user = {
		.uid_monitor_mode = AMM_IGNORE,
		.uids = {
			.len = 0
		}
	}
};

// General param parsers

static int set_uid_array(const char *param_name, const char *val, const struct kernel_param *kp)
{
	return arg_parse_uid_array("set_uid_array", param_name, val, (struct arg_array_uid *)(kp->arg));
}

static int set_monitor_mode(const char *param_name, const char *val, const struct kernel_param *kp)
{
	return arg_parse_monitor_mode("set_monitor_mode", param_name, val, (enum arg_monitor_mode *)(kp->arg));
}

static int set_pid_array(const char *param_name, const char *val, const struct kernel_param *kp)
{
	return arg_parse_pid_array("set_pid_array", param_name, val, (struct arg_array_pid *)(kp->arg));
}

static int set_bool(const char *param_name, const char *val, const struct kernel_param *kp)
{
	return arg_parse_bool("set_bool", param_name, val, (bool *)(kp->arg));
}

static int set_monitor_syscalls(const char *param_name, const char *val, const struct kernel_param *kp)
{
	return arg_parse_monitor_syscalls("set_monitor_syscalls", param_name, val, (enum arg_monitor_syscalls *)(kp->arg));
}

static int set_monitor_ct(const char *param_name, const char *val, const struct kernel_param *kp)
{
	return arg_parse_monitor_connections("set_monitor_ct", param_name, val, (enum arg_monitor_connections *)(kp->arg));
}

// Param setters

static int param_set_nf_use_user(const char *val, const struct kernel_param *kp)
{
	return set_bool(ARG_CONSTANT_NAME_NF_USE_USER_STR, val, kp);
}

static int param_set_nf_hooks(const char *val, const struct kernel_param *kp)
{
	return set_bool(ARG_CONSTANT_NAME_NF_HOOKS_STR, val, kp);
}

static int param_set_nf_monitor_ct(const char *val, const struct kernel_param *kp)
{
	return set_monitor_ct(ARG_CONSTANT_NAME_NF_MONITOR_CT_STR, val, kp);
}

static int param_set_network_io(const char *val, const struct kernel_param *kp)
{
	return set_bool(ARG_CONSTANT_NAME_NETWORK_IO_STR, val, kp);
}

static int param_set_include_ns_info(const char *val, const struct kernel_param *kp)
{
	return set_bool(ARG_CONSTANT_NAME_INCLUDE_NS_INFO_STR, val, kp);
}

static int param_set_monitor_syscalls(const char *val, const struct kernel_param *kp)
{
	return set_monitor_syscalls(ARG_CONSTANT_NAME_MONITOR_SYSCALLS_STR, val, kp);
}

static int param_set_ignore_pids(const char *val, const struct kernel_param *kp)
{
	return set_pid_array(ARG_CONSTANT_NAME_IGNORE_PIDS_STR, val, kp);
}

static int param_set_ignore_ppids(const char *val, const struct kernel_param *kp)
{
	return set_pid_array(ARG_CONSTANT_NAME_IGNORE_PPIDS_STR, val, kp);
}

static int param_set_uid_monitor_mode(const char *val, const struct kernel_param *kp)
{
	return set_monitor_mode(ARG_CONSTANT_NAME_UID_MONITOR_MODE_STR, val, kp);
}

static int param_set_uids(const char *val, const struct kernel_param *kp)
{
	return set_uid_array(ARG_CONSTANT_NAME_UIDS_STR, val, kp);
}

// Kernel param operations

static const struct kernel_param_ops param_ops_nf_use_user = {
	.set = param_set_nf_use_user,
	.get = 0,
};

static const struct kernel_param_ops param_ops_nf_hooks = {
	.set = param_set_nf_hooks,
	.get = 0,
};

static const struct kernel_param_ops param_ops_nf_monitor_ct = {
	.set = param_set_nf_monitor_ct,
	.get = 0,
};

static const struct kernel_param_ops param_ops_network_io = {
	.set = param_set_network_io,
	.get = 0,
};

static const struct kernel_param_ops param_ops_include_ns_info = {
	.set = param_set_include_ns_info,
	.get = 0,
};

static const struct kernel_param_ops param_ops_monitor_syscalls = {
	.set = param_set_monitor_syscalls,
	.get = 0,
};

static const struct kernel_param_ops param_ops_ignore_pids = {
	.set = param_set_ignore_pids,
	.get = 0,
};

static const struct kernel_param_ops param_ops_ignore_ppids = {
	.set = param_set_ignore_ppids,
	.get = 0,
};

static const struct kernel_param_ops param_ops_uid_monitor_mode = {
	.set = param_set_uid_monitor_mode,
	.get = 0,
};

static const struct kernel_param_ops param_ops_uids = {
	.set = param_set_uids,
	.get = 0,
};

// Kernel module params

#define DECLARE_PARAM_AND_DESC(name, param_ops, param_ptr, param_perm, param_desc) \
	module_param_cb(name, param_ops, param_ptr, param_perm); \
	MODULE_PARM_DESC(name, param_desc)

DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_NF_USE_USER, &param_ops_nf_use_user, &default_arg.nf.use_user, 0000, ARG_CONSTANT_DESC_NF_USE_USER);
DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_NF_HOOKS, &param_ops_nf_hooks, &default_arg.nf.hooks, 0000, ARG_CONSTANT_DESC_NF_HOOKS);
DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_NF_MONITOR_CT, &param_ops_nf_monitor_ct, &default_arg.nf.monitor_ct, 0000, ARG_CONSTANT_DESC_NF_MONITOR_CT);
DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_NETWORK_IO, &param_ops_network_io, &default_arg.network_io, 0000, ARG_CONSTANT_DESC_NETWORK_IO);
DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_INCLUDE_NS_INFO, &param_ops_include_ns_info, &default_arg.include_ns_info, 0000, ARG_CONSTANT_DESC_INCLUDE_NS_INFO);
DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_MONITOR_SYSCALLS, &param_ops_monitor_syscalls, &default_arg.monitor_syscalls, 0000, ARG_CONSTANT_DESC_MONITOR_SYSCALLS);
DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_IGNORE_PIDS, &param_ops_ignore_pids, &default_arg.ignore_pids, 0000, ARG_CONSTANT_DESC_IGNORE_PIDS);
DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_IGNORE_PPIDS, &param_ops_ignore_ppids, &default_arg.ignore_ppids, 0000, ARG_CONSTANT_DESC_IGNORE_PPIDS);
DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_UID_MONITOR_MODE, &param_ops_uid_monitor_mode, &default_arg.user.uid_monitor_mode, 0000, ARG_CONSTANT_DESC_UID_MONITOR_MODE);
DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_UIDS, &param_ops_uids, &default_arg.user.uids, 0000, ARG_CONSTANT_DESC_UIDS);


int param_copy_validated_args(struct arg *dst)
{
    if (!dst)
        return -EINVAL;

    memcpy(dst, &default_arg, sizeof(struct arg));
    return 0;
}