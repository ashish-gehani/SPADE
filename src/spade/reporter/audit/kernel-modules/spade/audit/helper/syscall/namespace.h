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

#ifndef _SPADE_AUDIT_HELPER_SYSCALL_NAMESPACE_H
#define _SPADE_AUDIT_HELPER_SYSCALL_NAMESPACE_H

#include <linux/errno.h>

#include "spade/audit/state/state.h"
#include "spade/audit/msg/namespace/create.h"
#include "spade/audit/kernel/function/context/post.h"


/*
    Populate msg using the context provided.

    Params:
        msg             : Message to populate.
        s               : The syscall state.
        op              : The operation assigned to the syscall.

    Returns:
        0       -> Success.
        -1      -> Error code.
*/
int helper_syscall_namespace_populate_msg(
    struct msg_namespace *msg,
    struct kernel_function_context_post *s,
    enum msg_namespace_operation op
);

/*
    Log the namespaces msg to Linux Audit Subsystem.

    Params:
        msg : The msg to log.

    Returns:
        0       -> Success.
        -ive    -> Error code.

*/
int helper_syscall_namespace_log_msg_to_audit(
    struct msg_namespace *msg
);

#endif // _SPADE_AUDIT_HELPER_SYSCALL_NAMESPACE_H