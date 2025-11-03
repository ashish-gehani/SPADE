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

#include "spade/audit/kernel/helper/network.h"

#include "spade/audit/kernel/helper/task.h"
#include "spade/audit/kernel/helper/sock.h"
#include "spade/audit/msg/network/create.h"
#include "spade/audit/global/global.h"
#include "spade/audit/global/filter.h"
#include "spade/util/log/log.h"


int kernel_helper_network_copy_saddr_and_size_from_userspace(
    struct sockaddr_storage *dst,
    uint32_t *dst_size,
    struct sockaddr __user *src,
    uint32_t __user *src_size
)
{
    const char *log_id = "kernel_helper_network_copy_saddr_and_size_from_userspace";

    int err;

    if (!dst || !dst_size || !src || !src_size)
        return -EINVAL;

    err = kernel_helper_sock_copy_sock_len_from_userspace(dst_size, src_size);
    if (err != 0)
    {
        util_log_warn(log_id, "Failed to copy sockaddr len from userspace");
        return err;
    }

    // dst_size is already properly copied above. Therefore, passing it as src_size.
    return kernel_helper_network_copy_only_saddr_from_userspace(
        dst, dst_size,
        src, *dst_size
    );
}

int kernel_helper_network_copy_only_saddr_from_userspace(
    struct sockaddr_storage *dst,
    uint32_t *dst_size,
    struct sockaddr __user *src,
    uint32_t src_size
)
{
    const char *log_id = "kernel_helper_network_copy_only_saddr_from_userspace";

    int err;

    if (!dst || !dst_size || !src)
        return -EINVAL;

    err = kernel_helper_sock_copy_saddr_from_userspace(
        dst, sizeof(*dst),
        src, src_size
    );
    if (err != 0)
    {
        util_log_warn(log_id, "Failed to copy sockaddr from userspace. Err: %d", err);
        return err;
    }

    *dst_size = src_size;

    return 0;
}

int kernel_helper_network_get_peer_saddr_from_fd(
    struct sockaddr_storage *dst,
    uint32_t *dst_size,
    int sockfd
)
{
    const char *log_id = "kernel_helper_network_get_peer_saddr_from_fd";
    int err;
    struct kernel_helper_sock_saddr_info saddr_info = {0};
    int peer_mode = 1; // remote
    bool include_ns_info = false;

    if (!dst || !dst_size)
        return -EINVAL;

    err = kernel_helper_sock_get_saddr_info_from_fd(
        &saddr_info, include_ns_info, peer_mode, sockfd
    );
    if (err != 0)
    {
        util_log_warn(log_id, "Failed to get remote fd saddr info");
        return err;
    }
    memcpy(dst, &(saddr_info.saddr), saddr_info.saddr_size);
    *dst_size = saddr_info.saddr_size;
    return 0;
}

int kernel_helper_network_copy_saddr_and_size_in_msghdr_from_userspace(
    struct sockaddr_storage *dst, uint32_t *dst_size,
    struct msghdr __user *src
)
{
    const char *log_id = "kernel_helper_network_copy_saddr_and_size_in_msghdr_from_userspace";
    int err;

    if (!dst || !dst_size || !src)
        return -EINVAL;

    err = kernel_helper_sock_copy_saddr_in_msghdr_from_userspace(
        dst, dst_size, src
    );
    if (err != 0)
    {
        util_log_warn(log_id, "Failed to get copy saddr in msghdr from userspace. Err: %d", err);
        return err;
    }
    return 0;
}

int kernel_helper_network_is_sockfd_connected(
    bool *dst, int sockfd
)
{
    const char *log_id = "kernel_helper_network_is_sockfd_connected";

    int err;

    if (!dst)
        return -EINVAL;

    err = kernel_helper_sock_is_sockfd_in_connected_state(dst, sockfd);
    if (err != 0)
    {
        util_log_warn(log_id, "Failed to check if sockfd state");
        return err;
    }

    return 0;
}

int kernel_helper_network_populate_msg(
    struct msg_network *msg,
    enum kernel_function_number func_num, long sys_ret, bool sys_success,
    int subject_sockfd,
    struct sockaddr_storage *remote_saddr,
    uint32_t remote_saddr_size
)
{
    const char *log_id = "kernel_helper_network_populate_msg";
    bool include_ns_info = global_filter_function_network_include_ns_info();

    int err;
    int sys_num;
    bool sys_num_default_to_func_num = false;

    struct kernel_helper_sock_saddr_info local_saddr_info = {0};
    int local_peer_mode = 0;

    if (!msg || !remote_saddr)
        return -EINVAL;

    err = kernel_helper_sock_get_saddr_info_from_fd(
        &local_saddr_info, include_ns_info, local_peer_mode, subject_sockfd
    );
    if (err != 0)
    {
        util_log_warn(log_id, "Failed to get local fd saddr info");
        return err;
    }

    err = kernel_helper_task_populate_process_info_from_current_task(&msg->proc_info);
    if (err != 0)
    {
        util_log_warn(log_id, "Failed to copy current process info");
        return err;
    }

    err = kernel_function_number_to_system_call_number(
        &sys_num, func_num, sys_num_default_to_func_num
    );
    if (err != 0)
    {
        util_log_warn(log_id, "Failed to get syscall number from function number: %d. Err: %d", func_num, err);
        return err;
    }

    msg->fd = subject_sockfd;
    msg->local_saddr = local_saddr_info.saddr;
    msg->local_saddr_size = local_saddr_info.saddr_size;
    msg->net_ns_inum = local_saddr_info.net_ns_inum;
    msg->remote_saddr = *remote_saddr;
    msg->remote_saddr_size = remote_saddr_size;
    msg->sock_type = local_saddr_info.sock_type;
    msg->syscall_number = sys_num;
    msg->syscall_result = sys_ret;
    msg->syscall_success = sys_success;

    return 0;
}