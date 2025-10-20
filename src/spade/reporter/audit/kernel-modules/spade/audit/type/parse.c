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

#include <linux/init.h>
#include <linux/module.h>
#include <linux/slab.h>
#include <linux/kernel.h>
#include <linux/param.h>
#include <linux/string.h>

#include "spade/audit/type/parse.h"
#include "spade/util/log/log.h"

int type_parse_monitor_mode(
	const char *log_id, const char *param_name,
	const char *src, enum type_monitor_mode *dst)
{
	int tmp, ret;

	if (!log_id || !param_name || !src || !dst)
		return -EINVAL;

	ret = kstrtoint(src, 0, &tmp);
	if (ret)
		return ret;

	if (tmp != TMM_CAPTURE && tmp != TMM_IGNORE)
	{
		util_log_warn("type_parse_monitor_mode", "Parameter (%s) has invalid value %d (must be 0=capture, 1=ignore)", param_name, tmp);
		return -EINVAL;
	}

	*dst = tmp;
	return 0;
}

int type_parse_pid_array(
	const char *log_id, const char *param_name,
	const char *src, struct type_array_pid *dst)
{
	struct type_array_pid *type = dst;
	char *src_copy, *src_item, *src_copy_ptr;
	pid_t src_item_pid, ret = 0;

	if (!log_id || !param_name || !src || !dst)
		return -EINVAL;

	src_copy = kstrdup(src, GFP_KERNEL);
	if (!src_copy)
		return -ENOMEM;

	type->len = 0;
	src_copy_ptr = src_copy;
	while ((src_item = strsep(&src_copy_ptr, ",")) != NULL)
	{
		if (*src_item == '\0')
			continue;
		ret = kstrtoint(src_item, 0, &src_item_pid);
		if (ret)
		{
			util_log_warn(log_id, "Parameter (%s) value contains invalid integer/pid: %s", param_name, src_item);
			break;
		}
		if (type->len >= TYPE_ARRAY_PID_MAX_LEN)
		{
			util_log_warn(log_id, "Parameter (%s) value exceeds max array len: %d", param_name, TYPE_ARRAY_PID_MAX_LEN);
			ret = -EINVAL;
			break;
		}
		type->arr[type->len++] = src_item_pid;
	}

	kfree(src_copy);
	return ret;
}

int type_parse_uid_array(
	const char *log_id, const char *param_name,
	const char *src, struct type_array_uid *dst)
{
	struct type_array_uid *type = dst;
	char *src_copy, *src_item, *src_copy_ptr;
	uid_t src_item_uid, ret = 0;

	if (!log_id || !param_name || !src || !dst)
		return -EINVAL;

	src_copy = kstrdup(src, GFP_KERNEL);
	if (!src_copy)
		return -ENOMEM;

	type->len = 0;
	src_copy_ptr = src_copy;
	while ((src_item = strsep(&src_copy_ptr, ",")) != NULL)
	{
		if (*src_item == '\0')
			continue;
		ret = kstrtouint(src_item, 0, &src_item_uid);
		if (ret)
		{
			util_log_warn(log_id, "Parameter (%s) value contains invalid unsigned integer/uid: %s", param_name, src_item);
			break;
		}
		if (type->len >= TYPE_ARRAY_UID_MAX_LEN)
		{
			util_log_warn(log_id, "Parameter (%s) value exceeds max array len: %d", param_name, TYPE_ARRAY_UID_MAX_LEN);
			ret = -EINVAL;
			break;
		}
		type->arr[type->len++] = src_item_uid;
	}

	kfree(src_copy);
	return ret;
}

int type_parse_bool(
	const char *log_id, const char *param_name,
	const char *src, bool *dst
)
{
	int tmp, ret;

	if (!log_id || !param_name || !src || !dst)
		return -EINVAL;

	ret = kstrtoint(src, 0, &tmp);
	if (ret)
		goto invalid;

	if (tmp == 0)
		*dst = false;
	else if (tmp == 1)
		*dst = true;
	else
		goto invalid;

	return 0;

invalid:
	util_log_warn(log_id, "Parameter (%s) value is not a boolean", param_name);
	return -EINVAL;
}

int type_parse_monitor_syscalls(
	const char *log_id, const char *param_name,
	const char *src, enum type_monitor_syscalls *dst)
{
	int tmp, ret;

	if (!log_id || !param_name || !src || !dst)
		return -EINVAL;

	ret = kstrtoint(src, 0, &tmp);
	if (ret)
		return ret;

	if (tmp != TMS_ALL && tmp != TMS_ONLY_FAILED && tmp != TMS_ONLY_SUCCESSFUL)
	{
		util_log_warn(log_id, "Parameter (%s) has invalid value %d (must be -1=all, 0=only_failed, 1=only_successful)", param_name, tmp);
		return -EINVAL;
	}

	*dst = tmp;
	return 0;
}

int type_parse_monitor_connections(
	const char *log_id, const char *param_name,
	const char *src, enum type_monitor_connections *dst
)
{
	int tmp, ret;

	if (!log_id || !param_name || !src || !dst)
		return -EINVAL;

	ret = kstrtoint(src, 0, &tmp);
	if (ret)
		return ret;

	if (tmp != TMC_ALL && tmp != TMC_ONLY_NEW)
	{
		util_log_warn(log_id, "Parameter (%s) has invalid value %d (must be -1=all, 0=only_new)", param_name, tmp);
		return -EINVAL;
	}

	*dst = tmp;
	return 0;
}