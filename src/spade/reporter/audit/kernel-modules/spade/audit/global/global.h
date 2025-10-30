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

#ifndef _SPADE_AUDIT_GLOBAL_H
#define _SPADE_AUDIT_GLOBAL_H

#include <linux/netfilter.h>

#include "spade/audit/arg/arg.h"
#include "spade/audit/state/state.h"
#include "spade/audit/context/context.h"
#include "spade/audit/kernel/function/number.h"


/*

    Sequence of actions:

        1.      Init
        ...
        N.      Perform any ops like auditing start/stop, etc.
        N+1.    Deinit

*/

/*
    Init.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int global_init(bool dry_run);

/*
    Deinit.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int global_deinit(void);

/*
    Is initialized?

    Returns:
        true/false.
*/
bool global_is_initialized(void);

/*
    Start auditing.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int global_auditing_start(const struct arg *arg);

/*
    Stop auditing.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int global_auditing_stop(void);

/*
    Is auditing started?

    Returns:
        true/false.
*/
bool global_is_auditing_started(void);

/*
    Check if netfilter event is loggable by uid.
*/
bool global_is_netfilter_loggable_by_user(uid_t uid);

/*
    Check if netfilter event is loggable by conntrack info.
*/
bool global_is_netfilter_loggable_by_conntrack_info(
    enum ip_conntrack_info ct_info
);

/*
    Check if namespace info is to be included in netfilter events.
*/
bool global_is_netfilter_logging_ns_info(void);

/*
    Check if netfilter hooks are being audited.
*/
bool global_is_netfilter_audit_hooks_on(void);

/*
    Check if namespace info is to be included in network events.
*/
bool global_is_network_logging_ns_info(void);

/*
    Check if syscall event is loggable.
*/
bool global_is_syscall_loggable(
    enum kernel_function_number func_num, bool sys_success,
    pid_t pid, pid_t ppid, uid_t uid
);

/*
    Check if syscall event is loggable.
*/
bool global_is_syscall_loggable_by_sys_num(enum kernel_function_number func_num);

/*
    Check if syscall event is loggable.
*/
bool global_is_syscall_loggable_by_sys_success(bool sys_success);

/*
    Check if syscall event is loggable.
*/
bool global_is_syscall_loggable_by_pid(pid_t pid);

/*
    Check if syscall event is loggable.
*/
bool global_is_syscall_loggable_by_ppid(pid_t ppid);

/*
    Check if syscall event is loggable.
*/
bool global_is_syscall_loggable_by_uid(uid_t uid);


#endif // _SPADE_AUDIT_GLOBAL_H