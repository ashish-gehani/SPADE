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

#include "spade/audit/arg/constant.h"
#include "spade/audit/type/parse.h"
#include "spade/audit/param/param.h"


/*
	Default arguments
*/
static struct arg default_arg = {
	.nf = {
		.use_user = ARG_DEFAULT_NF_USE_USER,
		.audit_hooks = ARG_DEFAULT_NF_AUDIT_HOOKS,
		.monitor_ct = ARG_DEFAULT_NF_MONITOR_CT
	},
	.monitor_function_result = ARG_DEFAULT_MONITOR_FUNCTION_RESULT,
	.network_io = ARG_DEFAULT_NETWORK_IO,
	.include_ns_info = ARG_DEFAULT_INCLUDE_NS_INFO,
	.monitor_pid = {
		.m_mode = ARG_DEFAULT_PID_MONITOR_MODE,
		.pids = ARG_DEFAULT_PIDS
	},
	.monitor_ppid = {
		.m_mode = ARG_DEFAULT_PPID_MONITOR_MODE,
		.ppids = ARG_DEFAULT_PPIDS
	},
	.monitor_user = {
		.m_mode = ARG_DEFAULT_UID_MONITOR_MODE,
		.uids = ARG_DEFAULT_UIDS
	},
	.config_file = ARG_DEFAULT_CONFIG_FILE,
	.harden = {
		.pids = ARG_DEFAULT_HARDEN_PIDS,
		.authorized_uids = ARG_DEFAULT_AUTHORIZED_UIDS
	}
};

// General param parsers

static int set_uid_array(const char *param_name, const char *val, const struct kernel_param *kp)
{
	return type_parse_uid_array("set_uid_array", param_name, val, (struct type_array_uid *)(kp->arg));
}

static int set_monitor_mode(const char *param_name, const char *val, const struct kernel_param *kp)
{
	return type_parse_monitor_mode("set_monitor_mode", param_name, val, (enum type_monitor_mode *)(kp->arg));
}

static int set_pid_array(const char *param_name, const char *val, const struct kernel_param *kp)
{
	return type_parse_pid_array("set_pid_array", param_name, val, (struct type_array_pid *)(kp->arg));
}

static int set_bool(const char *param_name, const char *val, const struct kernel_param *kp)
{
	return type_parse_bool("set_bool", param_name, val, (bool *)(kp->arg));
}

static int set_monitor_function_result(const char *param_name, const char *val, const struct kernel_param *kp)
{
	return type_parse_monitor_function_result("set_monitor_function_result", param_name, val, (enum type_monitor_function_result *)(kp->arg));
}

static int set_monitor_ct(const char *param_name, const char *val, const struct kernel_param *kp)
{
	return type_parse_monitor_connections("set_monitor_ct", param_name, val, (enum type_monitor_connections *)(kp->arg));
}

// Param setters

static int param_set_nf_use_user(const char *val, const struct kernel_param *kp)
{
	return set_bool(ARG_CONSTANT_NAME_NF_USE_USER_STR, val, kp);
}

