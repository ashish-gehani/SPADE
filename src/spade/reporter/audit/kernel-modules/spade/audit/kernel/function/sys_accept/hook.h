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

#ifndef SPADE_AUDIT_KERNEL_FUNCTION_SYS_ACCEPT_HOOK_H
#define SPADE_AUDIT_KERNEL_FUNCTION_SYS_ACCEPT_HOOK_H

#include <linux/types.h>
#include "spade/audit/kernel/function/hook.h"

/*
    Hook struct for accept syscall.
*/
extern const struct kernel_function_hook KERNEL_FUNCTION_SYS_ACCEPT_HOOK;

/*
    Validate sys_accept pre-execution context.

    Params:
        ctx     : Hook context pre-execution.

    Returns:
        true    -> Valid context.
        false   -> Invalid context.
*/
bool kernel_function_sys_accept_hook_context_pre_is_valid(
    const struct kernel_function_hook_context_pre *ctx
);

/*
    Validate sys_accept post-execution context.

    Params:
        ctx     : Hook context post-execution.

    Returns:
        true    -> Valid context.
        false   -> Invalid context.
*/
bool kernel_function_sys_accept_hook_context_post_is_valid(
    const struct kernel_function_hook_context_post *ctx
);

#endif // SPADE_AUDIT_KERNEL_FUNCTION_SYS_ACCEPT_HOOK_H