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

#ifndef _SPADE_AUDIT_GLOBAL_SYSCALL_H
#define _SPADE_AUDIT_GLOBAL_SYSCALL_H

#include <linux/types.h>

#include "spade/audit/context/syscall/syscall.h"


/*
    Is the syscall loggable based on the given information.

    Params:
        dst             : The result pointer.
        ctx             : Syscall context.
        sys_num         : The syscall number.

    Returns:
        0    -> dst is successfully set.
        -ive -> Error code and dst cannot be used.
*/
int global_syscall_is_loggable_by_sys_num(
    bool *dst, struct context_syscall *ctx, int sys_num
);

/*
    Is the syscall loggable based on the given information.

    Params:
        dst             : The result pointer.
        ctx             : Syscall context.
        sys_success     : Success of the syscall.

    Returns:
        0    -> dst is successfully set.
        -ive -> Error code and dst cannot be used.
*/
int global_syscall_is_loggable_by_sys_success(
    bool *dst, struct context_syscall *ctx, bool sys_success
);

/*
    Is the syscall loggable based on the given information.

    Params:
        dst             : The result pointer.
        ctx             : Syscall context.
        uid             : Uid of the process which generated the event.

    Returns:
        0    -> dst is successfully set.
        -ive -> Error code and dst cannot be used.
*/
int global_syscall_is_loggable_by_uid(
    bool *dst, struct context_syscall *ctx, uid_t uid
);

/*
    Is the syscall loggable based on the given information.

    Params:
        dst             : The result pointer.
        ctx             : Syscall context.
        pid             : Pid of the process which generated the event.

    Returns:
        0    -> dst is successfully set.
        -ive -> Error code and dst cannot be used.
*/
int global_syscall_is_loggable_by_pid(
    bool *dst, struct context_syscall *ctx, pid_t pid
);

/*
    Is the syscall loggable based on the given information.

    Params:
        dst             : The result pointer.
        ctx             : Syscall context.
        ppid            : Ppid of the process which generated the event.

    Returns:
        0    -> dst is successfully set.
        -ive -> Error code and dst cannot be used.
*/
int global_syscall_is_loggable_by_ppid(
    bool *dst, struct context_syscall *ctx, pid_t ppid
);

/*
    Is the syscall loggable based on the given information.

    Params:
        dst             : The result pointer.
        ctx             : Syscall context.
        sys_num         : The syscall number.
        sys_success     : Success of the syscall.
        pid             : Pid of the process which generated the event.
        ppid            : Ppid of the process which generated the event.
        uid             : Uid of the process which generated the event.

    Returns:
        0    -> dst is successfully set.
        -ive -> Error code and dst cannot be used.
*/
int global_syscall_is_loggable(
    bool *dst,
    struct context_syscall *ctx,
    int sys_num, bool sys_success,
    pid_t pid, pid_t ppid, uid_t uid
);

#endif // _SPADE_AUDIT_GLOBAL_SYSCALL_H