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

#ifndef SPADE_AUDIT_KERNEL_FUNCTION_OP_H
#define SPADE_AUDIT_KERNEL_FUNCTION_OP_H


#include <linux/types.h>

#include "spade/audit/kernel/function/action.h"
#include "spade/audit/kernel/function/hook.h"


#define KERNEL_FUNCTION_OP_LIST_MAX_LEN 64


struct kernel_function_op
{
    const struct kernel_function_hook *hook;
    const struct kernel_function_action_list *action_list;
};


extern const struct kernel_function_op* KERNEL_FUNCTION_OP_LIST[KERNEL_FUNCTION_OP_LIST_MAX_LEN];

extern const size_t KERNEL_FUNCTION_OP_LIST_LEN;


/*
    Check if the given arg is valid.
*/
bool kernel_function_op_is_valid(const struct kernel_function_op* op);

/*
    Find kernel function op's by function number.

    Params:
        dst         -> Set the pointer, if found.
        func_num    -> Function number to search for.

    Returns:
        -ive        -> Error code.
            -ENOENT -> No entry.
            -EINVAL -> Invalid arg or array element.
        0       -> Success.
*/
int kernel_function_op_get_by_func_num(const struct kernel_function_op** dst, enum kernel_function_number func_num);


#endif // SPADE_AUDIT_KERNEL_FUNCTION_OP_H