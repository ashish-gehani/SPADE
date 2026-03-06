/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International

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

#ifndef _SPADE_AUDIT_KERNEL_HELPER_UNIX_H
#define _SPADE_AUDIT_KERNEL_HELPER_UNIX_H


#include <linux/audit.h>
#include <linux/socket.h>

#include "audit/msg/scm_fd/scm_fd.h"
#include "audit/kernel/function/number.h"

/*
    Copy the msghdr and its control buffer from userspace, find the SCM_RIGHTS
    cmsghdr, and populate dst with the fd array and count.

    Returns:
        0    -> Success (SCM_RIGHTS found and dst populated).
        -ive -> Error code (e.g. -ENOENT if no SCM_RIGHTS cmsghdr found).
*/
int kernel_helper_unix_get_scm_rights_fd(struct msghdr __user *mh, struct msg_scm_fd *dst);

/*
    Get SCM_RIGHTS fds from mh and emit an audit log record via kernel_helper_audit_log.
    Populates pid from the current task and converts func_num to the syscall number.

    Returns:
        0    -> Successfully logged.
        -ive -> Error code.
*/
int kernel_helper_unix_audit_log_scm_fd_msg(
    struct msghdr __user *mh,
    struct audit_context *audit_ctx,
    enum kernel_function_number func_num
);


#endif // _SPADE_AUDIT_KERNEL_HELPER_UNIX_H