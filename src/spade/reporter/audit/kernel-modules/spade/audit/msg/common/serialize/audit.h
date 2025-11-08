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

#ifndef SPADE_AUDIT_MSG_COMMON_SERIALIZE_AUDIT_H
#define SPADE_AUDIT_MSG_COMMON_SERIALIZE_AUDIT_H

#include "spade/audit/util/seqbuf/seqbuf.h"

#include "spade/audit/msg/common/common.h"

#define HEX_TASK_COMM_LEN (TASK_COMM_LEN * 2 + 1)


/*
    Write version as audit string to seqbuf.

    Returns:
        0    -> Success.
        -ive -> Error code.
*/
int msg_common_serialize_audit_msg_version(
    struct seqbuf *b, struct msg_common_version *version
);

/*
    Write header as audit string to seqbuf.

    Returns:
        0    -> Success.
        -ive -> Error code.
*/
int msg_common_serialize_audit_msg_header(
    struct seqbuf *b, struct msg_common_header *header
);

/*
    Write process as audit string to seqbuf.

    Returns:
        0    -> Success.
        -ive -> Error code.
*/
int msg_common_serialize_audit_msg_process(
    struct seqbuf *b, struct msg_common_process *process
);

#endif // SPADE_AUDIT_MSG_COMMON_SERIALIZE_AUDIT_H