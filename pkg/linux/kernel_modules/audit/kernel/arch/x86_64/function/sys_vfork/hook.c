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

#include "audit/kernel/arch/common/helper/kernel.h"
#include "audit/kernel/arch/common/function/arg.h"
#include "audit/kernel/arch/common/function/action.h"
#include "audit/kernel/arch/common/function/hook.h"
#include "audit/kernel/arch/common/function/sys_vfork/arg.h"
#include "audit/kernel/arch/common/function/sys_vfork/hook.h"
#include "audit/util/log/log.h"


static const enum kernel_function_number global_func_num = KERN_F_NUM_SYS_VFORK;


#define BUILD_HOOK_CONTEXT() \
    ((const struct kernel_function_hook_context){ \
        .func_num = global_func_num, \
        .func_arg = &(const struct kernel_function_arg){ \
            .arg = &(const struct kernel_function_sys_vfork_arg){}, \
            .arg_size = sizeof(struct kernel_function_sys_vfork_arg) \
        }, \
        .act_res = &(struct kernel_function_action_result){0} \
    })


#if KERNEL_HELPER_KERNEL_PTREGS_SYSCALL_STUBS

	static asmlinkage long (*_orig)(const struct pt_regs *regs);
    static asmlinkage long _hook(const struct pt_regs *regs);

	static asmlinkage long _hook(const struct pt_regs *regs)
    {
		const char *log_id = "sys_vfork::_hook";
		long res;

		const struct kernel_function_hook_context h_ctx = BUILD_HOOK_CONTEXT();

		kernel_function_sys_vfork_hook_pre(&h_ctx);
		if (kernel_function_action_result_is_disallow_function(h_ctx.act_res->type))
		{
			util_log_debug(log_id, "Disallowing function execution due to action result type: %d", h_ctx.act_res->type);
			res = -EACCES;
		} else
		{
			res = _orig(regs);
		}
		kernel_function_sys_vfork_hook_post(&h_ctx, res);
		return res;
	}

#else

	static asmlinkage long (*_orig)(void);
    static asmlinkage long _hook(void);

    static asmlinkage long _hook(void)
    {
		const char *log_id = "sys_vfork::_hook";
		long res;

		const struct kernel_function_hook_context h_ctx = BUILD_HOOK_CONTEXT();

		kernel_function_sys_vfork_hook_pre(&h_ctx);
		if (kernel_function_action_result_is_disallow_function(h_ctx.act_res->type))
		{
			util_log_debug(log_id, "Disallowing function execution due to action result type: %d", h_ctx.act_res->type);
			res = -EACCES;
		} else
		{
			res = _orig();
		}
		kernel_function_sys_vfork_hook_post(&h_ctx, res);
		return res;
	}

#endif


static const char* kernel_function_hook_function_vfork_name(void)
{
#if KERNEL_HELPER_KERNEL_PTREGS_SYSCALL_STUBS
    return "__x64_sys_vfork";
#else
    return "sys_vfork";
#endif
}

static void *kernel_function_hook_function_vfork_original_ptr(void)
{
    return &_orig;
}

static void *kernel_function_hook_function_vfork_hook(void)
{
    return _hook;
}

static const struct kernel_function_hook KERNEL_FUNCTION_SYS_VFORK_HOOK = {
    .get_num = kernel_function_hook_function_vfork_num,
    .get_name = kernel_function_hook_function_vfork_name,
    .get_orig_func_ptr = kernel_function_hook_function_vfork_original_ptr,
    .get_hook_func = kernel_function_hook_function_vfork_hook
};

const struct kernel_function_hook* kernel_function_sys_vfork_hook_get(void)
{
    return &KERNEL_FUNCTION_SYS_VFORK_HOOK;
}
