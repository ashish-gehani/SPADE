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

#ifndef _SPADE_AUDIT_TYPE_H
#define _SPADE_AUDIT_TYPE_H

#include <linux/init.h>
#include <linux/kernel.h>


#define TYPE_ARRAY_PID_MAX_LEN  64
#define TYPE_ARRAY_UID_MAX_LEN  64

#define TYPE_BUILD_HASH_LEN     65


typedef char type_build_hash_t[TYPE_BUILD_HASH_LEN];


struct type_array_pid
{
    pid_t arr[TYPE_ARRAY_PID_MAX_LEN];
    size_t len;
};

struct type_array_uid
{
    uid_t arr[TYPE_ARRAY_UID_MAX_LEN];
    size_t len;
};

/*
    When a pid or uid is being monitored, we need to know whether
    it needs to be ignored or captured.

    This enum contains the possible options.
*/
enum type_monitor_mode
{
    TMM_CAPTURE = 0,
    TMM_IGNORE = 1
};

/*
    An enum to describe which syscalls to trace based on their
    result.
*/
enum type_monitor_syscalls
{
    TMS_ALL = -1,
    TMS_ONLY_FAILED = 0,
    TMS_ONLY_SUCCESSFUL = 1,
};

enum type_monitor_connections
{
    // All connections.
    TMC_ALL = -1,

    // Only new connections.
    TMC_ONLY_NEW = 0
};

struct type_monitor_user
{
    /*
        UID monitor mode for the list of uids.
    */
    enum type_monitor_mode m_mode;
    struct type_array_uid uids;
};

struct type_monitor_pid
{
    enum type_monitor_mode m_mode;
    struct type_array_pid pids;
};

struct type_monitor_ppid
{
    enum type_monitor_mode m_mode;
    struct type_array_pid ppids;
};

#endif // _SPADE_AUDIT_TYPE_H