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

#ifndef SPADE_AUDIT_MSG_COMMON_SCM_FD_SCM_FD_H
#define SPADE_AUDIT_MSG_COMMON_SCM_FD_SCM_FD_H

#include <net/scm.h>

#include "audit/msg/common/common.h"

struct msg_scm_fd
{
    struct msg_common_header header;
    pid_t pid;
    int syscall_number;
    int fds[SCM_MAX_FD];
    int fds_count;
};

#endif // SPADE_AUDIT_MSG_COMMON_SCM_FD_SCM_FD_H
