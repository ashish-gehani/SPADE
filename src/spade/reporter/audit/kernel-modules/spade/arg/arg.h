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

#ifndef _SPADE_ARG_H
#define _SPADE_ARG_H

#include <linux/init.h>
#include <linux/kernel.h>

/*
    Maximum number of items in an argument array.
*/
#define ARG_ARRAY_MAX 64


struct arg_array_pid
{
    pid_t arr[ARG_ARRAY_MAX];
    size_t len;
};

struct arg_array_uid
{
    uid_t arr[ARG_ARRAY_MAX];
    size_t len;
};

/*
    When a pid or uid is being monitored, we need to know whether
    it needs to be ignored or captured.

    This enum contains the possible options.
*/
enum arg_monitor_mode
{
    AMM_CAPTURE = 0,
    AMM_IGNORE = 1
};

/*
    An enum to describe which syscalls to trace based on their
    result.
*/
enum arg_monitor_syscalls
{
    AMMS_ALL = -1,
    AMMS_ONLY_FAILED = 0,
    AMMS_ONLY_SUCCESSFUL = 1,
};

enum arg_monitor_connections
{
    // All connections.
    AMMC_ALL = -1,

    // Only new connections.
    AMMC_ONLY_NEW = 0
};

struct arg_user
{
    /*
        UID monitor mode for the list of uids.
    */
    enum arg_monitor_mode uid_monitor_mode;
    struct arg_array_uid uids;
};

struct arg_netfilter
{
    /*
        Flag to enable/disable netfilter hooks.
    */
    bool hooks;

    /*
        Filter outgoing packets on netfilter hooks based on existing user criteria in arguments.
    */
    bool use_user;

    /*
        Monitor connections based on their type.
    */
    enum arg_monitor_connections monitor_ct;
};

struct arg
{
    /*
        Monitor networking I/O calls like sendmsg, recvmsg, etc.
    */
    bool network_io;

    /*
        Flag to include Namespace info in logs.
    */
    bool include_ns_info;

    /*
        System calls to monitor.
    */
    enum arg_monitor_syscalls monitor_syscalls;

    /*
        List of process ids to ignore.
    */
    struct arg_array_pid ignore_pids;

    /*
        List of parent process ids to ignore.
    */
    struct arg_array_pid ignore_ppids;

    /*
        User related args.
    */
    struct arg_user user;

    /*
        Netfilter functionality arguments.
    */
    struct arg_netfilter nf;
};

#endif // _SPADE_ARG_H