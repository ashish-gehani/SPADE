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

#ifndef SPADE_AUDIT_MSG_OPS_H
#define SPADE_AUDIT_MSG_OPS_H

#include "spade/audit/util/seqbuf/seqbuf.h"

#include "spade/audit/msg/common/common.h"

struct msg_ops
{
    /*
        Allocate memory for msg.

        Returns:
            ptr     -> Initialized msg.
            NULL    -> Error.
    */
    struct msg_common_header* (*kalloc)(void);

    /*
        Init msg.

        Returns:
            0    -> Success.
            -ive -> Error code.
    */
    int (*kinit)(struct msg_common_header *msg);

    /*
        Write msg as audit string to seqbuf.

        Returns:
            0    -> Success.
            -ive -> Error code.
    */
    int (*to_audit_str)(
        struct seqbuf *b, struct msg_common_header* msg
    );

};

/*
    Get the reference to msg operations of the given type.

    Returns:
        ptr     -> Success.
        NULL    -> Error.
*/
const struct msg_ops* msg_ops_get(enum msg_common_type type);

/*
    Allocate memory and initialize msg.

    Returns:
        ptr     -> Initialized msg.
        NULL    -> Error.
*/
struct msg_common_header* msg_ops_kalloc_kinit(enum msg_common_type type);

/*
    Initialize msg.

    Returns:
        0    -> Success.
        -ive -> Error code.
*/
int msg_ops_kinit(enum msg_common_type type, struct msg_common_header* msg);

/*
    Write msg as audit string to seqbuf.

    Returns:
        0    -> Success.
        -ive -> Error code.
*/
int msg_ops_to_audit_str(struct seqbuf *b, struct msg_common_header* msg);

/*
    Free msg init using msg_ops_kinit.
*/
void msg_ops_kfree(struct msg_common_header* msg);

#endif // SPADE_AUDIT_MSG_OPS_H