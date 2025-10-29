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

#include "spade/audit/kernel/syscall/hook/function/recvmsg/recvmsg.h"
#include "spade/audit/kernel/syscall/arg/recvmsg.h"
#include "spade/audit/helper/kernel.h"
#include "spade/audit/kernel/syscall/hook/execution/handler/handler.h"


static const int global_sys_num = __NR_recvmsg;

////

static void _pre(
    int sockfd, struct msghdr __user *msg, int flags
)
{
    int err;

    int sys_num = global_sys_num;

    struct kernel_syscall_action_result act_res = {0};

    struct kernel_syscall_arg_recvmsg syscall_args = {
        .sockfd = sockfd,
        .msg = msg,
        .flags = flags
    };

    struct kernel_syscall_arg sys_arg = {
        .arg = &syscall_args,
        .arg_size = sizeof(syscall_args)
    };

    err = kernel_syscall_hook_execution_handler_handle_pre(
        &act_res, sys_num, &sys_arg
    );
    if (err != 0)
        return;

    return;
}

static void _post(
    long sys_res,
    int sockfd, struct msghdr __user *msg, int flags
)
{
    int err;

    int sys_num = global_sys_num;

    struct kernel_syscall_action_result act_res = {0};

    struct kernel_syscall_arg_recvmsg syscall_args = {
        .sockfd = sockfd,
        .msg = msg,
        .flags = flags
    };

    struct kernel_syscall_arg sys_arg = {
        .arg = &syscall_args,
        .arg_size = sizeof(syscall_args)
    };

    struct kernel_syscall_result k_sys_res = {
        .ret = sys_res,
        .success = (sys_res >= 0)
    };

    err = kernel_syscall_hook_execution_handler_handle_post(
        &act_res, sys_num, &sys_arg, &k_sys_res
    );
    if (err != 0)
        return;

    return;
}

////


#if HELPER_KERNEL_PTREGS_SYSCALL_STUBS

	static asmlinkage long (*_orig)(const struct pt_regs *regs);
    static asmlinkage long _hook(const struct pt_regs *regs);

	static asmlinkage long _hook(const struct pt_regs *regs)
    {
		long res;
        int sockfd = (int)(regs->di);
        struct msghdr __user *msg = (struct msghdr *)(regs->si);
        int flags = (int)(regs->dx);

        _pre(sockfd, msg, flags);
		res = _orig(regs);
        _post(res, sockfd, msg, flags);
		return res;
	}

#else

	static asmlinkage long (*_orig)(int sockfd, struct msghdr __user *msg, int flags);
    static asmlinkage long _hook(int sockfd, struct msghdr __user *msg, int flags);

    static asmlinkage long _hook(int sockfd, struct msghdr __user *msg, int flags)
    {
		long res;

        _pre(sockfd, msg, flags);
		res = _orig(sockfd, msg, flags);
        _post(res, sockfd, msg, flags);
		return res;
	}

#endif

static int kernel_syscall_hook_function_recvmsg_num(void)
{
    return global_sys_num;
}

static const char* kernel_syscall_hook_function_recvmsg_name(void)
{
#if HELPER_KERNEL_PTREGS_SYSCALL_STUBS
    return "__x64_sys_recvmsg";
#else
    return "sys_recvmsg";
#endif
}

static void *kernel_syscall_hook_function_recvmsg_original_ptr(void)
{
    return &_orig;
}

static void *kernel_syscall_hook_function_recvmsg_hook(void)
{
    return _hook;
}

const struct kernel_syscall_hook kernel_syscall_hook_recvmsg = {
    .get_num = kernel_syscall_hook_function_recvmsg_num,
    .get_name = kernel_syscall_hook_function_recvmsg_name,
    .get_orig_func_ptr = kernel_syscall_hook_function_recvmsg_original_ptr,
    .get_hook_func = kernel_syscall_hook_function_recvmsg_hook
};