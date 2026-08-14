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

#ifndef _SPADE_AUDIT_KERNEL_HELPER_AUDIT_LOG_H
#define _SPADE_AUDIT_KERNEL_HELPER_AUDIT_LOG_H

#include <linux/audit.h>

#include "audit/msg/common/common.h"

#define KERNEL_HELPER_AUDIT_LOG_MSG_BUF_LEN 900

/*
    Log msg using kernel function audit_log.

    Params:
        ctx     : Audit context (nullable).
        msg_h   : The msg to log. Cannot be null.

    Returns:
        0       -> Successfully logged.
        -ive    -> Error code.
*/
int kernel_helper_audit_log(struct audit_context *ctx, struct msg_common_header *msg_h);

#endif // _SPADE_AUDIT_KERNEL_HELPER_AUDIT_LOG_H