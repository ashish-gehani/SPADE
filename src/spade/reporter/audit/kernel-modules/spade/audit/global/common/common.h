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

#ifndef _SPADE_AUDIT_GLOBAL_COMMON_H
#define _SPADE_AUDIT_GLOBAL_COMMON_H

#include <linux/types.h>

#include "spade/audit/arg/arg.h"

/*
    Does pid exist in the array?
*/
bool global_common_is_pid_in_array(const pid_t *arr, size_t len, pid_t needle);

/*
    Does uid exist in the array?
*/
bool global_common_is_uid_in_array(const uid_t *arr, size_t len, uid_t needle);

/*
    Does 'uid' match the criteria in 'user'.
*/
bool global_common_is_uid_loggable(struct arg_user *user, uid_t uid);

#endif // _SPADE_AUDIT_GLOBAL_COMMON_H