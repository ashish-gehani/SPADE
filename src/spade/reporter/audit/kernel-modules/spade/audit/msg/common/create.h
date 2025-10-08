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

#ifndef SPADE_AUDIT_MSG_COMMON_CREATE_H
#define SPADE_AUDIT_MSG_COMMON_CREATE_H

#include "spade/audit/msg/common/common.h"


/*
    Initialize dst with the given major, minor and patch.

    Returns:
        0    -> Success.
        -ive -> Error code.
*/
int msg_common_create_version(
    struct msg_common_version *dst,
    u8 major, u8 minor, u8 patch
);

/*
    Initialize dst with the given version.

    Returns:
        0    -> Success.
        -ive -> Error code.
*/
int msg_common_create_header(
    struct msg_common_header *dst,
    struct msg_common_version *version,
    enum msg_common_type msg_type
);

/*
    Initialize dst to zeroes.

    Returns:
        0    -> Success.
        -ive -> Error code.
*/
int msg_common_create_process(
    struct msg_common_process *dst
);

#endif // SPADE_AUDIT_MSG_COMMON_CREATE_H