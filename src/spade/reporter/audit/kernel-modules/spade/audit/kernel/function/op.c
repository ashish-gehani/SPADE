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

#include <linux/list.h>

#include "spade/audit/kernel/function/op.h"
#include "spade/audit/kernel/function/sys_accept/op.h"
#include "spade/audit/kernel/function/sys_accept4/op.h"
#include "spade/audit/kernel/function/sys_bind/op.h"
#include "spade/audit/kernel/function/sys_clone/op.h"
#include "spade/audit/kernel/function/sys_connect/op.h"
#include "spade/audit/kernel/function/sys_fork/op.h"
#include "spade/audit/kernel/function/sys_kill/op.h"
#include "spade/audit/kernel/function/sys_recvfrom/op.h"
#include "spade/audit/kernel/function/sys_recvmsg/op.h"
#include "spade/audit/kernel/function/sys_sendmsg/op.h"
#include "spade/audit/kernel/function/sys_sendto/op.h"
#include "spade/audit/kernel/function/sys_setns/op.h"
#include "spade/audit/kernel/function/sys_unshare/op.h"
#include "spade/audit/kernel/function/sys_vfork/op.h"
#include "spade/util/log/log.h"


const struct kernel_function_op* KERNEL_FUNCTION_OP_LIST[] = {
    &KERNEL_FUNCTION_SYS_ACCEPT_OP,
    &KERNEL_FUNCTION_SYS_ACCEPT4_OP,
    &KERNEL_FUNCTION_SYS_BIND_OP,
    &KERNEL_FUNCTION_SYS_CLONE_OP,
    &KERNEL_FUNCTION_SYS_CONNECT_OP,
    &KERNEL_FUNCTION_SYS_FORK_OP,
    &KERNEL_FUNCTION_SYS_KILL_OP,
    &KERNEL_FUNCTION_SYS_RECVFROM_OP,
    &KERNEL_FUNCTION_SYS_RECVMSG_OP,
    &KERNEL_FUNCTION_SYS_SENDMSG_OP,
    &KERNEL_FUNCTION_SYS_SENDTO_OP,
    &KERNEL_FUNCTION_SYS_SETNS_OP,
    &KERNEL_FUNCTION_SYS_UNSHARE_OP,
    &KERNEL_FUNCTION_SYS_VFORK_OP
};
const size_t KERNEL_FUNCTION_OP_LIST_LEN = sizeof(KERNEL_FUNCTION_OP_LIST) / sizeof(KERNEL_FUNCTION_OP_LIST[0]);


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
    const struct kernel_function_op* op;

    if (!dst)
    {
        err = -EINVAL;
        goto exit_fail;
    }

    for (i = 0; i < KERNEL_FUNCTION_OP_LIST_LEN; i++)
    {
        op = KERNEL_FUNCTION_OP_LIST[i];

        if (!kernel_function_op_is_valid(op))
        {
            err = -EINVAL;
            goto exit_fail;
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