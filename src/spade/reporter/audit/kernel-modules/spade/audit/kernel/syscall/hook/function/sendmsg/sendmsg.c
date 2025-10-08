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

#include "spade/audit/kernel/syscall/hook/function/sendmsg/sendmsg.h"
#include "spade/audit/kernel/syscall/arg/sendmsg.h"
#include "spade/audit/helper/kernel.h"
#include "spade/audit/kernel/syscall/hook/execution/handler/handler.h"


static const int global_sys_num = __NR_sendmsg;
static const char* global_sys_name = "sendmsg";

////

static void _pre(
    int sockfd, struct msghdr __user *msg, int flags
)
{
    int err;

    int sys_num = global_sys_num;

    struct kernel_syscall_action_result act_res = {0};

    struct kernel_syscall_arg_sendmsg syscall_args = {
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

    struct kernel_syscall_arg_sendmsg syscall_args = {
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

int kernel_syscall_hook_function_sendmsg_num(void)
{
    return global_sys_num;
}

const char* kernel_syscall_hook_function_sendmsg_name(void)
{
    return global_sys_name;
}

void *kernel_syscall_hook_function_sendmsg_original_ptr(void)
{
    return &_orig;
}

void *kernel_syscall_hook_function_sendmsg_hook(void)
{
    return _hook;
}