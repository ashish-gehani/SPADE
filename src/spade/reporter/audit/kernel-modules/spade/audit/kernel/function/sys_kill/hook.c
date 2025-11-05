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

#include <linux/errno.h>
#include <linux/types.h>
#include <asm/syscall.h>

#include "spade/audit/kernel/helper/kernel.h"
#include "spade/audit/kernel/function/arg.h"
#include "spade/audit/kernel/function/action.h"
#include "spade/audit/kernel/function/hook.h"
#include "spade/audit/kernel/function/result.h"
#include "spade/audit/kernel/function/sys_kill/arg.h"
#include "spade/audit/kernel/function/sys_kill/hook.h"
#include "spade/audit/kernel/function/sys_kill/result.h"
#include "spade/audit/kernel/function/sys_kill/ubsi.h"
#include "spade/util/log/log.h"


static const enum kernel_function_number global_func_num = KERN_F_NUM_SYS_KILL;


#define BUILD_HOOK_CONTEXT(_pid, _sig) \
    { \
        .func_num = global_func_num, \
        .func_arg = &(const struct kernel_function_arg){ \
            .arg = &(const struct kernel_function_sys_kill_arg){ \
                .pid = (_pid), \
                .sig = (_sig) \
            }, \
            .arg_size = sizeof(struct kernel_function_sys_kill_arg) \
        }, \
        .act_res = &(struct kernel_function_action_result){0} \
    }
// todo. make non const everywhere.        .act_res = &(struct kernel_function_action_result){0}

#define BUILD_HOOK_CONTEXT_PRE(_h_ctx) \
    { \
        .header = (_h_ctx), \
        .proc = KERNEL_FUNCTION_HOOK_PROCESS_CONTEXT_CURRENT \
    }

#define BUILD_HOOK_CONTEXT_POST(_h_ctx, _sys_res, _pid) \
    { \
        .header = (_h_ctx), \
        .proc = KERNEL_FUNCTION_HOOK_PROCESS_CONTEXT_CURRENT, \
        .func_res = &(const struct kernel_function_result){ \
            .res = &(const struct kernel_function_sys_kill_result){ \
                .ret = (_sys_res) \
            }, \
            .res_size = sizeof(struct kernel_function_sys_kill_result), \
            .success = _get_sys_success((_sys_res), (_pid)) \
        } \
    }

static bool _get_sys_success(long sys_res, pid_t pid)
{
    switch (pid)
    {
        case UBSI_UENTRY:
        case UBSI_UENTRY_ID:
        case UBSI_UEXIT:
        case UBSI_MREAD1:
        case UBSI_MREAD2:
        case UBSI_MWRITE1:
        case UBSI_MWRITE2:
        case UBSI_UDEP:
            return true;
        default:
            return sys_res == 0;
    }
}

bool kernel_function_sys_kill_hook_context_pre_is_valid(const struct kernel_function_hook_context_pre *ctx)
{
    return (
        kernel_function_hook_context_pre_is_valid(ctx)
        && ctx->header->func_num == KERN_F_NUM_SYS_KILL
        && ctx->header->func_arg->arg_size == sizeof(struct kernel_function_sys_kill_arg)
    );
}

bool kernel_function_sys_kill_hook_context_post_is_valid(const struct kernel_function_hook_context_post *ctx)
{
    return (
        kernel_function_hook_context_post_is_valid(ctx)
        && ctx->header->func_num == KERN_F_NUM_SYS_KILL
        && ctx->header->func_arg->arg_size == sizeof(struct kernel_function_sys_kill_arg)
        && ctx->func_res->res_size == sizeof(struct kernel_function_sys_kill_result)
        && ctx->func_res->success
    );
}

static void _pre(const struct kernel_function_hook_context *h_ctx)
{
    int err;

    const struct kernel_function_hook_context_pre hook_ctx_pre = 
        BUILD_HOOK_CONTEXT_PRE(h_ctx);

    err = kernel_function_hook_pre(&hook_ctx_pre);
    if (err != 0)
        return;

    return;
}

static void _post(const struct kernel_function_hook_context *h_ctx, long sys_res, pid_t pid)
{
    int err;

    const struct kernel_function_hook_context_post hook_ctx_post = 
        BUILD_HOOK_CONTEXT_POST(h_ctx, sys_res, pid);

    err = kernel_function_hook_post(&hook_ctx_post);
    if (err != 0)
        return;

    return;
}


#if KERNEL_HELPER_KERNEL_PTREGS_SYSCALL_STUBS

	static asmlinkage long (*_orig)(const struct pt_regs *regs);
    static asmlinkage long _hook(const struct pt_regs *regs);

	static asmlinkage long _hook(const struct pt_regs *regs)
    {
        const char *log_id = "sys_kill::_hook";
		long res;
        pid_t pid = (pid_t)(regs->di);
        int sig = (int)(regs->si);

        const struct kernel_function_hook_context h_ctx = BUILD_HOOK_CONTEXT(pid, sig);

        _pre(&h_ctx);
        if (kernel_function_action_result_is_disallow_function(h_ctx.act_res->type))
        {
            util_log_debug(log_id, "Disallowing function execution due to action result type: %d", h_ctx.act_res->type);
            res = -EACCES;
        } else
        {
            res = _orig(regs);
        }
		_post(&h_ctx, res, pid);
		return res;
	}

#else

	static asmlinkage long (*_orig)(pid_t pid, int sig);
    static asmlinkage long _hook(pid_t pid, int sig);

    static asmlinkage long _hook(pid_t pid, int sig)
    {
        const char *log_id = "sys_kill::_hook";
		long res;

        const struct kernel_function_hook_context h_ctx = BUILD_HOOK_CONTEXT(pid, sig);

        _pre(&h_ctx);
        if (kernel_function_action_result_is_disallow_function(h_ctx.act_res->type))
        {
            util_log_debug(log_id, "Disallowing function execution due to action result type: %d", h_ctx.act_res->type);
            res = -EACCES;
        } else
        {
            res = _orig(pid, sig);
        }
        _post(&h_ctx, res, pid);
		return res;
	}

#endif


static enum kernel_function_number kernel_function_hook_function_kill_num(void)
{
    return global_func_num;
}

static const char* kernel_function_hook_function_kill_name(void)
{
#if KERNEL_HELPER_KERNEL_PTREGS_SYSCALL_STUBS
    return "__x64_sys_kill";
#else
    return "sys_kill";
#endif
}

static void *kernel_function_hook_function_kill_original_ptr(void)
{
    return &_orig;
}

static void *kernel_function_hook_function_kill_hook(void)
{
    return _hook;
}

const struct kernel_function_hook KERNEL_FUNCTION_SYS_KILL_HOOK = {
    .get_num = kernel_function_hook_function_kill_num,
    .get_name = kernel_function_hook_function_kill_name,
    .get_orig_func_ptr = kernel_function_hook_function_kill_original_ptr,
    .get_hook_func = kernel_function_hook_function_kill_hook
};
