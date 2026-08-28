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

#include "audit/kernel/arch/common/function/sys_sendto/op.h"
#include "audit/kernel/arch/common/function/sys_sendto/hook.h"
#include "audit/kernel/arch/common/function/sys_sendto/action.h"


static struct kernel_function_op KERNEL_FUNCTION_SYS_SENDTO_OP;

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
        KERNEL_FUNCTION_SYS_SENDTO_OP.hook = kernel_arch_common_overridable_function_sys_sendto_hook_get();
        KERNEL_FUNCTION_SYS_SENDTO_OP.action_list = kernel_arch_common_function_sys_sendto_action_list_get();
        state.initialized = true;
    }
}

const struct kernel_function_op* kernel_arch_common_function_sys_sendto_op_get(void)
{
    _ensure_initialized();
    return &KERNEL_FUNCTION_SYS_SENDTO_OP;
}
