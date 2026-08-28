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

#include "audit/kernel/arch/common/function/sys_accept4/hook.h"
#include "audit/kernel/arch/common/function/sys_accept4/arg.h"
#include "audit/kernel/arch/common/function/sys_accept4/result.h"


static const enum kernel_function_number global_func_num = KERN_F_NUM_SYS_ACCEPT4;

#define BUILD_HOOK_CONTEXT_PRE(_h_ctx) \
    ((struct kernel_function_hook_context_pre){ \
        .header = (_h_ctx), \
        .proc = KERNEL_FUNCTION_HOOK_PROCESS_CONTEXT_CURRENT \
    })

#define BUILD_HOOK_CONTEXT_POST(_h_ctx, _sys_res) \
    ((struct kernel_function_hook_context_post){ \
        .header = (_h_ctx), \
        .proc = KERNEL_FUNCTION_HOOK_PROCESS_CONTEXT_CURRENT, \
        .func_res = &(const struct kernel_function_result){ \
            .res = &(const struct kernel_function_sys_accept4_result){ \
                .ret = (_sys_res) \
            }, \
            .res_size = sizeof(struct kernel_function_sys_accept4_result), \
            .success = ((_sys_res) >= 0) \
        } \
    })


enum kernel_function_number kernel_arch_common_function_hook_function_accept4_num(void)
{
    return global_func_num;
}

void kernel_arch_common_function_sys_accept4_hook_pre(const struct kernel_function_hook_context *h_ctx)
{
    int err;

    const struct kernel_function_hook_context_pre hook_ctx_pre = BUILD_HOOK_CONTEXT_PRE(h_ctx);

    err = kernel_arch_common_function_hook_pre(&hook_ctx_pre);
    if (err != 0)
        return;

    return;
}

void kernel_arch_common_function_sys_accept4_hook_post(const struct kernel_function_hook_context *h_ctx, long sys_res)
{
    int err;

    const struct kernel_function_hook_context_post hook_ctx_post = BUILD_HOOK_CONTEXT_POST(h_ctx, sys_res);

    err = kernel_arch_common_function_hook_post(&hook_ctx_post);
    if (err != 0)
        return;

    return;
}


/* Arch-specific implementations (e.g. audit/kernel/arch/x86_64/function/sys_accept4/hook.c) provide
 * strong definitions of these that override the ones below at link time. These weak no-op
 * definitions exist so archs without one yet still link successfully. */

const struct kernel_function_hook* __weak kernel_arch_common_overridable_function_sys_accept4_hook_get(void)
{
    return NULL;
}

bool kernel_arch_common_function_sys_accept4_hook_context_pre_is_valid(const struct kernel_function_hook_context_pre *ctx)
{
    return (
        kernel_arch_common_function_hook_context_pre_is_valid(ctx)
        && ctx->header->func_num == global_func_num
        && ctx->header->func_arg->arg_size == sizeof(struct kernel_function_sys_accept4_arg)
    );
}

bool kernel_arch_common_function_sys_accept4_hook_context_post_is_valid(const struct kernel_function_hook_context_post *ctx)
{
    return (
        kernel_arch_common_function_hook_context_post_is_valid(ctx)
        && ctx->header->func_num == global_func_num
        && ctx->header->func_arg->arg_size == sizeof(struct kernel_function_sys_accept4_arg)
        && ctx->func_res->res_size == sizeof(struct kernel_function_sys_accept4_result)
        && ctx->func_res->success // todo
    );
}
