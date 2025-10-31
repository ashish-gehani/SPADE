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
#include "spade/audit/kernel/function/sys_connect/arg.h"
#include "spade/audit/kernel/function/sys_connect/hook.h"
#include "spade/audit/kernel/function/sys_connect/result.h"


static const enum kernel_function_number global_func_num = KERN_F_NUM_SYS_CONNECT;


static void _pre(
    int fd, struct sockaddr __user *addr, uint32_t addrlen
)
{
    int err;

    struct kernel_function_hook_context_pre hook_ctx_pre = {
        .header = &(const struct kernel_function_hook_context){
            .type = KERNEL_FUNCTION_HOOK_CONTEXT_TYPE_PRE,
            .func_num = global_func_num,
            .func_arg = &(const struct kernel_function_arg){
                .arg = &(const struct kernel_function_sys_connect_arg){
                    .sockfd = fd,
                    .addr = addr,
                    .addrlen = addrlen
                },
                .arg_size = sizeof(struct kernel_function_sys_connect_arg)
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

static void _post(
    long sys_res, int fd, struct sockaddr __user *addr, uint32_t addrlen
)
{
    int err;

    struct kernel_function_hook_context_post hook_ctx_post = {
        .header = &(const struct kernel_function_hook_context){
            .type = KERNEL_FUNCTION_HOOK_CONTEXT_TYPE_POST,
            .func_num = global_func_num,
            .func_arg = &(const struct kernel_function_arg){
                .arg = &(const struct kernel_function_sys_connect_arg){
                    .sockfd = fd,
                    .addr = addr,
                    .addrlen = addrlen
                },
                .arg_size = sizeof(struct kernel_function_sys_connect_arg)
            },
            .act_res = &(struct kernel_function_action_result){0}
        },
        .func_res = &(const struct kernel_function_result){
            .res = &(const struct kernel_function_sys_connect_result){
                .ret = sys_res
            },
            .res_size = sizeof(struct kernel_function_sys_connect_result),
            .success = (sys_res == 0 || sys_res == -EINPROGRESS)
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
        int fd = (int)(regs->di);
        struct sockaddr __user *addr = (struct sockaddr *)(regs->si);
        uint32_t addr_size = (uint32_t)(regs->dx);

        _pre(fd, addr, addr_size);
		res = _orig(regs);
		_post(res, fd, addr, addr_size);
		return res;
	}

#else

	static asmlinkage long (*_orig)(int fd, struct sockaddr __user *addr, uint32_t addr_size);
    static asmlinkage long _hook(int fd, struct sockaddr __user *addr, uint32_t addr_size);

    static asmlinkage long _hook(int fd, struct sockaddr __user *addr, uint32_t addr_size)
    {
		long res;
        _pre(fd, addr, addr_size);
		res = _orig(fd, addr, addr_size);
        _post(res, fd, addr, addr_size);
		return res;
	}

#endif


static enum kernel_function_number kernel_function_hook_function_connect_num(void)
{
    return global_func_num;
}

static const char* kernel_function_hook_function_connect_name(void)
{
#if HELPER_KERNEL_PTREGS_SYSCALL_STUBS
    return "__x64_sys_connect";
#else
    return "sys_connect";
#endif
}

static void *kernel_function_hook_function_connect_original_ptr(void)
{
    return &_orig;
}

static void *kernel_function_hook_function_connect_hook(void)
{
    return _hook;
}

const struct kernel_function_hook KERNEL_FUNCTION_SYS_CONNECT_HOOK = {
    .get_num = kernel_function_hook_function_connect_num,
    .get_name = kernel_function_hook_function_connect_name,
    .get_orig_func_ptr = kernel_function_hook_function_connect_original_ptr,
    .get_hook_func = kernel_function_hook_function_connect_hook
};
