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
#include "audit/kernel/arch/common/function/action.h"
#include "audit/kernel/arch/common/function/hook.h"
#include "audit/kernel/arch/common/function/sys_kill/hook.h"
#include "audit/util/log/log.h"


#if KERNEL_HELPER_KERNEL_PTREGS_SYSCALL_STUBS

	static asmlinkage long (*_orig)(const struct pt_regs *regs);
    static asmlinkage long _hook(const struct pt_regs *regs);

	static asmlinkage long _hook(const struct pt_regs *regs)
    {
        const char *log_id = "sys_kill::_hook";
		long res;
        pid_t pid = (pid_t)(regs->di);
        int sig = (int)(regs->si);

        const struct kernel_function_hook_context h_ctx = KERNEL_FUNCTION_SYS_KILL_BUILD_HOOK_CONTEXT(pid, sig);

        kernel_arch_common_function_sys_kill_hook_pre(&h_ctx);
        if (kernel_arch_common_function_action_result_is_disallow_function(h_ctx.act_res->type))
        {
            util_log_debug(log_id, "Disallowing function execution due to action result type: %d", h_ctx.act_res->type);
            res = -EACCES;
        } else
        {
            res = _orig(regs);
        }
		kernel_arch_common_function_sys_kill_hook_post(&h_ctx, res, pid);
		return res;
	}

#else

	static asmlinkage long (*_orig)(pid_t pid, int sig);
    static asmlinkage long _hook(pid_t pid, int sig);

    static asmlinkage long _hook(pid_t pid, int sig)
    {
        const char *log_id = "sys_kill::_hook";
		long res;

        const struct kernel_function_hook_context h_ctx = KERNEL_FUNCTION_SYS_KILL_BUILD_HOOK_CONTEXT(pid, sig);

        kernel_arch_common_function_sys_kill_hook_pre(&h_ctx);
        if (kernel_arch_common_function_action_result_is_disallow_function(h_ctx.act_res->type))
        {
            util_log_debug(log_id, "Disallowing function execution due to action result type: %d", h_ctx.act_res->type);
            res = -EACCES;
        } else
        {
            res = _orig(pid, sig);
        }
        kernel_arch_common_function_sys_kill_hook_post(&h_ctx, res, pid);
		return res;
	}

#endif


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

static const struct kernel_function_hook KERNEL_FUNCTION_SYS_KILL_HOOK = {
    .get_num = kernel_arch_common_function_hook_function_kill_num,
    .get_name = kernel_function_hook_function_kill_name,
    .get_orig_func_ptr = kernel_function_hook_function_kill_original_ptr,
    .get_hook_func = kernel_function_hook_function_kill_hook
};

const struct kernel_function_hook* kernel_arch_common_overridable_function_sys_kill_hook_get(void)
{
    return &KERNEL_FUNCTION_SYS_KILL_HOOK;
}
