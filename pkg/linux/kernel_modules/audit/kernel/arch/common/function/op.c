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

#include <linux/atomic.h>

#include "audit/kernel/arch/common/function/op.h"
#include "audit/util/log/log.h"


bool kernel_arch_common_function_op_is_valid(const struct kernel_function_op* op)
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

int kernel_arch_common_function_op_get_by_func_num(const struct kernel_function_op** dst, enum kernel_function_number func_num)
{
    const char *log_id = "kernel_arch_common_function_op_get_by_func_num";
    int err;
    size_t i;
    const struct kernel_function_op_list* op_list;
    const struct kernel_function_op* op;

    if (!dst)
    {
        err = -EINVAL;
        goto exit_fail;
    }

    err = kernel_arch_common_overridable_function_op_get_list(&op_list);
    if (err == -EAGAIN)
    {
        goto exit_try_again;
    }
    if (err)
    {
        goto exit_fail;
    }

    for (i = 0; i < op_list->len; i++)
    {
        op = op_list->ops[i];

        if (!kernel_arch_common_function_op_is_valid(op))
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

exit_try_again:
    return err;

exit_success:
    return err;
}

static struct kernel_function_op_list EMPTY_OP_LIST;

static atomic_t OP_LIST_READY = ATOMIC_INIT(0);

/* Arch-specific implementations (e.g. audit/kernel/arch/x86_64/function/op.c) provide strong
 * definitions of kernel_arch_common_overridable_function_op_init_list() and
 * kernel_arch_common_overridable_function_op_get_list() that override these at link time. These weak
 * empty-list definitions exist so archs without one yet still link successfully. */
int __weak kernel_arch_common_overridable_function_op_init_list(void)
{
    atomic_set(&OP_LIST_READY, 1);
    return 0;
}

int __weak kernel_arch_common_overridable_function_op_get_list(const struct kernel_function_op_list** dst)
{
    if (!dst)
    {
        return -EINVAL;
    }

    if (!atomic_read(&OP_LIST_READY))
    {
        return -EAGAIN;
    }

    *dst = &EMPTY_OP_LIST;
    return 0;
}