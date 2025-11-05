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

#ifndef _SPADE_AUDIT_GLOBAL_PROCESS_PROCESS_H
#define _SPADE_AUDIT_GLOBAL_PROCESS_PROCESS_H

#include <linux/types.h>

#include "spade/audit/arg/arg.h"

/*
    Does 'pid' match the criteria in 'm_pid'.
*/
bool global_process_pid_is_actionable(struct type_monitor_pid *m_pid, pid_t pid);

/*
    Does 'ppid' match the criteria in 'm_ppid'.
*/
bool global_process_ppid_is_actionable(struct type_monitor_ppid *m_ppid, pid_t ppid);

/*
    Does 'uid' match the criteria in 'm_user'.
*/
bool global_process_uid_is_actionable(struct type_monitor_user *m_user, uid_t uid);

/*
    Does pid exist in pid array.
*/
bool global_process_is_pid_in_array(const pid_t *arr, size_t len, pid_t needle);

#endif // _SPADE_AUDIT_GLOBAL_PROCESS_PROCESS_H