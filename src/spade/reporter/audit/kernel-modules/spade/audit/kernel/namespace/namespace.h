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

#ifndef SPADE_AUDIT_KERNEL_NAMESPACE_NAMESPACE_H
#define SPADE_AUDIT_KERNEL_NAMESPACE_NAMESPACE_H

#include "spade/audit/msg/namespace/namespace.h"

struct kernel_namespace_pointers
{
    struct proc_ns_operations* ops_mnt;
    struct proc_ns_operations* ops_net;
    struct proc_ns_operations* ops_pid;
    struct proc_ns_operations* ops_user;
    struct proc_ns_operations* ops_ipc;
    struct proc_ns_operations* ops_cgroup;
};

/*
    Set the pointers needed from kernel.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int kernel_namespace_set(struct kernel_namespace_pointers *k);

/*
    Unset the pointers.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int kernel_namespace_unset(void);

/*
    Get the kernel_namespace_pointers inited during setup.

    Returns:
        ptr     -> Success.
        NULL    -> Error.
*/
struct kernel_namespace_pointers* kernel_namespace_get_pointers(void);

#endif // SPADE_AUDIT_KERNEL_NAMESPACE_NAMESPACE_H