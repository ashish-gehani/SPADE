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

#ifndef _SPADE_AUDIT_CONTEXT_SYSCALL_H
#define _SPADE_AUDIT_CONTEXT_SYSCALL_H

#include <linux/init.h>

#include "spade/audit/arg/arg.h"


struct context_syscall
{
    bool initialized;

    bool network_io;

    bool include_ns_info;

    enum type_monitor_syscalls monitor_syscalls;

    struct type_monitor_pid m_pids;

    struct type_monitor_ppid m_ppids;

    struct type_monitor_user m_uids;
};

/*
    Check if the context is initialized along with nested context.

    Params:
        dst     : Contains the result if successful.
        s       : The context to check for initialization.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int context_syscall_is_initialized(
    bool *dst,
    struct context_syscall *c
);

/*
    Initialize context with the given arg and nested context.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int context_syscall_init(struct context_syscall *c, struct arg *arg);

/*
    Deinitialize context.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int context_syscall_deinit(struct context_syscall *c);

#endif // _SPADE_AUDIT_CONTEXT_SYSCALL_H