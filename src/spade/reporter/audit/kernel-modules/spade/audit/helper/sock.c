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
 #include <linux/net.h>
 #include <linux/socket.h>
 #include <net/sock.h>
 #include <linux/string.h>
 #include <linux/version.h>
 #include <linux/net_namespace.h>

#include "spade/audit/helper/sock.h"
#include "spade/audit/helper/kernel.h"


int helper_sock_is_sockfd_in_connected_state(
    bool *dst,
    int sockfd
)
{
    int err = 0;
	struct socket *fd_sock;

    if (!dst)
        return -EINVAL;
    
    fd_sock = sockfd_lookup(sockfd, &err);
    if (!fd_sock || err != 0)
    {
        err = -ENOENT;
        goto exit;
    }

    if (
        fd_sock->state != SS_CONNECTED
        // && fd_sock->state != SS_CONNECTING // Should use this to cater for -EINPROGRESS ?
    )
    {
        *dst = false;
        goto release_sockfd_and_exit;
    }

    *dst = true;

release_sockfd_and_exit:
    sockfd_put(fd_sock);

exit:
    return err;
}

int helper_sock_get_saddr_info_from_fd(
    struct helper_sock_saddr_info *dst,
    bool include_ns_info,
    int peer_mode,
	int fd
)
{
    struct net *ns_net;
    int err = 0;
	struct socket *fd_sock;
    
    if (!dst)
        return -EINVAL;

    fd_sock = sockfd_lookup(fd, &err);
    if (!fd_sock || err != 0)
    {
        err = -ENOENT;
        goto exit;
    }

    dst->sock_type = fd_sock->type;

    switch (fd_sock->ops->family)
    {
        case AF_UNIX:
        case AF_INET:
        case AF_INET6:
            break;
        default:
        {
            err = -ENOTSUPP;
            goto release_sockfd_and_exit;
        }
    }

#if HELPER_KERNEL_VERSION_GTE_4_17_0
    dst->saddr_size = fd_sock->ops->getname(fd_sock, (struct sockaddr *)&dst->saddr, peer_mode);
    if (dst->saddr_size <= 0)
    {
        err = -ENOENT;
        goto release_sockfd_and_exit;
    }
#else
    err = fd_sock->ops->getname(fd_sock, (struct sockaddr *)&dst->saddr, &dst->saddr_size, peer_mode);
    if (err != 0)
    {
        err = -ENOENT;
        goto release_sockfd_and_exit;
    }
#endif

    if (include_ns_info)
    {
        if (!fd_sock->sk)
        {
            err = -ENOENT;
            goto release_sockfd_and_exit;
        }
        ns_net = sock_net(fd_sock->sk);
        if (!ns_net)
        {
            err = -ENOENT;
            goto release_sockfd_and_exit;
        }
        dst->net_ns_inum = ns_net->ns.inum;
    }

release_sockfd_and_exit:
    sockfd_put(fd_sock);

exit:
    return err;
}

int helper_sock_copy_sock_len_from_userspace(
    uint32_t *dst_len, uint32_t __user *src_len
)
{
    int err;
    if (!dst_len || !src_len)
        return -EINVAL;

    err = copy_from_user(dst_len, src_len, sizeof(uint32_t));

    if (err == 0)
        return 0;
    else if (err < 0)
        return err;
    else // (err > 0)
        return -ENOMEM;
}

int helper_sock_copy_saddr_from_userspace(
    struct sockaddr_storage *dst, uint32_t dst_len,
    const struct sockaddr __user *src, uint32_t src_len
)
{
    int err;
    if (
        !dst || !src
        || dst_len == 0 || src_len == 0
        || src_len > dst_len
        || src_len > sizeof(struct sockaddr_storage)
    )
        return -EINVAL;

    err = copy_from_user(dst, src, src_len);

    if (err == 0)
        return 0;
    else if (err < 0)
        return err;
    else // (err > 0)
        return -ENOMEM;
}

int helper_sock_copy_saddr_in_msghdr_from_userspace(
    struct sockaddr_storage *dst, uint32_t *dst_len,
    struct msghdr __user *src
)
{
    struct msghdr copy_src;

    int err;
    if (!dst || !dst_len || !src)
        return -EINVAL;

    err = copy_from_user(&copy_src, src, sizeof(struct msghdr));

    if (err < 0)
        return err;
    else if (err > 0)
        return -ENOMEM;

    // Here when err == 0 i.e. no error.

    if (
        !copy_src.msg_name
        || copy_src.msg_namelen <= 0
        || copy_src.msg_namelen > sizeof(struct sockaddr_storage)
    )
        return -EINVAL;

    *dst_len = copy_src.msg_namelen;
    err = copy_from_user(dst, copy_src.msg_name, copy_src.msg_namelen);
    if (err < 0)
        return err;
    else // (err > 0)
        return -ENOMEM;

    return 0;
}