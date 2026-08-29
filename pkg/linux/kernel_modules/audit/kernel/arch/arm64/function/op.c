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
#include <linux/errno.h>
#include <linux/list.h>

#include "audit/kernel/arch/common/function/op.h"
#include "audit/kernel/arch/common/function/sys_accept/op.h"
#include "audit/kernel/arch/common/function/sys_accept4/op.h"
#include "audit/kernel/arch/common/function/sys_bind/op.h"
#include "audit/kernel/arch/common/function/sys_clone/op.h"
#include "audit/kernel/arch/common/function/sys_connect/op.h"
#include "audit/kernel/arch/common/function/sys_kill/op.h"
#include "audit/kernel/arch/common/function/sys_recvfrom/op.h"
#include "audit/kernel/arch/common/function/sys_recvmsg/op.h"
#include "audit/kernel/arch/common/function/sys_sendmsg/op.h"
#include "audit/kernel/arch/common/function/sys_sendto/op.h"
#include "audit/kernel/arch/common/function/sys_setns/op.h"
#include "audit/kernel/arch/common/function/sys_unshare/op.h"
#include "audit/util/log/log.h"


/* Populated by kernel_arch_common_overridable_function_op_init_list() below, since none of these
 * syscalls' ops are compile-time constants now that they've all moved to arch/common.
 *
 * sys_fork / sys_vfork are excluded here: aarch64's native 64-bit ABI has no SYS_fork / SYS_vfork
 * syscall numbers (glibc's fork()/vfork() are implemented via clone() on this architecture), so
 * they are unreachable from userspace and have no arm64 hook implementation. */
static struct kernel_function_op_list OP_LIST;

/* Published (via atomic_set, after OP_LIST is fully populated) once init_list() has completed, so
 * get_list() never hands back a partially populated OP_LIST. */
static atomic_t OP_LIST_READY = ATOMIC_INIT(0);

int kernel_arch_common_overridable_function_op_init_list(void)
{
    if (atomic_read(&OP_LIST_READY))
    {
        return 0;
    }

    /* Every syscall's op is populated here, via its respective hook_get() (none of them are
     * compile-time constants any more, now that they've all moved to arch/common). */
    size_t i = 0;
    OP_LIST.ops[i++] = kernel_arch_common_function_sys_accept_op_get();
    OP_LIST.ops[i++] = kernel_arch_common_function_sys_accept4_op_get();
    OP_LIST.ops[i++] = kernel_arch_common_function_sys_bind_op_get();
    OP_LIST.ops[i++] = kernel_arch_common_function_sys_clone_op_get();
    OP_LIST.ops[i++] = kernel_arch_common_function_sys_connect_op_get();
    OP_LIST.ops[i++] = kernel_arch_common_function_sys_kill_op_get();
    OP_LIST.ops[i++] = kernel_arch_common_function_sys_recvfrom_op_get();
    OP_LIST.ops[i++] = kernel_arch_common_function_sys_recvmsg_op_get();
    OP_LIST.ops[i++] = kernel_arch_common_function_sys_sendmsg_op_get();
    OP_LIST.ops[i++] = kernel_arch_common_function_sys_sendto_op_get();
    OP_LIST.ops[i++] = kernel_arch_common_function_sys_setns_op_get();
    OP_LIST.ops[i++] = kernel_arch_common_function_sys_unshare_op_get();
    OP_LIST.len = i;

    atomic_set(&OP_LIST_READY, 1);
    return 0;
}

int kernel_arch_common_overridable_function_op_get_list(const struct kernel_function_op_list** dst)
{
    if (!dst)
    {
        return -EINVAL;
    }

    if (!atomic_read(&OP_LIST_READY))
    {
        return -EAGAIN;
    }

    *dst = &OP_LIST;
    return 0;
}
