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
#include "spade/audit/kernel/function/sys_fork/arg.h"
#include "spade/audit/kernel/function/sys_fork/hook.h"
#include "spade/audit/kernel/function/sys_fork/result.h"


static const enum kernel_function_number global_func_num = KERN_F_NUM_SYS_FORK;


static void _pre(void)
{
    int err;

    struct kernel_function_hook_context_pre hook_ctx_pre = {
        .header = &(const struct kernel_function_hook_context){
            .type = KERNEL_FUNCTION_HOOK_CONTEXT_TYPE_PRE,
            .proc = KERNEL_FUNCTION_HOOK_PROCESS_CONTEXT_CURRENT,
            .func_num = global_func_num,
            .func_arg = &(const struct kernel_function_arg){
                .arg = &(const struct kernel_function_sys_fork_arg){},
                .arg_size = sizeof(struct kernel_function_sys_fork_arg)
            },
            .act_res = &(struct kernel_function_action_result){0}
        }
    };

    err = kernel_function_hook_pre(&hook_ctx_pre);
    if (err != 0)
        return;

    // todo
    // switch (hook_ctx_pre.header->act_res->type)
    // {
    //     case KERNEL_FUNCTION_ACTION_RESULT_TYPE_SUCCESS: break;
    //     case KERNEL_FUNCTION_ACTION_RESULT_TYPE_FAILURE: break;
    //     default: break;
    // }

    return;
}

static void _post(long sys_res)
{
    int err;

    struct kernel_function_hook_context_post hook_ctx_post = {
        .header = &(const struct kernel_function_hook_context){
            .type = KERNEL_FUNCTION_HOOK_CONTEXT_TYPE_POST,
            .proc = KERNEL_FUNCTION_HOOK_PROCESS_CONTEXT_CURRENT,
            .func_num = global_func_num,
            .func_arg = &(const struct kernel_function_arg){
                .arg = &(const struct kernel_function_sys_fork_arg){},
                .arg_size = sizeof(struct kernel_function_sys_fork_arg)
            },
            .act_res = &(struct kernel_function_action_result){0}
        },
        .func_res = &(const struct kernel_function_result){
            .res = &(const struct kernel_function_sys_fork_result){
                .ret = sys_res
            },
            .res_size = sizeof(struct kernel_function_sys_fork_result),
            .success = (sys_res != -1)
        }
    };

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
		long res;

        _pre();
		res = _orig(regs);
		_post(res);
		return res;
	}

#else

	static asmlinkage long (*_orig)(void);
    static asmlinkage long _hook(void);

    static asmlinkage long _hook(void)
    {
		long res;
        _pre();
		res = _orig();
        _post(res);
		return res;
	}

#endif


static enum kernel_function_number kernel_function_hook_function_fork_num(void)
{
    return global_func_num;
}

static const char* kernel_function_hook_function_fork_name(void)
{
#if KERNEL_HELPER_KERNEL_PTREGS_SYSCALL_STUBS
    return "__x64_sys_fork";
#else
    return "sys_fork";
#endif
}

static void *kernel_function_hook_function_fork_original_ptr(void)
{
    return &_orig;
}

static void *kernel_function_hook_function_fork_hook(void)
{
    return _hook;
}

const struct kernel_function_hook KERNEL_FUNCTION_SYS_FORK_HOOK = {
    .get_num = kernel_function_hook_function_fork_num,
    .get_name = kernel_function_hook_function_fork_name,
    .get_orig_func_ptr = kernel_function_hook_function_fork_original_ptr,
    .get_hook_func = kernel_function_hook_function_fork_hook
};
