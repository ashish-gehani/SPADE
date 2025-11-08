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

#include "audit/global/process/process.h"


bool global_process_is_pid_in_array(const pid_t *arr, size_t len, pid_t needle)
{
    size_t i;
    
    if (!arr)
        return false;

    for (i = 0; i < len; i++)
    {
        if (arr[i] == needle)
            return true;
    }
    return false;
}

bool global_process_is_uid_in_array(const uid_t *arr, size_t len, uid_t needle)
{
    size_t i;

    if (!arr)
        return false;

    for (i = 0; i < len; i++)
    {
        if (arr[i] == needle)
            return true;
    }
    return false;
}

bool global_process_uid_is_actionable(struct type_monitor_user *m_user, uid_t uid)
{
    bool uid_is_in_uid_array;
    if (!m_user)
        return false;

    uid_is_in_uid_array = global_process_is_uid_in_array(
        &(m_user->uids.arr[0]), m_user->uids.len,
        uid
    );

    if (m_user->m_mode == TMM_IGNORE)
    {
        if (uid_is_in_uid_array)
        {
            return false;
        }
    }
    else if (m_user->m_mode == TMM_CAPTURE)
    {
        if (!uid_is_in_uid_array)
        {
            return false;
        }
    }

    return true;
}

bool global_process_pid_is_actionable(struct type_monitor_pid *m_pid, pid_t pid)
{
    bool pid_is_in_pid_array;
    if (!m_pid)
        return false;

    pid_is_in_pid_array = global_process_is_pid_in_array(
        &(m_pid->pids.arr[0]), m_pid->pids.len,
        pid
    );

    if (m_pid->m_mode == TMM_IGNORE)
    {
        if (pid_is_in_pid_array)
        {
            return false;
        }
    }
    else if (m_pid->m_mode == TMM_CAPTURE)
    {
        if (!pid_is_in_pid_array)
        {
            return false;
        }
    }

    return true;
}

bool global_process_ppid_is_actionable(struct type_monitor_ppid *m_ppid, pid_t ppid)
{
    bool ppid_is_in_ppid_array;
    if (!m_ppid)
        return false;

    ppid_is_in_ppid_array = global_process_is_pid_in_array(
        &(m_ppid->ppids.arr[0]), m_ppid->ppids.len,
        ppid
    );

    if (m_ppid->m_mode == TMM_IGNORE)
    {
        if (ppid_is_in_ppid_array)
        {
            return false;
        }
    }
    else if (m_ppid->m_mode == TMM_CAPTURE)
    {
        if (!ppid_is_in_ppid_array)
        {
            return false;
        }
    }

    return true;
}