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

#include <linux/errno.h>

#include "spade/audit/kernel/function/action/action.h"
#include "spade/audit/kernel/function/action/audit/audit.h"


int kernel_function_action_handle(
    struct kernel_function_action *s,
    struct kernel_function_context *sys_ctx
)
{
    if (!s || !sys_ctx)
        return -EINVAL;

    switch (s->type)
    {
        case KERNEL_FUNCTION_ACTION_TYPE_AUDIT:
            return kernel_function_action_audit_handle(sys_ctx);
        default:
            return -ENOTSUPP;
    }

    return 0;
}
