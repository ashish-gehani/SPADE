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

#include "audit/kernel/arch/common/function/op.h"
#include "audit/kernel/arch/common/function/sys_accept/op.h"
#include "audit/kernel/arch/common/function/sys_accept4/op.h"
#include "audit/kernel/arch/common/function/sys_bind/op.h"
#include "audit/kernel/arch/common/function/sys_clone/op.h"
#include "audit/kernel/arch/common/function/sys_connect/op.h"
#include "audit/kernel/arch/common/function/sys_fork/op.h"
#include "audit/kernel/arch/common/function/sys_kill/op.h"
#include "audit/kernel/arch/common/function/sys_recvfrom/op.h"
#include "audit/kernel/arch/common/function/sys_recvmsg/op.h"
#include "audit/kernel/arch/common/function/sys_sendmsg/op.h"
#include "audit/kernel/arch/common/function/sys_sendto/op.h"
#include "audit/kernel/arch/common/function/sys_setns/op.h"
#include "audit/kernel/arch/common/function/sys_unshare/op.h"
#include "audit/kernel/arch/common/function/sys_vfork/op.h"
#include "audit/util/log/log.h"


/* Populated lazily by _ensure_initialized() below, since none of the 14 syscalls' ops are
 * compile-time constants now that they've all moved to arch/common. */
static const struct kernel_function_op* KERNEL_FUNCTION_OP_LIST[14];

static struct
{
    bool initialized;
} state = {
    .initialized = false,
};

static void _ensure_initialized(void)
{
    if (!state.initialized)
    {
        /* Every syscall's op is populated lazily here, via its respective hook_get() (none of them
         * are compile-time constants any more, now that all 14 have moved to arch/common). */
        size_t i = 0;
        KERNEL_FUNCTION_OP_LIST[i++] = kernel_function_sys_accept_op_get();
        KERNEL_FUNCTION_OP_LIST[i++] = kernel_function_sys_accept4_op_get();
        KERNEL_FUNCTION_OP_LIST[i++] = kernel_function_sys_bind_op_get();
        KERNEL_FUNCTION_OP_LIST[i++] = kernel_function_sys_clone_op_get();
        KERNEL_FUNCTION_OP_LIST[i++] = kernel_function_sys_connect_op_get();
        KERNEL_FUNCTION_OP_LIST[i++] = kernel_function_sys_fork_op_get();
        KERNEL_FUNCTION_OP_LIST[i++] = kernel_function_sys_kill_op_get();
        KERNEL_FUNCTION_OP_LIST[i++] = kernel_function_sys_recvfrom_op_get();
        KERNEL_FUNCTION_OP_LIST[i++] = kernel_function_sys_recvmsg_op_get();
        KERNEL_FUNCTION_OP_LIST[i++] = kernel_function_sys_sendmsg_op_get();
        KERNEL_FUNCTION_OP_LIST[i++] = kernel_function_sys_sendto_op_get();
        KERNEL_FUNCTION_OP_LIST[i++] = kernel_function_sys_setns_op_get();
        KERNEL_FUNCTION_OP_LIST[i++] = kernel_function_sys_unshare_op_get();
        KERNEL_FUNCTION_OP_LIST[i++] = kernel_function_sys_vfork_op_get();
        state.initialized = true;
    }
}

int kernel_function_op_get_list(const struct kernel_function_op*** list, size_t *len)
{
    if (!list || !len)
    {
        return -EINVAL;
    }

    _ensure_initialized();

    *list = KERNEL_FUNCTION_OP_LIST;
    *len = sizeof(KERNEL_FUNCTION_OP_LIST) / sizeof(KERNEL_FUNCTION_OP_LIST[0]);

    return 0;
}