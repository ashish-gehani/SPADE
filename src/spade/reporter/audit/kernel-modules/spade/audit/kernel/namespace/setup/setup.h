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

#ifndef SPADE_AUDIT_KERNEL_NAMESPACE_SETUP_SETUP_H
#define SPADE_AUDIT_KERNEL_NAMESPACE_SETUP_SETUP_H

#include "spade/audit/state/syscall/namespace/namespace.h"

/*
    Setup required proc_ns_operations structs in state.

    Params:
        s       : The state to setup.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int kernel_namespace_setup_do(struct state_syscall_namespace *s);

/*
    Undo setup.

    Params:
        s       : The state to setup.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int kernel_namespace_setup_undo(struct state_syscall_namespace *s);


#endif // SPADE_AUDIT_KERNEL_NAMESPACE_SETUP_SETUP_H