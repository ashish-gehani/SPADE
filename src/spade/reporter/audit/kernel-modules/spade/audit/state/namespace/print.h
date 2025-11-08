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

#ifndef SPADE_AUDIT_STATE_NAMESPACE_PRINT_H
#define SPADE_AUDIT_STATE_NAMESPACE_PRINT_H

#include "spade/audit/state/namespace/namespace.h"
#include "spade/audit/util/seqbuf/seqbuf.h"


/*
    Write the state_namespace struct to a seqbuf.

    Params:
        b       : The seqbuf to write to.
        state   : The state_namespace struct to print.
*/
void state_namespace_write_to_seqbuf(struct seqbuf *b, const struct state_namespace *state);

/*
    Print the state_namespace struct to kernel log.

    Params:
        state   : The state_namespace struct to print.
*/
void state_namespace_print(const struct state_namespace *state);

#endif // SPADE_AUDIT_STATE_NAMESPACE_PRINT_H
