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

#ifndef _SPADE_AUDIT_ARG_H
#define _SPADE_AUDIT_ARG_H

#include <linux/init.h>
#include <linux/kernel.h>

#include "spade/audit/type/type.h"


struct arg_netfilter
{
    /*
        Flag to audit hooks.
    */
    bool audit_hooks;

    /*
        Filter outgoing packets on netfilter hooks based on existing user criteria in arguments.
    */
    bool use_user;

    /*
        Monitor connections based on their type.
    */
    enum type_monitor_connections monitor_ct;
};

struct arg_harden
{
    /*
        List of thread group ids to prevent from being killed except by the authorized user.
    */
    struct type_array_pid tgids;

    /*
        The user(s) authorized to perform hardened activities like killing harden processes.
    */
    struct type_array_uid authorized_uids;
};

struct arg
{
    /*
        Config file path
    */
    char config_file[PATH_MAX];

    /*
        Monitor networking I/O calls like sendmsg, recvmsg, etc.
    */
    bool network_io;

    /*
        Flag to include Namespace info in logs.
    */
    bool include_ns_info;

    /*
        Function call results to monitor.
    */
    enum type_monitor_function_result monitor_function_result;

    /*
        PID monitoring related args.
    */
    struct type_monitor_pid monitor_pid;

    /*
        PPID monitoring related args.
    */
    struct type_monitor_ppid monitor_ppid;

    /*
        User monitoring related args.
    */
    struct type_monitor_user monitor_user;

    /*
        Netfilter functionality arguments.
    */
    struct arg_netfilter nf;

    struct arg_harden harden;

};

#endif // _SPADE_AUDIT_ARG_H