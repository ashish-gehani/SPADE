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

#include "spade/audit/msg/common/serialize/audit.h"
#include "spade/util/seqbuf/seqbuf.h"


int msg_common_serialize_audit_msg_version(
    struct seqbuf *b, struct msg_common_version *version
)
{
    if (!b || !version)
        return -EINVAL;

    util_seqbuf_printf(
        b, "version=\"%u.%u.%u\"", 
        version->major,
        version->minor,
        version->patch
    );

    return 0;
}

int msg_common_serialize_audit_msg_header(
    struct seqbuf *b, struct msg_common_header *header
)
{
    if (!b || !header)
        return -EINVAL;

    // msg_common_serialize_audit_msg_version(b, &header->version);
    // Don't need the msg type in audit record. TODO

    return 0;
}

static void seqbuf_comm_as_hex_to_string(struct seqbuf *b, char *comm, size_t comm_len)
{
    char hex_comm[HEX_TASK_COMM_LEN];

    memset(&hex_comm[0], 0, HEX_TASK_COMM_LEN);
    bin2hex(&hex_comm[0], comm, comm_len);
    util_seqbuf_printf(b, "comm=%s", &hex_comm[0]);
}

int msg_common_serialize_audit_msg_process(
    struct seqbuf *b, struct msg_common_process *process
)
{
    if (!b || !process)
        return -EINVAL;

    util_seqbuf_printf(b, "pid=%d ", process->pid);
    util_seqbuf_printf(b, "ppid=%d ", process->ppid);
    util_seqbuf_printf(b, "gid=%u ", process->gid);
    util_seqbuf_printf(b, "egid=%u ", process->egid);
    util_seqbuf_printf(b, "sgid=%u ", process->sgid);
    util_seqbuf_printf(b, "fsgid=%u ", process->fsgid);
    util_seqbuf_printf(b, "uid=%u ", process->uid);
    util_seqbuf_printf(b, "euid=%u ", process->euid);
    util_seqbuf_printf(b, "suid=%u ", process->suid);
    util_seqbuf_printf(b, "fsuid=%u ", process->fsuid);
    seqbuf_comm_as_hex_to_string(b, &process->comm[0], TASK_COMM_LEN);

    return 0;
}
