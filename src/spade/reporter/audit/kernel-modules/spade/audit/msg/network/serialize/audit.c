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

#include <linux/kernel.h>
#include <linux/errno.h>
#include <linux/string.h>
#include <linux/un.h>

#include "spade/audit/msg/common/common.h"
#include "spade/audit/msg/common/serialize/audit.h"
#include "spade/audit/msg/network/serialize/audit.h"

#define SAADR_HEX_LEN (sizeof(struct sockaddr_storage) * 2 + 1)

/*
 * Serialize Unix domain socket address (sockaddr_un) as hex string.
 * Unix sockets have two types of paths in sun_path field:
 * 1. Filesystem paths: null-terminated string (use strlen)
 * 2. Abstract socket paths: start with '\0', not null-terminated (use full length from saddr_len)
 * Only hexifies from start of structure to the actual path length.
 */
static void seqbuf_saddr_un_to_string(
    struct seqbuf *b, char *key_name,
    struct sockaddr_un *sun, int saddr_len
)
{
    int path_offset;
    int path_len;
    int total_len;
    char hex[SAADR_HEX_LEN];

    /* Calculate offset to sun_path field */
    path_offset = offsetof(struct sockaddr_un, sun_path);

    /* Calculate actual path length */
    if (saddr_len <= path_offset)
    {
        /* Invalid or empty path */
        path_len = 0;
    }
    else if (sun->sun_path[0] == '\0')
    {
        /* Abstract socket: path starts with null byte, use full length from saddr_len */
        path_len = saddr_len - path_offset;
    }
    else
    {
        /* Filesystem socket: null-terminated string, use strlen */
        size_t str_len = strnlen(sun->sun_path, saddr_len - path_offset);
        path_len = str_len;
        /* Include null terminator if present */
        if (str_len < (saddr_len - path_offset) && sun->sun_path[str_len] == '\0')
        {
            path_len = str_len + 1;
        }
    }

    /* Total bytes to hexify: sa_family + actual path */
    total_len = path_offset + path_len;

    memset(&hex[0], 0, SAADR_HEX_LEN);

    /* Hexify only from start (sa_family) to end of actual path */
    bin2hex(hex, sun, total_len);

    util_seqbuf_printf(b, "%s=%s", key_name, &hex[0]);
}

/*
 * Serialize generic socket address.
 * Detects AF_UNIX family and delegates to sockaddr_un handler.
 */
static void seqbuf_saddr_to_string(
    struct seqbuf *b, char *key_name,
    struct sockaddr_storage *saddr, int saddr_len
)
{
    struct sockaddr *sa = (struct sockaddr *)saddr;

    /* Check if this is a Unix domain socket */
    if (sa->sa_family == AF_UNIX)
    {
        seqbuf_saddr_un_to_string(b, key_name, (struct sockaddr_un *)saddr, saddr_len);
    } else
    {
        char hex[SAADR_HEX_LEN];
        memset(&hex[0], 0, SAADR_HEX_LEN);
        bin2hex(hex, saddr, saddr_len);
        util_seqbuf_printf(b, "%s=%s", key_name, &hex[0]);
    }
}

static int _msg_network_serialize_audit_msg(
    struct seqbuf *b, struct msg_network *msg
)
{
    if (!b || !msg)
        return -EINVAL;

    util_seqbuf_printf(b, "netio_intercepted=\"");

    msg_common_serialize_audit_msg_header(b, &msg->header);

    util_seqbuf_printf(b, "syscall=%d", msg->syscall_number);
    util_seqbuf_printf(b, " exit=%ld", msg->syscall_result);
    util_seqbuf_printf(b, " success=%d", msg->syscall_success);
    util_seqbuf_printf(b, " fd=%d ", msg->fd);
    msg_common_serialize_audit_msg_process(b, &msg->proc_info);
    util_seqbuf_printf(b, " sock_type=%d", msg->sock_type);
    seqbuf_saddr_to_string(
        b, " local_saddr", &msg->local_saddr, 
        msg->local_saddr_size
    );
    seqbuf_saddr_to_string(
        b, " remote_saddr", &msg->remote_saddr, 
        msg->remote_saddr_size
    );
    util_seqbuf_printf(b, " remote_saddr_size=%d", msg->remote_saddr_size); // todo... do we need this field?
    util_seqbuf_printf(b, " net_ns_inum=%u", msg->net_ns_inum);

    util_seqbuf_printf(b, "\"");

    return 0;
}

int msg_network_serialize_audit_msg(
    struct seqbuf *b, struct msg_network *msg
)
{
    if (!msg)
        return -EINVAL;
    return _msg_network_serialize_audit_msg(b, msg);
}