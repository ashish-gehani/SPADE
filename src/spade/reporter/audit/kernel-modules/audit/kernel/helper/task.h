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

#ifndef _SPADE_AUDIT_KERNEL_HELPER_TASK_H
#define _SPADE_AUDIT_KERNEL_HELPER_TASK_H

#include <linux/sched.h>
#include <linux/errno.h>
#include <linux/types.h>

#include "audit/msg/common/common.h"


/*
    Get UID of the current task from the host namespace view.

    Returns:
        uid -> Always
*/
uid_t kernel_helper_task_host_view_current_uid(void);

/*
    Get EUID of the current task from the host namespace view.

    Returns:
        euid -> Always
*/
uid_t kernel_helper_task_host_view_current_euid(void);

/*
    Get SUID of the current task from the host namespace view.

    Returns:
        suid -> Always
*/
uid_t kernel_helper_task_host_view_current_suid(void);

/*
    Get FSUID of the current task from the host namespace view.

    Returns:
        fsuid -> Always
*/
uid_t kernel_helper_task_host_view_current_fsuid(void);

/*
    Get GID of the current task from the host namespace view.

    Returns:
        gid -> Always
*/
gid_t kernel_helper_task_host_view_current_gid(void);

/*
    Get EGID of the current task from the host namespace view.

    Returns:
        egid -> Always
*/
gid_t kernel_helper_task_host_view_current_egid(void);

/*
    Get SGID of the current task from the host namespace view.

    Returns:
        sgid -> Always
*/
gid_t kernel_helper_task_host_view_current_sgid(void);

/*
    Get FSGID of the current task from the host namespace view.

    Returns:
        fsgid -> Always
*/
gid_t kernel_helper_task_host_view_current_fsgid(void);

/*
    Get TGID of the given pid from the task's namespace view.

    Returns:
        >=0        -> Success.
        -ive        -> Error code.
*/
pid_t kernel_helper_task_task_view_get_tgid(pid_t pid);

/*
    Get PPID of the current task from the current task namespace view.

    Returns:
        ppid -> Always
*/
pid_t kernel_helper_task_task_view_current_ppid(void);

/*
    Get PID of the current task from the current task namespace view.

    Returns:
        pid -> Always
*/
pid_t kernel_helper_task_task_view_current_pid(void);

/*
    Get audit_context of the current task.

    Returns:
        struct audit_context -> Success.
        NULL                 -> Otherwise.
*/
struct audit_context* kernel_helper_task_current_audit_context(void);

/*
    Populate struct msg_common_process with the current process information
    to be reported as required by the Linux Subsystem.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int kernel_helper_task_populate_process_info_from_current_task(
    struct msg_common_process *proc
);

#endif // _SPADE_AUDIT_KERNEL_HELPER_TASK_H
