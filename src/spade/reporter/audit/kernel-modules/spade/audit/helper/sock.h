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

#ifndef _SPADE_AUDIT_HELPER_SOCK_H
#define _SPADE_AUDIT_HELPER_SOCK_H

#include <linux/net.h>
#include <linux/socket.h>

struct helper_sock_saddr_info
{
    int fd;
    struct sockaddr saddr;
    size_t saddr_size;
    short sock_type;
    unsigned int net_ns_inum;
};

/*
    Check if the given socket fd is in connected state.

    Params:
        dst             : The result variable.
        sockfd          : The file descriptor to use to get socket address information.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int helper_sock_is_sockfd_in_connected_state(
    bool *dst,
    int sockfd
);

/*
    Get socket address and related information from file description fd.

    Params:
        dst             : The structure to populate with socket address information.
        include_ns_info : Boolean flag that controls whether network namespace information is included.
        peer_mode       : As defined by socket->getname function. 0 -> Local address, 1 -> Peer address.
        fd              : The file descriptor to use to get socket address information.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int helper_sock_get_saddr_info_from_fd(
    struct helper_sock_saddr_info *dst,
    bool include_ns_info,
    int peer_mode,
	int fd
);

/*
    Copy sock len from userspace to kernel space.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int helper_sock_copy_sock_len_from_userspace(
    uint32_t *dst_len, uint32_t __user *src_len
);

/*
    Copy sockaddr from userspace to kernel space.

    Params:
        dst             : The destination sockaddr to put the result in.
        avail_dst_len   : The available destination length. Used for checks.
        src             : The src sockaddr ptr (from userspace).
        src_len         : The src sockaddr len.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int helper_sock_copy_saddr_from_userspace(
    struct sockaddr *dst, uint32_t avail_dst_len,
    const struct sockaddr __user *src, uint32_t src_len
);

/*
    Copy sockaddr in msghdr (from userspace ptr).

    Params:
        dst     : The destination sockaddr to put the result in.
        dst_len : The destination sockaddr to put the result length in.
        src     : The src msghdr ptr from userspace.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int helper_sock_copy_saddr_in_msghdr_from_userspace(
    struct sockaddr *dst, uint32_t *dst_len,
    struct msghdr __user *src
);

#endif // _SPADE_AUDIT_HELPER_SOCK_H