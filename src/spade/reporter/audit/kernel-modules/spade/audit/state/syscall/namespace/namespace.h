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

#ifndef SPADE_AUDIT_STATE_SYSCALL_NAMESPACE_H
#define SPADE_AUDIT_STATE_SYSCALL_NAMESPACE_H

#include <linux/atomic.h>
#include <linux/init.h>
#include <linux/proc_ns.h>


struct state_syscall_namespace
{
    bool initialized;

    struct proc_ns_operations* ops_mnt;
    struct proc_ns_operations* ops_net;
    struct proc_ns_operations* ops_pid;
    struct proc_ns_operations* ops_user;
    struct proc_ns_operations* ops_ipc;
    struct proc_ns_operations* ops_cgroup;
};

/*
    Check if the namespace pointers are initialized.

    Params:
        dst     : Contains the result if successful.
        s       : The state to check for initialization.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int state_syscall_namespace_is_initialized(
    bool *dst,
    struct state_syscall_namespace *s
);

/*
    Initialize.

    Params:
        s       : The state to initialize.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int state_syscall_namespace_init(
    struct state_syscall_namespace *s
);

/*
    De-initialize.

    Params:
        s       : The state to deinitialize.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int state_syscall_namespace_deinit(
    struct state_syscall_namespace *s
);

#endif // SPADE_AUDIT_STATE_SYSCALL_NAMESPACE_H