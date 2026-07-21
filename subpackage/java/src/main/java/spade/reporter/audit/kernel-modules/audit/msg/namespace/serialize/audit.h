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

#ifndef SPADE_AUDIT_MSG_NAMESPACE_SERIALIZE_AUDIT_H
#define SPADE_AUDIT_MSG_NAMESPACE_SERIALIZE_AUDIT_H

#include "audit/util/seqbuf/seqbuf.h"

#include "audit/msg/namespace/namespace.h"


/*
    Write namespace as string to seqbuf.

    Returns:
        0    -> Success.
        -ive -> Error code.
*/
int msg_namespace_serialize_audit_msg(
    struct seqbuf *b, struct msg_namespace *msg
);

#endif // SPADE_AUDIT_MSG_NAMESPACE_SERIALIZE_AUDIT_H