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

#include "spade/audit/kernel/syscall/action/audit/accept4/accept4.h"
#include "spade/audit/kernel/syscall/arg/accept4.h"
#include "spade/audit/msg/network/network.h"
#include "spade/audit/msg/ops.h"
#include "spade/audit/helper/syscall/network.h"
#include "spade/audit/helper/audit_log.h"


static const enum msg_common_type GLOBAL_MSG_TYPE = MSG_NETIO_INTERCEPTED;


static bool _is_valid_sys_ctx(struct kernel_syscall_context_post *sys_ctx)
{
    return (
        sys_ctx && sys_ctx->header.type == SYSCALL_CONTEXT_TYPE_POST && sys_ctx->header.sys_num == __NR_accept4
        && sys_ctx->header.sys_arg.arg != NULL && sys_ctx->header.sys_arg.arg_size == sizeof(struct kernel_syscall_arg_accept4)
        && sys_ctx->sys_res.success
    );
}

int kernel_syscall_action_audit_accept4_handle(struct kernel_syscall_context_post *sys_ctx)
{
    int err;

    struct msg_network msg;
    struct sockaddr remote_saddr;
    uint32_t remote_saddr_size;

    struct kernel_syscall_arg_accept4 *sys_arg;

    if (!_is_valid_sys_ctx(sys_ctx))
        return -EINVAL;

    err = msg_ops_kinit(GLOBAL_MSG_TYPE, &msg.header);
    if (err != 0)
        return err;

    sys_arg = (struct kernel_syscall_arg_accept4*)sys_ctx->header.sys_arg.arg;

    if (sys_arg->addr)
    {
        err = helper_syscall_network_copy_saddr_and_size_from_userspace(
            &remote_saddr, &remote_saddr_size,
            sys_arg->addr, sys_arg->addrlen
        );
        if (err != 0)
            return err;
    } else
    {
        err = helper_syscall_network_get_peer_saddr_from_fd(
            &remote_saddr, &remote_saddr_size,
            sys_arg->sockfd
        );
        if (err != 0)
            return err;
    }

    err = helper_syscall_network_populate_msg(
        &msg, sys_ctx, sys_arg->sockfd, &remote_saddr, remote_saddr_size
    );
    if (err != 0)
        return err;

    err = helper_audit_log(NULL, &msg.header);

    return err;
}