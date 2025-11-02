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

#include "spade/audit/kernel/function/number.h"
#include "spade/audit/kernel/function/hook.h"
#include "spade/audit/kernel/function/sys_recvmsg/action/audit.h"
#include "spade/audit/kernel/function/sys_recvmsg/arg.h"
#include "spade/audit/kernel/function/sys_recvmsg/result.h"
#include "spade/audit/msg/network/network.h"
#include "spade/audit/msg/ops.h"
#include "spade/audit/kernel/helper/network.h"
#include "spade/audit/kernel/helper/audit_log.h"
#include "spade/util/log/log.h"


static const enum msg_common_type GLOBAL_MSG_TYPE = MSG_NETWORK;


static bool _is_valid_recvmsg_ctx_post(struct kernel_function_hook_context_post *ctx)
{
    return (
        kernel_function_hook_context_post_is_valid(ctx)
        && ctx->header->func_num == KERN_F_NUM_SYS_RECVMSG
        && ctx->header->func_arg->arg_size == sizeof(struct kernel_function_sys_recvmsg_arg)
        && ctx->func_res->res_size == sizeof(struct kernel_function_sys_recvmsg_result)
        && ctx->func_res->success
    );
}

int kernel_function_sys_recvmsg_action_audit_handle_post(
    struct kernel_function_hook_context_post *ctx_post
)
{
    const char *log_id = "kernel_function_sys_recvmsg_action_audit_handle_post";
    int err;

    struct msg_network msg;
    struct sockaddr_storage remote_saddr;
    uint32_t remote_saddr_size;
    bool sockfd_is_connected;

    struct kernel_function_sys_recvmsg_arg *sys_arg;
    struct kernel_function_sys_recvmsg_result *sys_res;

    if (!_is_valid_recvmsg_ctx_post(ctx_post))
        return -EINVAL;

    err = msg_ops_kinit(GLOBAL_MSG_TYPE, &msg.header);
    if (err != 0)
    {
        util_log_debug(log_id, "Failed msg_ops_kinit. Err: %d", err);
        return err;
    }

    sys_arg = (struct kernel_function_sys_recvmsg_arg*)ctx_post->header->func_arg->arg;
    sys_res = (struct kernel_function_sys_recvmsg_result*)ctx_post->func_res->res;

    err = kernel_helper_network_is_sockfd_connected(&sockfd_is_connected, sys_arg->sockfd);
    if (err != 0)
    {
        util_log_debug(log_id, "Failed kernel_helper_network_sockfd_is_connected. Err: %d", err);
        return err;
    }

    if (sockfd_is_connected)
    {
        err = kernel_helper_network_get_peer_saddr_from_fd(
            &remote_saddr, &remote_saddr_size,
            sys_arg->sockfd
        );
        if (err != 0)
        {
            util_log_debug(log_id, "Failed kernel_helper_network_get_peer_saddr_from_fd. Err: %d", err);
            return err;
        }
    } else
    {
        err = kernel_helper_network_copy_saddr_and_size_in_msghdr_from_userspace(
            &remote_saddr, &remote_saddr_size,
            sys_arg->msg
        );
        if (err != 0)
        {
            util_log_debug(log_id, "Failed kernel_helper_network_copy_saddr_and_size_in_msghdr_from_userspace. Err: %d", err);
            return err;
        }
    }

    err = kernel_helper_network_populate_msg(
        &msg,
        ctx_post->header->func_num, sys_res->ret, ctx_post->func_res->success,
        sys_arg->sockfd, &remote_saddr, remote_saddr_size
    );
    if (err != 0)
    {
        util_log_debug(log_id, "Failed kernel_helper_network_populate_msg. Err: %d", err);
        return err;
    }

    err = kernel_helper_audit_log(NULL, &msg.header);
    if (err != 0)
    {
        util_log_debug(log_id, "Failed kernel_helper_audit_log. Err: %d", err);
    }

    return err;
}
