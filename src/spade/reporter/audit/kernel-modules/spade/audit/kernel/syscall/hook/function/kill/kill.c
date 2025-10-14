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

#include "spade/audit/kernel/syscall/hook/function/kill/kill.h"
#include "spade/audit/kernel/syscall/hook/function/kill/ubsi.h"
#include "spade/audit/kernel/syscall/arg/kill.h"
#include "spade/audit/helper/kernel.h"
#include "spade/audit/kernel/syscall/hook/execution/handler/handler.h"


static const int global_sys_num = __NR_kill;
static const char* global_sys_name = "kill";

////

static void _pre(pid_t pid, int sig)
{
    int err;

    int sys_num = global_sys_num;

    struct kernel_syscall_action_result act_res = {0};

    struct kernel_syscall_arg_kill syscall_args = {
        .pid = pid,
        .sig = sig
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

static bool _get_sys_success(long sys_res, pid_t pid)
{
    switch (pid)
    {
        case UBSI_UENTRY:
        case UBSI_UENTRY_ID:
        case UBSI_UEXIT:
        case UBSI_MREAD1:
        case UBSI_MREAD2:
        case UBSI_MWRITE1:
        case UBSI_MWRITE2:
        case UBSI_UDEP:
            return true;
        default:
            return sys_res == 0;
    }
}

static void _post(long sys_res, pid_t pid, int sig)
{
    int err;

    int sys_num = global_sys_num;

    struct kernel_syscall_action_result act_res = {0};

    struct kernel_syscall_arg_kill syscall_args = {
        .pid = pid,
        .sig = sig
    };

    struct kernel_syscall_arg sys_arg = {
        .arg = &syscall_args,
        .arg_size = sizeof(syscall_args)
    };

    struct kernel_syscall_result k_sys_res = {
        .ret = sys_res,
        .success = _get_sys_success(sys_res, pid)
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
        pid_t pid = (pid_t)(regs->di);
        int sig = (int)(regs->si);

        _pre(pid, sig);
		res = _orig(regs);
		_post(res, pid, sig);
		return res;
	}

#else

	static asmlinkage long (*_orig)(pid_t pid, int sig);
    static asmlinkage long _hook(pid_t pid, int sig);

    static asmlinkage long _hook(pid_t pid, int sig)
    {
		long res;
        _pre(pid, sig);
		res = _orig(pid, sig);
		_post(res, pid, sig);
		return res;
	}

#endif

int kernel_syscall_hook_function_kill_num(void)
{
    return global_sys_num;
}

const char* kernel_syscall_hook_function_kill_name(void)
{
    return global_sys_name;
}

void *kernel_syscall_hook_function_kill_original_ptr(void)
{
    return &_orig;
}

void *kernel_syscall_hook_function_kill_hook(void)
{
    return _hook;
}