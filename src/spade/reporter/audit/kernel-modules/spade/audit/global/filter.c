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

#include <linux/errno.h>
#include <linux/types.h>

#include "spade/audit/global/filter.h"
#include "spade/audit/global/global.h"
#include "spade/audit/global/function/function.h"
#include "spade/audit/global/netfilter/netfilter.h"
#include "spade/audit/global/process/process.h"


extern struct global global_state;


bool global_filter_netfilter_user_is_actionable(uid_t uid)
{
    int err;
    bool res;

    if (!global_is_auditing_started())
        return false;

    err = global_netfilter_uid_is_actionable(
        &res, &global_state.c.netfilter, uid
    );
    if (err != 0)
        return false;

    return res;
}

bool global_filter_netfilter_conntrack_info_is_actionable(
    enum ip_conntrack_info ct_info
)
{
    int err;
    bool res;

    if (!global_is_auditing_started())
        return false;

    err = global_netfilter_conntrack_info_is_actionable(
        &res, &global_state.c.netfilter, ct_info
    );
    if (err != 0)
        return false;

    return res;
}

bool global_filter_netfilter_include_ns_info(void)
{
    if (!global_is_auditing_started())
        return false;
    return global_state.c.netfilter.include_ns_info;
}

bool global_filter_netfilter_audit_hooks_on(void)
{
    if (!global_is_auditing_started())
        return false;
    return global_state.c.netfilter.audit_hooks;
}

bool global_filter_function_network_include_ns_info(void)
{
    if (!global_is_auditing_started())
        return false;
    return global_state.c.function.include_ns_info;
}

bool global_filter_function_post_execution_is_actionable(
    enum kernel_function_number func_num, bool func_success,
    pid_t pid, pid_t ppid, uid_t uid
)
{
    int err;
    bool res;

    if (!global_is_auditing_started())
        return false;

    err = global_function_post_execution_is_actionable(
        &res, &global_state.c.function, func_num, func_success
    );
    if (err != 0 || !res)
        return false;

    if (!global_process_pid_is_actionable(&global_state.c.function.m_pids, pid))
        return false;

    if (!global_process_ppid_is_actionable(&global_state.c.function.m_ppids, ppid))
        return false;

    if (!global_process_uid_is_actionable(&global_state.c.function.m_uids, uid))
        return false;

    return true;
}

bool global_filter_function_pre_execution_is_actionable(
    enum kernel_function_number func_num,
    pid_t pid, pid_t ppid, uid_t uid
)
{
    int err;
    bool res;

    if (!global_is_auditing_started())
        return false;

    err = global_function_pre_execution_is_actionable(
        &res, &global_state.c.function, func_num
    );
    if (err != 0 || !res)
        return false;

    if (!global_process_pid_is_actionable(&global_state.c.function.m_pids, pid))
        return false;

    if (!global_process_ppid_is_actionable(&global_state.c.function.m_ppids, ppid))
        return false;

    if (!global_process_uid_is_actionable(&global_state.c.function.m_uids, uid))
        return false;

    return true;
}

bool global_filter_function_number_is_actionable(
    enum kernel_function_number func_num
)
{
    int err;
    bool res;

    if (!global_is_auditing_started())
        return false;

    err = global_function_number_is_actionable(
        &res, &global_state.c.function, func_num
    );
    if (err != 0)
        return false;

    return res;
}

bool global_filter_function_success_is_actionable(
    bool func_success
)
{
    int err;
    bool res;

    if (!global_is_auditing_started())
        return false;

    err = global_function_success_is_actionable(
        &res, &global_state.c.function, func_success
    );
    if (err != 0)
        return false;

    return res;
}

bool global_filter_function_pid_is_actionable(pid_t pid)
{
    if (!global_is_auditing_started())
        return false;

    return global_process_pid_is_actionable(&global_state.c.function.m_pids, pid);
}

bool global_filter_function_ppid_is_actionable(pid_t ppid)
{
    if (!global_is_auditing_started())
        return false;

    return global_process_ppid_is_actionable(&global_state.c.function.m_ppids, ppid);
}

bool global_filter_function_uid_is_actionable(uid_t uid)
{
    if (!global_is_auditing_started())
        return false;

    return global_process_uid_is_actionable(&global_state.c.function.m_uids, uid);
}

bool global_filter_function_tgid_is_hardened(pid_t tgid)
{
    bool found;

    if (!global_is_auditing_started())
        return false;

    found = global_process_is_pid_in_array(
        &(global_state.c.function.harden.tgids.arr[0]), global_state.c.function.harden.tgids.len,
        tgid
    );

    return found;
}

bool global_filter_function_uid_is_authorized(uid_t uid)
{
    bool uid_is_in_authorized_uid_array;

    if (!global_is_auditing_started())
        return false;

    uid_is_in_authorized_uid_array = global_process_is_uid_in_array(
        &(global_state.c.function.harden.authorized_uids.arr[0]), global_state.c.function.harden.authorized_uids.len,
        uid
    );

    return uid_is_in_authorized_uid_array;
}