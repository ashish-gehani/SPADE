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

#include <linux/kernel.h>
#include <linux/errno.h>
#include <linux/module.h>

#include "spade/config/config.h"
#include "spade/util/log/log.h"


extern const char *SPADE_MODULE_NAME;


static int util_log_common_va(const char *level, const char *log_id, const char *fmt, va_list args)
{
	const char *mod_name;
	char buf[512];
	int len = 0;

	mod_name = SPADE_MODULE_NAME ? SPADE_MODULE_NAME : "unknown";

	if (!level || !log_id)
		return -EINVAL;

	len = scnprintf(buf, sizeof(buf), "[%s] [%s] : ", mod_name, log_id);
	len += vsnprintf(buf + len, sizeof(buf) - len, fmt, args);

	printk("%s%s\n", level, buf);

	return 0;
}

int util_log_raw(const char *level, const char *log_id, const char *fmt, ...)
{
	va_list args;
	int ret;

	if (!level || !log_id || !fmt)
		return -EINVAL;

	va_start(args, fmt);
	ret = util_log_common_va(level, log_id, fmt, args);
	va_end(args);

	return ret;
}

int util_log_warn(const char *log_id, const char *fmt, ...)
{
	va_list args;
	int ret;

	if (!log_id || !fmt)
		return -EINVAL;

	va_start(args, fmt);
	ret = util_log_common_va(KERN_WARNING, log_id, fmt, args);
	va_end(args);

	return ret;
}

int util_log_info(const char *log_id, const char *fmt, ...)
{
	va_list args;
	int ret;

	if (!log_id || !fmt)
		return -EINVAL;

	va_start(args, fmt);
	ret = util_log_common_va(KERN_INFO, log_id, fmt, args);
	va_end(args);

	return ret;
}

int util_log_debug(const char *log_id, const char *fmt, ...)
{
	va_list args;
	int ret;

	if (!log_id || !fmt)
		return -EINVAL;

	if (!CONFIG_GLOBAL.debug)
	{
		return 0;
	}

	va_start(args, fmt);
	ret = util_log_common_va(KERN_DEBUG, log_id, fmt, args);
	va_end(args);

	return ret;
}


