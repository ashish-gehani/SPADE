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

#include <linux/types.h>
#include <linux/kernel.h>


#ifndef _SPADE_UTIL_LOG_LOG_H
#define _SPADE_UTIL_LOG_LOG_H

/*
    Add a warning to dmesg log.

    Params:
        log_id      : The id to log to identify who logged it.
        msg         : The msg to log.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int __printf(2, 3) util_log_warn(const char *log_id, const char *fmt, ...);

int __printf(2, 3) util_log_info(const char *log_id, const char *fmt, ...);

int __printf(2, 3) util_log_debug(const char *log_id, const char *fmt, ...);

int __printf(3, 4) util_log_raw(const char *level, const char *log_id, const char *fmt, ...);

#endif // _SPADE_UTIL_LOG_LOG_H