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

#include "spade/audit/helper/kernel.h"
#include "spade/audit/kernel/function/arg.h"
#include "spade/audit/kernel/function/action.h"
#include "spade/audit/kernel/function/hook.h"
#include "spade/audit/kernel/function/result.h"
#include "spade/audit/kernel/function/sys_sendto/arg.h"
#include "spade/audit/kernel/function/sys_sendto/hook.h"
#include "spade/audit/kernel/function/sys_sendto/result.h"


static const enum kernel_function_number global_func_num = KERN_F_NUM_SYS_SENDTO;


static void _pre(int sockfd, const void __user *buf, size_t len, int flags, struct sockaddr __user *dest_addr, int addrlen)
{
    int err;

    struct kernel_function_hook_context_pre hook_ctx_pre = {
        .header = &(const struct kernel_function_hook_context){
            .type = KERNEL_FUNCTION_HOOK_CONTEXT_TYPE_PRE,
            .func_num = global_func_num,
            .func_arg = &(const struct kernel_function_arg){
                .arg = &(const struct kernel_function_sys_sendto_arg){
                    .sockfd = sockfd,
                    .buf = buf,
                    .len = len,
                    .flags = flags,
                    .dest_addr = dest_addr,
                    .addrlen = addrlen
                },
                .arg_size = sizeof(struct kernel_function_sys_sendto_arg)
            },
            .act_res = &(struct kernel_function_action_result){0}
        }
    };

    err = kernel_function_hook_pre(&hook_ctx_pre);
    if (err != 0)
        return;

    return;
}

static void _post(long sys_res, int sockfd, const void __user *buf, size_t len, int flags, struct sockaddr __user *dest_addr, int addrlen)
{
    int err;

    struct kernel_function_hook_context_post hook_ctx_post = {
        .header = &(const struct kernel_function_hook_context){
            .type = KERNEL_FUNCTION_HOOK_CONTEXT_TYPE_POST,
            .func_num = global_func_num,
            .func_arg = &(const struct kernel_function_arg){
                .arg = &(const struct kernel_function_sys_sendto_arg){
                    .sockfd = sockfd,
                    .buf = buf,
                    .len = len,
                    .flags = flags,
                    .dest_addr = dest_addr,
                    .addrlen = addrlen
                },
                .arg_size = sizeof(struct kernel_function_sys_sendto_arg)
            },
            .act_res = &(struct kernel_function_action_result){0}
        },
        .func_res = &(const struct kernel_function_result){
            .res = &(const struct kernel_function_sys_sendto_result){
                .ret = sys_res
            },
            .res_size = sizeof(struct kernel_function_sys_sendto_result),
            .success = (sys_res >= 0)
        }
    };

    err = kernel_function_hook_post(&hook_ctx_post);
    if (err != 0)
        return;

    return;
}


#if HELPER_KERNEL_PTREGS_SYSCALL_STUBS

	static asmlinkage long (*_orig)(const struct pt_regs *regs);
    static asmlinkage long _hook(const struct pt_regs *regs);

	static asmlinkage long _hook(const struct pt_regs *regs)
    {
		long res;
        int sockfd = (int)(regs->di);
        const void __user *buf = (const void __user *)(regs->si);
        size_t len = (size_t)(regs->dx);
        int flags = (int)(regs->r10);
        struct sockaddr __user *dest_addr = (struct sockaddr __user *)(regs->r8);
        int addrlen = (int)(regs->r9);

        _pre(sockfd, buf, len, flags, dest_addr, addrlen);
		res = _orig(regs);
		_post(res, sockfd, buf, len, flags, dest_addr, addrlen);
		return res;
	}

#else

	static asmlinkage long (*_orig)(int sockfd, const void __user *buf, size_t len, int flags, struct sockaddr __user *dest_addr, int addrlen);
    static asmlinkage long _hook(int sockfd, const void __user *buf, size_t len, int flags, struct sockaddr __user *dest_addr, int addrlen);

    static asmlinkage long _hook(int sockfd, const void __user *buf, size_t len, int flags, struct sockaddr __user *dest_addr, int addrlen)
    {
		long res;
        _pre(sockfd, buf, len, flags, dest_addr, addrlen);
		res = _orig(sockfd, buf, len, flags, dest_addr, addrlen);
		_post(res, sockfd, buf, len, flags, dest_addr, addrlen);
		return res;
	}

#endif


static enum kernel_function_number kernel_function_hook_function_sendto_num(void)
{
    return global_func_num;
}

static const char* kernel_function_hook_function_sendto_name(void)
{
#if HELPER_KERNEL_PTREGS_SYSCALL_STUBS
    return "__x64_sys_sendto";
#else
    return "sys_sendto";
#endif
}

static void *kernel_function_hook_function_sendto_original_ptr(void)
{
    return &_orig;
}

static void *kernel_function_hook_function_sendto_hook(void)
{
    return _hook;
}

const struct kernel_function_hook KERNEL_FUNCTION_SYS_SENDTO_HOOK = {
    .get_num = kernel_function_hook_function_sendto_num,
    .get_name = kernel_function_hook_function_sendto_name,
    .get_orig_func_ptr = kernel_function_hook_function_sendto_original_ptr,
    .get_hook_func = kernel_function_hook_function_sendto_hook
};
