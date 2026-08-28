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

#ifndef SPADE_AUDIT_KERNEL_FUNCTION_SYS_RECVFROM_OP_H
#define SPADE_AUDIT_KERNEL_FUNCTION_SYS_RECVFROM_OP_H


#include "audit/kernel/arch/common/function/op.h"


/*
    Get the sys_recvfrom op, ensuring its .hook field (populated via
    kernel_function_sys_recvfrom_hook_get(), not a compile-time constant) is set first.

    Returns:
        ptr     -> Pointer to the op.
*/
const struct kernel_function_op* kernel_function_sys_recvfrom_op_get(void);


#endif // SPADE_AUDIT_KERNEL_FUNCTION_SYS_RECVFROM_OP_H
