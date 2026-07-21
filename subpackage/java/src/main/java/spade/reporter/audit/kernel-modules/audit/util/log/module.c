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
#include <linux/string.h>
#include <linux/time.h>
#include <linux/timekeeping.h>

#include "audit/util/log/log.h"
#include "audit/util/log/module.h"


#define TIME_BUF_LEN 32


static struct
{
    bool initialized;
    char time_buf[TIME_BUF_LEN];
} global_state = {
    .initialized = false
};


static void populate_current_time(char *buf, size_t buf_size)
{
	struct timespec64 ts;
	struct tm tm_result;

    memset(buf, 0, buf_size);
	ktime_get_real_ts64(&ts);
	time64_to_tm(ts.tv_sec, sys_tz.tz_minuteswest * 60, &tm_result);

	snprintf(
        buf, buf_size, "%04ld-%02d-%02d_%02d:%02d:%02d.%03ld",
        tm_result.tm_year + 1900,
        tm_result.tm_mon + 1,
        tm_result.tm_mday,
        tm_result.tm_hour,
        tm_result.tm_min,
        tm_result.tm_sec,
        ts.tv_nsec / 1000000
    );
}

static void _ensure_init(void)
{
    if (global_state.initialized)
    {
        return;
    }
    populate_current_time(&global_state.time_buf[0], TIME_BUF_LEN);
    global_state.initialized = true;
}

static char *get_time_in_state(void)
{
    _ensure_init();
    return &global_state.time_buf[0];
}

static int _util_log_module_msg(const char *level, const char *log_id)
{
    return util_log_raw(level, log_id, "[instance_id=%s]", get_time_in_state());
}

int util_log_module_loading_started(void)
{
    return _util_log_module_msg(KERN_INFO, "spade:module:state:loading:started");
}

int util_log_module_loading_success(void)
{
    return _util_log_module_msg(KERN_INFO, "spade:module:state:loading:finished:success");
}

int util_log_module_loading_failure(void)
{
    return _util_log_module_msg(KERN_WARNING, "spade:module:state:loading:finished:failure");
}

int util_log_module_unloading_started(void)
{
    return _util_log_module_msg(KERN_INFO, "spade:module:state:unloading:started");
}

int util_log_module_unloading_success(void)
{
    return _util_log_module_msg(KERN_INFO, "spade:module:state:unloading:finished:success");
}

int util_log_module_unloading_failure(void)
{
    return _util_log_module_msg(KERN_WARNING, "spade:module:state:unloading:finished:failure");
}
