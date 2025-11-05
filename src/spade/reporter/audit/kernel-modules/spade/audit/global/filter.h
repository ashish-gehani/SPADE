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

#ifndef _SPADE_AUDIT_GLOBAL_FILTER_H
#define _SPADE_AUDIT_GLOBAL_FILTER_H

#include <linux/types.h>
#include <linux/netfilter.h>

#include "spade/audit/kernel/function/number.h"


/*
    Check if netfilter event is actionable by uid.
*/
bool global_filter_netfilter_user_is_actionable(uid_t uid);

/*
    Check if netfilter event is actionable by conntrack info.
*/
bool global_filter_netfilter_conntrack_info_is_actionable(
    enum ip_conntrack_info ct_info
);

/*
    Check if namespace info is to be included in netfilter events.
*/
bool global_filter_netfilter_include_ns_info(void);

/*
    Check if netfilter hooks are being audited.
*/
bool global_filter_netfilter_audit_hooks_on(void);

/*
    Check if namespace info is to be included in network events.
*/
bool global_filter_function_network_include_ns_info(void);

/*
    Check if function post-execution is actionable.
*/
bool global_filter_function_post_execution_is_actionable(
    enum kernel_function_number func_num, bool func_success,
    pid_t pid, pid_t ppid, uid_t uid
);

/*
    Check if function pre-execution is actionable.
*/
bool global_filter_function_pre_execution_is_actionable(
    enum kernel_function_number func_num,
    pid_t pid, pid_t ppid, uid_t uid
);

/*
    Check if function number is actionable.
*/
bool global_filter_function_number_is_actionable(
    enum kernel_function_number func_num
);

/*
    Check if function success is actionable.
*/
bool global_filter_function_success_is_actionable(
    bool func_success
);

/*
    Check if function is actionable by pid.
*/
bool global_filter_function_pid_is_actionable(pid_t pid);

/*
    Check if function is actionable by ppid.
*/
bool global_filter_function_ppid_is_actionable(pid_t ppid);

/*
    Check if function is actionable by uid.
*/
bool global_filter_function_uid_is_actionable(uid_t uid);

/*
    Check if the pid is hardened.

    Params:
        pid     : Process ID.

    Returns:
        true    -> Process is hardened.
        false   -> Process is not hardened.
*/
bool global_filter_function_pid_is_hardened(pid_t pid);

/*
    Check if the uid is authorized.

    Params:
        uid     : User ID.

    Returns:
        true    -> User is hardened.
        false   -> User is not hardened.
*/
bool global_filter_function_uid_is_authorized(uid_t uid);


#endif // _SPADE_AUDIT_GLOBAL_FILTER_H