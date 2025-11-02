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

#ifndef _SPADE_AUDIT_GLOBAL_FUNCTION_FUNCTION_H
#define _SPADE_AUDIT_GLOBAL_FUNCTION_FUNCTION_H

#include <linux/types.h>

#include "spade/audit/context/function/function.h"
#include "spade/audit/kernel/function/number.h"


/*
    Is the function actionable based on the given information.

    Params:
        dst             : The result pointer.
        ctx             : Function context.
        func_num        : The function number assigned.

    Returns:
        0    -> dst is successfully set.
        -ive -> Error code and dst cannot be used.
*/
int global_function_number_is_actionable(
    bool *dst, struct context_function *ctx, enum kernel_function_number func_num
);

/*
    Is the function actionable based on the given information.

    Params:
        dst             : The result pointer.
        ctx             : Function context.
        func_success    : Success of the function.

    Returns:
        0    -> dst is successfully set.
        -ive -> Error code and dst cannot be used.
*/
int global_function_success_is_actionable(
    bool *dst, struct context_function *ctx, bool func_success
);

/*
    Is the function pre-execution actionable based on the given information.

    Params:
        dst             : The result pointer.
        ctx             : Function context.
        func_num        : The function number assigned.

    Returns:
        0    -> dst is successfully set.
        -ive -> Error code and dst cannot be used.
*/
int global_function_pre_execution_is_actionable(
    bool *dst,
    struct context_function *ctx,
    enum kernel_function_number func_num
);

/*
    Is the function post-execution actionable based on the given information.

    Params:
        dst             : The result pointer.
        ctx             : Function context.
        func_num        : The function number assigned.
        func_success    : Success of the function.

    Returns:
        0    -> dst is successfully set.
        -ive -> Error code and dst cannot be used.
*/
int global_function_post_execution_is_actionable(
    bool *dst,
    struct context_function *ctx,
    enum kernel_function_number func_num, bool func_success
);

#endif // _SPADE_AUDIT_GLOBAL_FUNCTION_FUNCTION_H