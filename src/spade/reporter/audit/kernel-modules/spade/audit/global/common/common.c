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

#include "spade/audit/global/common/common.h"


bool global_common_is_pid_in_array(const pid_t *arr, size_t len, pid_t needle)
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

bool global_common_is_uid_in_array(const uid_t *arr, size_t len, uid_t needle)
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

bool global_common_is_uid_loggable(struct arg_user *user, uid_t uid)
{
    bool uid_is_in_uid_array;
    if (!user)
        return false;

    uid_is_in_uid_array = global_common_is_uid_in_array(
        &(user->uids.arr[0]), user->uids.len,
        uid
    );

    if (user->uid_monitor_mode == AMM_IGNORE)
    {
        if (uid_is_in_uid_array)
        {
            return false;
        }
    }
    else if (user->uid_monitor_mode == AMM_CAPTURE)
    {
        if (!uid_is_in_uid_array)
        {
            return false;
        }
    }

    return true;
}