static int param_set_nf_audit_hooks(const char *val, const struct kernel_param *kp)
{
	return set_bool(ARG_CONSTANT_NAME_NF_AUDIT_HOOKS_STR, val, kp);
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

static int param_set_monitor_function_result(const char *val, const struct kernel_param *kp)
{
	return set_monitor_function_result(ARG_CONSTANT_NAME_MONITOR_FUNCTION_RESULT_STR, val, kp);
}

static int param_set_pids(const char *val, const struct kernel_param *kp)
{
	return set_pid_array(ARG_CONSTANT_NAME_PIDS_STR, val, kp);
}

static int param_set_ppids(const char *val, const struct kernel_param *kp)
{
	return set_pid_array(ARG_CONSTANT_NAME_PPIDS_STR, val, kp);
}

static int param_set_pid_monitor_mode(const char *val, const struct kernel_param *kp)
{
	return set_monitor_mode(ARG_CONSTANT_NAME_PID_MONITOR_MODE_STR, val, kp);
}

static int param_set_ppid_monitor_mode(const char *val, const struct kernel_param *kp)
{
	return set_monitor_mode(ARG_CONSTANT_NAME_PPID_MONITOR_MODE_STR, val, kp);
}

static int param_set_uid_monitor_mode(const char *val, const struct kernel_param *kp)
{
	return set_monitor_mode(ARG_CONSTANT_NAME_UID_MONITOR_MODE_STR, val, kp);
}

static int param_set_uids(const char *val, const struct kernel_param *kp)
{
	return set_uid_array(ARG_CONSTANT_NAME_UIDS_STR, val, kp);
}

static int param_set_config_file(const char *val, const struct kernel_param *kp)
{
	char *config_file = (char *)(kp->arg);
	size_t len;

	if (!val)
		return -EINVAL;

	len = strlen(val);
	if (len >= PATH_MAX)
		return -E2BIG;

	strncpy(config_file, val, PATH_MAX - 1);
	config_file[PATH_MAX - 1] = '\0';

	return 0;
}

static int param_set_harden_pids(const char *val, const struct kernel_param *kp)
{
	return set_pid_array(ARG_CONSTANT_NAME_HARDEN_PIDS_STR, val, kp);
}

static int param_set_authorized_uids(const char *val, const struct kernel_param *kp)
{
	return set_uid_array(ARG_CONSTANT_NAME_AUTHORIZED_UIDS_STR, val, kp);
}

// Kernel param operations

static const struct kernel_param_ops param_ops_nf_use_user = {
	.set = param_set_nf_use_user,
	.get = 0,
};

static const struct kernel_param_ops param_ops_nf_audit_hooks = {
	.set = param_set_nf_audit_hooks,
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

static const struct kernel_param_ops param_ops_monitor_function_result = {
	.set = param_set_monitor_function_result,
	.get = 0,
};

static const struct kernel_param_ops param_ops_pids = {
	.set = param_set_pids,
	.get = 0,
};

static const struct kernel_param_ops param_ops_ppids = {
	.set = param_set_ppids,
	.get = 0,
};

static const struct kernel_param_ops param_ops_pid_monitor_mode = {
	.set = param_set_pid_monitor_mode,
	.get = 0,
};

static const struct kernel_param_ops param_ops_ppid_monitor_mode = {
	.set = param_set_ppid_monitor_mode,
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

static const struct kernel_param_ops param_ops_config_file = {
	.set = param_set_config_file,
	.get = 0,
};

static const struct kernel_param_ops param_ops_harden_pids = {
	.set = param_set_harden_pids,
	.get = 0,
};

static const struct kernel_param_ops param_ops_authorized_uids = {
	.set = param_set_authorized_uids,
	.get = 0,
};

// Kernel module params

#define DECLARE_PARAM_AND_DESC(name, param_ops, param_ptr, param_perm, param_desc) \
	module_param_cb(name, param_ops, param_ptr, param_perm); \
	MODULE_PARM_DESC(name, param_desc)

DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_NF_USE_USER, &param_ops_nf_use_user, &default_arg.nf.use_user, 0000, ARG_CONSTANT_DESC_NF_USE_USER);
DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_NF_AUDIT_HOOKS, &param_ops_nf_audit_hooks, &default_arg.nf.audit_hooks, 0000, ARG_CONSTANT_DESC_NF_AUDIT_HOOKS);
DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_NF_MONITOR_CT, &param_ops_nf_monitor_ct, &default_arg.nf.monitor_ct, 0000, ARG_CONSTANT_DESC_NF_MONITOR_CT);
DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_NETWORK_IO, &param_ops_network_io, &default_arg.network_io, 0000, ARG_CONSTANT_DESC_NETWORK_IO);
DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_INCLUDE_NS_INFO, &param_ops_include_ns_info, &default_arg.include_ns_info, 0000, ARG_CONSTANT_DESC_INCLUDE_NS_INFO);
// DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_MONITOR_FUNCTION_RESULT, &param_ops_monitor_function_result, &default_arg.monitor_function_result, 0000, ARG_CONSTANT_DESC_FUNCTION_RESULT);
DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_PID_MONITOR_MODE, &param_ops_pid_monitor_mode, &default_arg.monitor_pid.m_mode, 0000, ARG_CONSTANT_DESC_PID_MONITOR_MODE);
DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_PIDS, &param_ops_pids, &default_arg.monitor_pid.pids, 0000, ARG_CONSTANT_DESC_PIDS);
DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_PPID_MONITOR_MODE, &param_ops_ppid_monitor_mode, &default_arg.monitor_ppid.m_mode, 0000, ARG_CONSTANT_DESC_PPID_MONITOR_MODE);
DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_PPIDS, &param_ops_ppids, &default_arg.monitor_ppid.ppids, 0000, ARG_CONSTANT_DESC_PPIDS);
DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_UID_MONITOR_MODE, &param_ops_uid_monitor_mode, &default_arg.monitor_user.m_mode, 0000, ARG_CONSTANT_DESC_UID_MONITOR_MODE);
DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_UIDS, &param_ops_uids, &default_arg.monitor_user.uids, 0000, ARG_CONSTANT_DESC_UIDS);
DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_CONFIG_FILE, &param_ops_config_file, &default_arg.config_file, 0000, ARG_CONSTANT_DESC_CONFIG_FILE);
DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_HARDEN_PIDS, &param_ops_harden_pids, &default_arg.harden.pids, 0000, ARG_CONSTANT_DESC_HARDEN_PIDS);
DECLARE_PARAM_AND_DESC(ARG_CONSTANT_NAME_AUTHORIZED_UIDS, &param_ops_authorized_uids, &default_arg.harden.authorized_uids, 0000, ARG_CONSTANT_DESC_AUTHORIZED_UIDS);


int param_copy_validated_args(struct arg *dst)
{
    if (!dst)
        return -EINVAL;

    memcpy(dst, &default_arg, sizeof(struct arg));
    return 0;
}