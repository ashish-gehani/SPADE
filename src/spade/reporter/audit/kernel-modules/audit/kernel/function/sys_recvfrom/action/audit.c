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

#include "audit/kernel/function/number.h"
#include "audit/kernel/function/hook.h"
#include "audit/kernel/function/sys_recvfrom/action/audit.h"
#include "audit/kernel/function/sys_recvfrom/arg.h"
#include "audit/kernel/function/sys_recvfrom/hook.h"
#include "audit/kernel/function/sys_recvfrom/result.h"
#include "audit/msg/network/network.h"
#include "audit/msg/ops.h"
#include "audit/kernel/helper/network.h"
#include "audit/kernel/helper/audit_log.h"
#include "audit/util/log/log.h"


static const enum msg_common_type GLOBAL_MSG_TYPE = MSG_NETWORK;


int kernel_function_sys_recvfrom_action_audit_handle_post(
    const struct kernel_function_hook_context_post *ctx_post
)
{
    const char *log_id = "kernel_function_sys_recvfrom_action_audit_handle_post";
    int err;

    struct msg_network msg;
    struct sockaddr_storage remote_saddr;
    int remote_saddr_size;
    bool sockfd_is_connected;

    struct kernel_function_sys_recvfrom_arg *sys_arg;
    struct kernel_function_sys_recvfrom_result *sys_res;

    if (!kernel_function_sys_recvfrom_hook_context_post_is_valid(ctx_post))
        return -EINVAL;

    err = msg_ops_kinit(GLOBAL_MSG_TYPE, &msg.header);
    if (err != 0)
    {
        util_log_debug(log_id, "Failed msg_ops_kinit. Err: %d", err);
        return err;
    }

    sys_arg = (struct kernel_function_sys_recvfrom_arg*)ctx_post->header->func_arg->arg;
    sys_res = (struct kernel_function_sys_recvfrom_result*)ctx_post->func_res->res;

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
        err = kernel_helper_network_copy_saddr_and_size_from_userspace(
            &remote_saddr, &remote_saddr_size,
            sys_arg->src_addr, sys_arg->addrlen
        );
        if (err != 0)
        {
            util_log_debug(log_id, "Failed kernel_helper_network_copy_saddr_and_size_from_userspace. Err: %d", err);
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
