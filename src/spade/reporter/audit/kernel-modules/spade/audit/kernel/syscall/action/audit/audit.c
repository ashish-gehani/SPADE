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
#include <asm/syscall.h>

#include "spade/audit/kernel/syscall/action/audit/audit.h"
#include "spade/audit/kernel/syscall/action/audit/accept/accept.h"
#include "spade/audit/kernel/syscall/action/audit/accept4/accept4.h"
#include "spade/audit/kernel/syscall/action/audit/bind/bind.h"
#include "spade/audit/kernel/syscall/action/audit/clone/clone.h"
#include "spade/audit/kernel/syscall/action/audit/connect/connect.h"
#include "spade/audit/kernel/syscall/action/audit/fork/fork.h"
#include "spade/audit/kernel/syscall/action/audit/kill/kill.h"
#include "spade/audit/kernel/syscall/action/audit/recvfrom/recvfrom.h"
#include "spade/audit/kernel/syscall/action/audit/recvmsg/recvmsg.h"
#include "spade/audit/kernel/syscall/action/audit/sendmsg/sendmsg.h"
#include "spade/audit/kernel/syscall/action/audit/sendto/sendto.h"
#include "spade/audit/kernel/syscall/action/audit/setns/setns.h"
#include "spade/audit/kernel/syscall/action/audit/unshare/unshare.h"
#include "spade/audit/kernel/syscall/action/audit/vfork/vfork.h"
#include "spade/util/log/log.h"


int kernel_syscall_action_audit_handle(
    struct kernel_syscall_context *sys_ctx
)
{
    const char *log_id = "kernel_syscall_action_audit_handle";
    struct kernel_syscall_context_post *sys_ctx_post;

    if (!sys_ctx)
        return -EINVAL;

    // Only post supported for now.
    if (sys_ctx->type != SYSCALL_CONTEXT_TYPE_POST)
        return -ENOTSUPP;

    sys_ctx_post = (struct kernel_syscall_context_post *)sys_ctx;

    util_log_debug(
        log_id,
        "loggable_action={sys_num=%d, sys_exit=%ld, sys_success=%d}",
        sys_ctx_post->header.sys_num, sys_ctx_post->sys_res.ret, sys_ctx_post->sys_res.success
    );

    switch (sys_ctx->sys_num)
    {
        case __NR_accept:
            return kernel_syscall_action_audit_accept_handle(sys_ctx_post);
        case __NR_accept4:
            return kernel_syscall_action_audit_accept4_handle(sys_ctx_post);
        case __NR_bind:
            return kernel_syscall_action_audit_bind_handle(sys_ctx_post);
        case __NR_clone:
            return kernel_syscall_action_audit_clone_handle(sys_ctx_post);
        case __NR_connect:
            return kernel_syscall_action_audit_connect_handle(sys_ctx_post);
        case __NR_fork:
            return kernel_syscall_action_audit_fork_handle(sys_ctx_post);
        case __NR_kill:
            return kernel_syscall_action_audit_kill_handle(sys_ctx_post);
        case __NR_recvfrom:
            return kernel_syscall_action_audit_recvfrom_handle(sys_ctx_post);
        case __NR_recvmsg:
            return kernel_syscall_action_audit_recvmsg_handle(sys_ctx_post);
        case __NR_sendmsg:
            return kernel_syscall_action_audit_sendmsg_handle(sys_ctx_post);
        case __NR_sendto:
            return kernel_syscall_action_audit_sendto_handle(sys_ctx_post);
        case __NR_setns:
            return kernel_syscall_action_audit_setns_handle(sys_ctx_post);
        case __NR_unshare:
            return kernel_syscall_action_audit_unshare_handle(sys_ctx_post);
        case __NR_vfork:
            return kernel_syscall_action_audit_vfork_handle(sys_ctx_post);
        default:
            return -ENOTSUPP;
    }

    return 0;
}
