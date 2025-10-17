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

#ifndef _SPADE_AUDIT_ARG_CONSTANT_H
#define _SPADE_AUDIT_ARG_CONSTANT_H

#define __STRINGIFY(x) #x
#define STRINGIFY(x)   __STRINGIFY(x)

/* Identifiers */
#define ARG_CONSTANT_NAME_NF_USE_USER        nf_handle_user
#define ARG_CONSTANT_NAME_NF_AUDIT_HOOKS     nf_hooks
#define ARG_CONSTANT_NAME_NF_MONITOR_CT      nf_hooks_log_all_ct
#define ARG_CONSTANT_NAME_MONITOR_SYSCALLS   log_syscalls
#define ARG_CONSTANT_NAME_NETWORK_IO         net_io
#define ARG_CONSTANT_NAME_INCLUDE_NS_INFO    namespaces
#define ARG_CONSTANT_NAME_IGNORE_PIDS        ignore_pids
#define ARG_CONSTANT_NAME_IGNORE_PPIDS       ignore_ppids
#define ARG_CONSTANT_NAME_UID_MONITOR_MODE   uid_trace_mode
#define ARG_CONSTANT_NAME_UIDS               uids

/* String forms */
#define ARG_CONSTANT_NAME_NF_USE_USER_STR                STRINGIFY(ARG_CONSTANT_NAME_NF_USE_USER)
#define ARG_CONSTANT_NAME_NF_AUDIT_HOOKS_STR             STRINGIFY(ARG_CONSTANT_NAME_NF_AUDIT_HOOKS)
#define ARG_CONSTANT_NAME_NF_MONITOR_CT_STR              STRINGIFY(ARG_CONSTANT_NAME_NF_MONITOR_CT)
#define ARG_CONSTANT_NAME_MONITOR_SYSCALLS_STR           STRINGIFY(ARG_CONSTANT_NAME_MONITOR_SYSCALLS)
#define ARG_CONSTANT_NAME_NETWORK_IO_STR                 STRINGIFY(ARG_CONSTANT_NAME_NETWORK_IO)
#define ARG_CONSTANT_NAME_INCLUDE_NS_INFO_STR            STRINGIFY(ARG_CONSTANT_NAME_INCLUDE_NS_INFO)
#define ARG_CONSTANT_NAME_IGNORE_PIDS_STR                STRINGIFY(ARG_CONSTANT_NAME_IGNORE_PIDS)
#define ARG_CONSTANT_NAME_IGNORE_PPIDS_STR               STRINGIFY(ARG_CONSTANT_NAME_IGNORE_PPIDS)
#define ARG_CONSTANT_NAME_UID_MONITOR_MODE_STR           STRINGIFY(ARG_CONSTANT_NAME_UID_MONITOR_MODE)
#define ARG_CONSTANT_NAME_UIDS_STR                       STRINGIFY(ARG_CONSTANT_NAME_UIDS)

/* Descriptions */
#define ARG_CONSTANT_DESC_NF_USE_USER        "In netfilter hooks, filter packet logging based on user criteria. Default: 0. Options: 0 (log packets for all user), 1 (log packets based on user criteria)"
#define ARG_CONSTANT_DESC_NF_AUDIT_HOOKS     "Audit netfilter hooks. Default: 0. Options: 0 (Audit hooks), 1 (Do not audit hooks)"
#define ARG_CONSTANT_DESC_NF_MONITOR_CT      "Monitor packets by connection state. Default: all. Options: -1 (Monitor packets with all connection states), 0 (Monitor packets with only new connection states)"
#define ARG_CONSTANT_DESC_MONITOR_SYSCALLS   "Monitor system calls by result. Default: 1. Options: -1 (Monitor syscalls with any result), 0 (Monitor failed syscalls), 1 (Monitor successful syscalls)"
#define ARG_CONSTANT_DESC_NETWORK_IO         "Enable network IO monitoring. Default: 0. Options: 0 (Do not monitor network IO syscalls), 1 (Monitor network IO syscalls)"
#define ARG_CONSTANT_DESC_INCLUDE_NS_INFO    "Include namespace information in monitoring. Default: 0. Options: 0 (Do not include namespace events and info in msgs), 1 (Include namespace events and info in msgs)"
#define ARG_CONSTANT_DESC_IGNORE_PIDS        "List of process ids to ignore. Default: empty list"
#define ARG_CONSTANT_DESC_IGNORE_PPIDS       "List of parent process ids to ignore. Default: empty list"
#define ARG_CONSTANT_DESC_UID_MONITOR_MODE   "Monitoring mode for the list of user ids. Default: 1. Options: 0 (Capture the specified list of user ids), 1 (Ignore the specified list of user ids)"
#define ARG_CONSTANT_DESC_UIDS               "List of user ids to ignore. Default: empty list"

#endif // _SPADE_AUDIT_ARG_CONSTANT_H

