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

#ifndef _SPADE_AUDIT_CONTEXT_SYSCALL_PRINT_H
#define _SPADE_AUDIT_CONTEXT_SYSCALL_PRINT_H

#include "spade/audit/context/syscall/syscall.h"
#include "spade/util/seqbuf/seqbuf.h"

/*
    Log context syscall to seqbuf.

    Params:
        s           : Seqbuf to write to.
        context     : The context syscall to log.
*/
void context_syscall_write_to_seqbuf(struct seqbuf *s, const struct context_syscall *context);

#endif // _SPADE_AUDIT_CONTEXT_SYSCALL_PRINT_H
