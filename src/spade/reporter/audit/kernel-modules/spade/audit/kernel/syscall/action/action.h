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

#ifndef SPADE_AUDIT_KERNEL_SYSCALL_ACTION_H
#define SPADE_AUDIT_KERNEL_SYSCALL_ACTION_H

#include <linux/types.h>

#include "spade/audit/kernel/syscall/context/context.h"


enum kernel_syscall_action_type
{
    ACTION_TYPE_AUDIT
};

enum kernel_syscall_action_result_type
{
    ACTION_RESULT_TYPE_SUCCESS,
    ACTION_RESULT_TYPE_FAILURE
};

struct kernel_syscall_action_result
{
    enum kernel_syscall_action_result_type type;
    int result;
};

struct kernel_syscall_action
{
    enum kernel_syscall_action_type type;
    struct kernel_syscall_action_result result;
};

/*
    Handle action with given arguments.

    Returns:
        0       -> Success.
        -ive    -> Error code in action handling.
*/
int kernel_syscall_action_handle(
    struct kernel_syscall_action *a,
    struct kernel_syscall_context *sys_ctx
);

#endif // SPADE_AUDIT_KERNEL_SYSCALL_ACTION_H