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

    May sleep: audit_log waits for room when the audit backlog is full. Call it
    only from process context, e.g. a syscall hook.

    Params:
        ctx     : Audit context (nullable).
        msg_h   : The msg to log. Cannot be null.

    Returns:
        0       -> Successfully logged.
        -ive    -> Error code.
*/
int kernel_helper_audit_log(struct audit_context *ctx, struct msg_common_header *msg_h);

/*
    Like kernel_helper_audit_log, but never sleeps. Use it where sleeping is
    not allowed, e.g. in a netfilter hook, which runs in an RCU read-side
    critical section and, for received packets, in softirq context. When the
    audit backlog is full, the kernel drops the record and counts it as lost
    instead of waiting.

    Params and returns as for kernel_helper_audit_log.
*/
int kernel_helper_audit_log_atomic(struct audit_context *ctx, struct msg_common_header *msg_h);

#endif // _SPADE_AUDIT_KERNEL_HELPER_AUDIT_LOG_H