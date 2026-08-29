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

#ifndef SPADE_AUDIT_KERNEL_ARCH_COMMON_FUNCTION_SYS_SETNS_HOOK_H
#define SPADE_AUDIT_KERNEL_ARCH_COMMON_FUNCTION_SYS_SETNS_HOOK_H

#include <linux/types.h>
#include "audit/kernel/arch/common/function/hook.h"
#include "audit/kernel/arch/common/function/sys_setns/arg.h"


#define KERNEL_FUNCTION_SYS_SETNS_BUILD_HOOK_CONTEXT(_fd, _nstype) \
    ((const struct kernel_function_hook_context){ \
        .func_num = kernel_arch_common_function_hook_function_setns_num(), \
        .func_arg = &(const struct kernel_function_arg){ \
            .arg = &(const struct kernel_function_sys_setns_arg){ \
                .fd = (_fd), \
                .nstype = (_nstype) \
            }, \
            .arg_size = sizeof(struct kernel_function_sys_setns_arg) \
        }, \
        .act_res = &(struct kernel_function_action_result){0} \
    })

/*
    Get the sys_setns function number.
*/
enum kernel_function_number kernel_arch_common_function_hook_function_setns_num(void);

/*
    Build the pre-execution hook context and run the pre-execution actions for it.

    Params:
        h_ctx   : Hook context.
*/
void kernel_arch_common_function_sys_setns_hook_pre(const struct kernel_function_hook_context *h_ctx);

/*
    Build the post-execution hook context and run the post-execution actions for it.

    Params:
        h_ctx   : Hook context.
        sys_res : Syscall return value.
*/
void kernel_arch_common_function_sys_setns_hook_post(const struct kernel_function_hook_context *h_ctx, long sys_res);

/*
    Get the sys_setns hook.

    Returns:
        ptr     -> Pointer to KERNEL_FUNCTION_SYS_SETNS_HOOK.
        NULL    -> Not available.
*/
const struct kernel_function_hook* kernel_arch_common_overridable_function_sys_setns_hook_get(void);

/*
    Validate sys_setns pre-execution context.

    Params:
        ctx     : Hook context pre-execution.

    Returns:
        true    -> Valid context.
        false   -> Invalid context.
*/
bool kernel_arch_common_function_sys_setns_hook_context_pre_is_valid(
    const struct kernel_function_hook_context_pre *ctx
);

/*
    Validate sys_setns post-execution context.

    Params:
        ctx     : Hook context post-execution.

    Returns:
        true    -> Valid context.
        false   -> Invalid context.
*/
bool kernel_arch_common_function_sys_setns_hook_context_post_is_valid(
    const struct kernel_function_hook_context_post *ctx
);

#endif // SPADE_AUDIT_KERNEL_ARCH_COMMON_FUNCTION_SYS_SETNS_HOOK_H
