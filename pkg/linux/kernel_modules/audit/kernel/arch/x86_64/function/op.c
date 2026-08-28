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
#include "audit/kernel/arch/x86_64/function/sys_unshare/op.h"
#include "audit/kernel/arch/x86_64/function/sys_vfork/op.h"
#include "audit/util/log/log.h"


const struct kernel_function_op* KERNEL_FUNCTION_OP_LIST[] = {
    NULL, /* sys_accept - not a compile-time constant; populated lazily below via kernel_function_sys_accept_op_get() */
    NULL, /* sys_accept4 - not a compile-time constant; populated lazily below via kernel_function_sys_accept4_op_get() */
    NULL, /* sys_bind - not a compile-time constant; populated lazily below via kernel_function_sys_bind_op_get() */
    NULL, /* sys_clone - not a compile-time constant; populated lazily below via kernel_function_sys_clone_op_get() */
    NULL, /* sys_connect - not a compile-time constant; populated lazily below via kernel_function_sys_connect_op_get() */
    NULL, /* sys_fork - not a compile-time constant; populated lazily below via kernel_function_sys_fork_op_get() */
    NULL, /* sys_kill - not a compile-time constant; populated lazily below via kernel_function_sys_kill_op_get() */
    NULL, /* sys_recvfrom - not a compile-time constant; populated lazily below via kernel_function_sys_recvfrom_op_get() */
    NULL, /* sys_recvmsg - not a compile-time constant; populated lazily below via kernel_function_sys_recvmsg_op_get() */
    NULL, /* sys_sendmsg - not a compile-time constant; populated lazily below via kernel_function_sys_sendmsg_op_get() */
    NULL, /* sys_sendto - not a compile-time constant; populated lazily below via kernel_function_sys_sendto_op_get() */
    NULL, /* sys_setns - not a compile-time constant; populated lazily below via kernel_function_sys_setns_op_get() */
    &KERNEL_FUNCTION_SYS_UNSHARE_OP,
    &KERNEL_FUNCTION_SYS_VFORK_OP
};
const size_t KERNEL_FUNCTION_OP_LIST_LEN = sizeof(KERNEL_FUNCTION_OP_LIST) / sizeof(KERNEL_FUNCTION_OP_LIST[0]);


int kernel_function_op_get_list(const struct kernel_function_op*** list, size_t *len)
{
    if (!list || !len)
    {
        return -EINVAL;
    }

    /* sys_accept's, sys_accept4's, sys_bind's, sys_clone's, sys_connect's, sys_fork's,
     * sys_kill's, sys_recvfrom's, sys_recvmsg's, sys_sendmsg's, sys_sendto's, and sys_setns's
     * ops aren't compile-time constants (their .hook is populated at runtime via their
     * respective hook_get()), so their list slots are filled in here instead. */
    KERNEL_FUNCTION_OP_LIST[0] = kernel_function_sys_accept_op_get();
    KERNEL_FUNCTION_OP_LIST[1] = kernel_function_sys_accept4_op_get();
    KERNEL_FUNCTION_OP_LIST[2] = kernel_function_sys_bind_op_get();
    KERNEL_FUNCTION_OP_LIST[3] = kernel_function_sys_clone_op_get();
    KERNEL_FUNCTION_OP_LIST[4] = kernel_function_sys_connect_op_get();
    KERNEL_FUNCTION_OP_LIST[5] = kernel_function_sys_fork_op_get();
    KERNEL_FUNCTION_OP_LIST[6] = kernel_function_sys_kill_op_get();
    KERNEL_FUNCTION_OP_LIST[7] = kernel_function_sys_recvfrom_op_get();
    KERNEL_FUNCTION_OP_LIST[8] = kernel_function_sys_recvmsg_op_get();
    KERNEL_FUNCTION_OP_LIST[9] = kernel_function_sys_sendmsg_op_get();
    KERNEL_FUNCTION_OP_LIST[10] = kernel_function_sys_sendto_op_get();
    KERNEL_FUNCTION_OP_LIST[11] = kernel_function_sys_setns_op_get();

    *list = KERNEL_FUNCTION_OP_LIST;
    *len = KERNEL_FUNCTION_OP_LIST_LEN;

    return 0;
}