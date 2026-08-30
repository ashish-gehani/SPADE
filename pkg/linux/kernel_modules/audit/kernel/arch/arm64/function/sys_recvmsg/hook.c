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
#include "audit/kernel/arch/common/function/sys_recvmsg/hook.h"
#include "audit/util/log/log.h"


#ifdef CONFIG_ARCH_HAS_SYSCALL_WRAPPER

	static asmlinkage long (*_orig)(const struct pt_regs *regs);
    static asmlinkage long _hook(const struct pt_regs *regs);

	static asmlinkage long _hook(const struct pt_regs *regs)
    {
		const char *log_id = "sys_recvmsg::_hook";
		long res;
        int sockfd = (int)(regs->regs[0]);
        struct msghdr __user *msg = (struct msghdr __user *)(regs->regs[1]);
        int flags = (int)(regs->regs[2]);

        const struct kernel_function_hook_context h_ctx = KERNEL_FUNCTION_SYS_RECVMSG_BUILD_HOOK_CONTEXT(sockfd, msg, flags);

        kernel_arch_common_function_sys_recvmsg_hook_pre(&h_ctx);
        if (kernel_arch_common_function_action_result_is_disallow_function(h_ctx.act_res->type))
        {
            util_log_debug(log_id, "Disallowing function execution due to action result type: %d", h_ctx.act_res->type);
            res = -EACCES;
        } else
        {
            res = _orig(regs);
        }
		kernel_arch_common_function_sys_recvmsg_hook_post(&h_ctx, res);
		return res;
	}

#else

	static asmlinkage long (*_orig)(int sockfd, struct msghdr __user *msg, int flags);
    static asmlinkage long _hook(int sockfd, struct msghdr __user *msg, int flags);

    static asmlinkage long _hook(int sockfd, struct msghdr __user *msg, int flags)
    {
		const char *log_id = "sys_recvmsg::_hook";
		long res;

        const struct kernel_function_hook_context h_ctx = KERNEL_FUNCTION_SYS_RECVMSG_BUILD_HOOK_CONTEXT(sockfd, msg, flags);

        kernel_arch_common_function_sys_recvmsg_hook_pre(&h_ctx);
        if (kernel_arch_common_function_action_result_is_disallow_function(h_ctx.act_res->type))
        {
            util_log_debug(log_id, "Disallowing function execution due to action result type: %d", h_ctx.act_res->type);
            res = -EACCES;
        } else
        {
            res = _orig(sockfd, msg, flags);
        }
		kernel_arch_common_function_sys_recvmsg_hook_post(&h_ctx, res);
		return res;
	}

#endif


static const char* kernel_function_hook_function_recvmsg_name(void)
{
#ifdef CONFIG_ARCH_HAS_SYSCALL_WRAPPER
    return "__arm64_sys_recvmsg";
#else
    return "sys_recvmsg";
#endif
}

static void *kernel_function_hook_function_recvmsg_original_ptr(void)
{
    return &_orig;
}

static void *kernel_function_hook_function_recvmsg_hook(void)
{
    return _hook;
}

static const struct kernel_function_hook KERNEL_FUNCTION_SYS_RECVMSG_HOOK = {
    .get_num = kernel_arch_common_function_hook_function_recvmsg_num,
    .get_name = kernel_function_hook_function_recvmsg_name,
    .get_orig_func_ptr = kernel_function_hook_function_recvmsg_original_ptr,
    .get_hook_func = kernel_function_hook_function_recvmsg_hook
};

const struct kernel_function_hook* kernel_arch_common_overridable_function_sys_recvmsg_hook_get(void)
{
    return &KERNEL_FUNCTION_SYS_RECVMSG_HOOK;
}
