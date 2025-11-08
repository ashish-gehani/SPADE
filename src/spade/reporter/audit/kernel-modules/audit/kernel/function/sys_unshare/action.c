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

#include "audit/kernel/function/action.h"
#include "audit/kernel/function/sys_unshare/action.h"
#include "audit/kernel/function/sys_unshare/action/audit.h"


const struct kernel_function_action_list KERNEL_FUNCTION_SYS_UNSHARE_ACTION_LIST = {
    .pre = {
        kernel_function_action_pre_is_actionable,
        0
    },
    .post = {
        kernel_function_action_post_is_actionable,
        kernel_function_sys_unshare_action_audit_handle_post,
        0
    }
};
