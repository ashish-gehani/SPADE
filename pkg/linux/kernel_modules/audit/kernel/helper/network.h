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

#ifndef _SPADE_AUDIT_KERNEL_HELPER_NETWORK_H
#define _SPADE_AUDIT_KERNEL_HELPER_NETWORK_H

#include <linux/errno.h>
#include <linux/socket.h>

#include "audit/msg/network/network.h"
#include "audit/state/state.h"
#include "audit/kernel/function/number.h"


int kernel_helper_network_is_sockfd_connected(
    bool *dst, int sockfd
);

/*
    Copy src, and src_size from userspace into dst, and dst_size, respectively.

    Params:
        dst          : The destination socket address.
        dst_size     : The destination socket address size.
        src          : The source socket address in userspace.
        src_size     : The source socket address size in userspace.

    Returns:
        0       -> Success.
        -1      -> Error code.
*/
int kernel_helper_network_copy_saddr_and_size_from_userspace(
    struct sockaddr_storage *dst,
    int *dst_size,
    struct sockaddr __user *src,
    int __user *src_size
);

/*
    Copy src (ONLY) from userspace into dst.

    Assign dst_size directly from src_size because it is not in userspace.

    Params:
        dst          : The destination socket address.
        dst_size     : The destination socket address size.
        src          : The source socket address in userspace.
        src_size     : The source socket address size.

    Returns:
        0       -> Success.
        -1      -> Error code.
*/
int kernel_helper_network_copy_only_saddr_from_userspace(
    struct sockaddr_storage *dst,
    int *dst_size,
    struct sockaddr __user *src,
    int src_size
);

/*
    Populate dst, and dst_size from peer socket address for the sockfd.

    Params:
        dst          : The destination socket address.
        dst_size     : The destination socket address size.
        sockfd       : The file descriptor of the socket.

    Returns:
        0       -> Success.
        -1      -> Error code.
*/
int kernel_helper_network_get_peer_saddr_from_fd(
    struct sockaddr_storage *dst,
    int *dst_size,
    int sockfd
);

/*
    Populate msg using the context provided.

    It also used subject_sockfd to get socket information for local endpoint.

    Params:
        msg                 : Message to populate.
        func_num            : The function number.
        func_result         : The function result.
        func_success        : The function success.
        subject_sockfd      : The file descriptor on which the system call was performed.
        remote_saddr        : The remote socket address.
        remote_saddr_size   : The size of the remote socket address.

    Returns:
        0       -> Success.
        -1      -> Error code.
*/
int kernel_helper_network_populate_msg(
    struct msg_network *msg,
    enum kernel_function_number func_num, long func_result, bool func_success,
    int subject_sockfd,
    struct sockaddr_storage *remote_saddr,
    int remote_saddr_size
);

/*
    Given the msghdr ptr 'src' in userspace, populate dst, and dst_size from msg_name, and name_len, respectively.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int kernel_helper_network_copy_saddr_and_size_in_msghdr_from_userspace(
    struct sockaddr_storage *dst, int *dst_size,
    struct msghdr __user *src
);

#endif // _SPADE_AUDIT_KERNEL_HELPER_NETWORK_H