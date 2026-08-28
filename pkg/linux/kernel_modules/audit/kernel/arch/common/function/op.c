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

#include "audit/kernel/arch/common/function/op.h"
#include "audit/util/log/log.h"


bool kernel_function_op_is_valid(const struct kernel_function_op* op)
{
    return (
        op
        && (
            op->hook
            && op->hook->get_hook_func
            && op->hook->get_name
            && op->hook->get_num
            && op->hook->get_orig_func_ptr
        )
        && op->action_list
    );
}

int kernel_function_op_get_by_func_num(const struct kernel_function_op** dst, enum kernel_function_number func_num)
{
    const char *log_id = "kernel_function_op_get_by_func_num";
    int err;
    int i;
    const struct kernel_function_op** list;
    size_t len;
    const struct kernel_function_op* op;

    if (!dst)
    {
        err = -EINVAL;
        goto exit_fail;
    }

    err = kernel_function_op_get_list(&list, &len);
    if (err != 0)
    {
        goto exit_fail;
    }

    for (i = 0; i < len; i++)
    {
        op = list[i];

        if (!kernel_function_op_is_valid(op))
        {
            continue;
        }

        if (op->hook->get_num() == func_num)
        {
            *dst = op;
            err = 0;
            goto exit_success;
        }
    }

    err = -ENOENT;
    goto exit_fail;

exit_fail:
    util_log_debug(log_id, "Failed to find kernel function entry with func_num=%d. Err=%d.", func_num, err);
    return err;

exit_success:
    return err;
}

/* Arch-specific implementations (e.g. audit/kernel/arch/x86_64/function/op.c) provide a strong
 * definition of kernel_function_op_get_list() that overrides this one at link time. This weak
 * empty-list definition exists so archs without one yet still link successfully. */
int __weak kernel_function_op_get_list(const struct kernel_function_op*** list, size_t *len)
{
    if (!list || !len)
    {
        return -EINVAL;
    }

    *list = NULL;
    *len = 0;

    return 0;
}