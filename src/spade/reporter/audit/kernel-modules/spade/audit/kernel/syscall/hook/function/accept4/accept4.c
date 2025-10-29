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

#include "spade/audit/kernel/syscall/hook/function/accept4/accept4.h"
#include "spade/audit/kernel/syscall/arg/accept4.h"
#include "spade/audit/helper/kernel.h"
#include "spade/audit/kernel/syscall/hook/execution/handler/handler.h"


static const int global_sys_num = __NR_accept4;

////

static void _pre(
    int fd, struct sockaddr __user *addr, uint32_t __user *addrlen, int flags
)
{
    int err;

    int sys_num = global_sys_num;

    struct kernel_syscall_action_result act_res = {0};

    struct kernel_syscall_arg_accept4 syscall_args = {
        .sockfd = fd,
        .addr = addr,
        .addrlen = addrlen,
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
    long sys_res, int fd, struct sockaddr __user *addr, uint32_t __user *addrlen, int flags
)
{
    int err;

    int sys_num = global_sys_num;

    struct kernel_syscall_action_result act_res = {0};

    struct kernel_syscall_arg_accept4 syscall_args = {
        .sockfd = fd,
        .addr = addr,
        .addrlen = addrlen,
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
        int fd = (int)(regs->di);
        struct sockaddr __user *addr = (struct sockaddr *)(regs->si);
        uint32_t __user *addr_size = (uint32_t *)(regs->dx);
        int flags = (int)(regs->r10);

        _pre(fd, addr, addr_size, flags);
		res = _orig(regs);
		_post(res, fd, addr, addr_size, flags);
		return res;
	}

#else

	static asmlinkage long (*_orig)(int fd, struct sockaddr __user *addr, uint32_t __user *addr_size, int flags);
    static asmlinkage long _hook(int fd, struct sockaddr __user *addr, uint32_t __user *addr_size, int flags);

    static asmlinkage long _hook(int fd, struct sockaddr __user *addr, uint32_t __user *addr_size, int flags)
    {
		long res;
        _pre(fd, addr, addr_size, flags);
		res = _orig(fd, addr, addr_size, flags);
		_post(res, fd, addr, addr_size, flags);
		return res;
	}

#endif

static int kernel_syscall_hook_function_accept4_num(void)
{
    return global_sys_num;
}

static const char* kernel_syscall_hook_function_accept4_name(void)
{
#if HELPER_KERNEL_PTREGS_SYSCALL_STUBS
    return "__x64_sys_accept4";
#else
    return "sys_accept4";
#endif
}

static void *kernel_syscall_hook_function_accept4_original_ptr(void)
{
    return &_orig;
}

static void *kernel_syscall_hook_function_accept4_hook(void)
{
    return _hook;
}

const struct kernel_syscall_hook kernel_syscall_hook_accept4 = {
    .get_num = kernel_syscall_hook_function_accept4_num,
    .get_name = kernel_syscall_hook_function_accept4_name,
    .get_orig_func_ptr = kernel_syscall_hook_function_accept4_original_ptr,
    .get_hook_func = kernel_syscall_hook_function_accept4_hook
};