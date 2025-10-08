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

#include "spade/audit/kernel/syscall/hook/function/recvfrom/recvfrom.h"
#include "spade/audit/kernel/syscall/arg/recvfrom.h"
#include "spade/audit/helper/kernel.h"
#include "spade/audit/kernel/syscall/hook/execution/handler/handler.h"


static const int global_sys_num = __NR_recvfrom;
static const char* global_sys_name = "recvfrom";

////

static void _pre(
    int fd, void __user *buf, size_t len, int flags,
    struct sockaddr __user *src_addr, uint32_t __user *addrlen
)
{
    int err;

    int sys_num = global_sys_num;

    struct kernel_syscall_action_result act_res = {0};

    struct kernel_syscall_arg_recvfrom syscall_args = {
        .sockfd = fd,
        .buf = buf,
        .len = len,
        .flags = flags,
        .src_addr = src_addr,
        .addrlen = addrlen
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
    int fd, void __user *buf, size_t len, int flags,
    struct sockaddr __user *src_addr, uint32_t __user *addrlen
)
{
    int err;

    int sys_num = global_sys_num;

    struct kernel_syscall_action_result act_res = {0};

    struct kernel_syscall_arg_recvfrom syscall_args = {
        .sockfd = fd,
        .buf = buf,
        .len = len,
        .flags = flags,
        .src_addr = src_addr,
        .addrlen = addrlen
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
        void __user *buf = (void *)(regs->si);
        size_t len = (size_t)(regs->dx);
        int flags = (int)(regs->r10);
        struct sockaddr __user *src_addr = (struct sockaddr *)(regs->r8);
        uint32_t __user *addrlen = (uint32_t *)(regs->r9);

        _pre(fd, buf, len, flags, src_addr, addrlen);
		res = _orig(regs);
        _post(res, fd, buf, len, flags, src_addr, addrlen);
		return res;
	}

#else

	static asmlinkage long (*_orig)(int fd, void __user *buf, size_t len, int flags, struct sockaddr __user *src_addr, uint32_t __user *addrlen);
    static asmlinkage long _hook(int fd, void __user *buf, size_t len, int flags, struct sockaddr __user *src_addr, uint32_t __user *addrlen);

    static asmlinkage long _hook(int fd, void __user *buf, size_t len, int flags, struct sockaddr __user *src_addr, uint32_t __user *addrlen)
    {
		long res;
        _pre(fd, buf, len, flags, src_addr, addrlen);
		res = _orig(fd, buf, len, flags, src_addr, addrlen);
        _post(res, fd, buf, len, flags, src_addr, addrlen);
		return res;
	}

#endif

int kernel_syscall_hook_function_recvfrom_num(void)
{
    return global_sys_num;
}

const char* kernel_syscall_hook_function_recvfrom_name(void)
{
    return global_sys_name;
}

void *kernel_syscall_hook_function_recvfrom_original_ptr(void)
{
    return &_orig;
}

void *kernel_syscall_hook_function_recvfrom_hook(void)
{
    return _hook;
}