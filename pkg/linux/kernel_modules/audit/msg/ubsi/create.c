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
#include <linux/string.h>

#include "audit/msg/common/create.h"
#include "audit/msg/ubsi/create.h"


static struct msg_common_version default_version = {
    .major = 1,
    .minor = 0,
    .patch = 0
};
static enum msg_common_type default_msg_type = MSG_UBSI;


int msg_ubsi_create(
    struct msg_ubsi *dst
)
{
    if (!dst)
        return -EINVAL;

    memset(dst, 0, sizeof(struct msg_ubsi));
    return msg_common_create_header(&dst->header, &default_version, default_msg_type);
}