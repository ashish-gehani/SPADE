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

#include "spade/arg/arg.h"
#include "spade/audit/state/state.h"
#include "spade/audit/context/context.h"


/*

    Sequence of actions:

        1.      Init state
        2.      Init context
        ...
        N.      Perform any ops like auditing start/stop, etc.
        N+1.    Deinit context
        N+2.    Deinit state

*/


/*
    Initialize global state.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int global_state_init(void);

/*
    Deinitialize global state.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int global_state_deinit(void);

/*
    Is global state initialized?

    Returns:
        true/false.
*/
bool global_is_state_initialized(void);

/*
    Initialize global context from arguments.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int global_context_init(struct arg *arg);

/*
    Deinitialize global context.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int global_context_deinit(void);

/*
    Is global context initialized?

    Returns:
        true/false.
*/
bool global_is_context_initialized(void);

/*
    Start auditing.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int global_auditing_start(void);

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

//

bool global_is_netfilter_loggable_by_user(uid_t uid);
bool global_is_netfilter_loggable_by_conntrack_info(
    enum ip_conntrack_info ct_info
);
bool global_is_netfilter_logging_ns_info(void);

bool global_is_syscall_loggable(
    int sys_num, bool sys_success,
    pid_t pid, pid_t ppid, uid_t uid
);

struct state_syscall_namespace* global_get_ref_to_syscall_ns_state(void);

#endif // _SPADE_AUDIT_GLOBAL_H