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

#include "spade/audit/msg/common/create.h"


int msg_common_create_version(
    struct msg_common_version *dst,
    u8 major, u8 minor, u8 patch
)
{
    if (!dst)
        return -EINVAL;

    dst->major = major;
    dst->minor = minor;
    dst->patch = patch;
    return 0;
}

int msg_common_create_header(
    struct msg_common_header *dst,
    struct msg_common_version *version,
    enum msg_common_type msg_type
)
{
    if (!dst || !version)
        return -EINVAL;

    memset(dst, 0, sizeof(struct msg_common_header));
    memcpy(&(dst->version), version, sizeof(struct msg_common_version));
    dst->msg_type = msg_type;
    return 0;
}

int msg_common_create_process(
    struct msg_common_process *dst
)
{
    if (!dst)
        return -EINVAL;

    memset(dst, 0, sizeof(struct msg_common_process));

    return 0;
}