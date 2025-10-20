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

#include "spade/audit/global/global.h"
#include "spade/audit/global/syscall/syscall.h"
#include "spade/audit/global/netfilter/netfilter.h"
#include "spade/audit/context/print.h"
#include "spade/util/log/log.h"
#include "spade/audit/state/print.h"


static struct global
{
    struct state s;
    struct context c;
    bool auditing_started;
} g = {
    .s = {0},
    .c = {0},
    .auditing_started = false
};

static bool _is_state_and_context_inited(void)
{
    int err = 0;
    bool dst;

    err = context_is_initialized(&dst, &g.c);
    if (err != 0 || dst == false)
        return false;

    err = state_is_initialized(&dst, &g.s);
    if (err != 0 || dst == false)
        return false;

    return true;
}

/*
    Public functions.
*/

// todo print state and context
int global_init(struct arg *arg)
{
    int err;

    if (!arg)
        return -EINVAL;

    err = state_init(&g.s, arg->dry_run);
    if (err != 0)
        return err;

    err = context_init(&g.c, arg);
    if (err != 0)
    {
        state_deinit(&g.s);
        return err;
    }

    return err;
}

bool global_is_initialized(void)
{
    return _is_state_and_context_inited();
}

int global_deinit(void)
{
    int err = 0;
    bool dst;

    err = context_is_initialized(&dst, &g.c);
    if (err == 0 && dst == true)
        err = context_deinit(&g.c);

    err = state_is_initialized(&dst, &g.s);
    if (err == 0 && dst == true)
        err = state_deinit(&g.s);

    return err;
}

static void _log_started(const char *log_id)
{
    util_log_info(log_id, "{started=%s}", (g.auditing_started ? "true" : "false"));
}

int global_auditing_start(void)
{
    if (!_is_state_and_context_inited())
        return -EINVAL;

    if (g.auditing_started)
        return -EALREADY;

    g.auditing_started = true;
    _log_started("global_auditing_start");
    return 0;
}

int global_auditing_stop(void)
{
    if (!_is_state_and_context_inited())
        return -EINVAL;

    if (!g.auditing_started)
        return -EALREADY;

    g.auditing_started = false;
    _log_started("global_auditing_stop");
    return 0;
}

bool global_is_auditing_started(void)
{
    if (!_is_state_and_context_inited())
        return false;

    return g.auditing_started;
}

//

bool global_is_netfilter_loggable_by_user(uid_t uid)
{
    int err;
    bool res;

    if (!global_is_auditing_started())
        return false;

    err = global_netfilter_is_loggable_by_user(
        &res, &g.c.netfilter, uid
    );
    if (err != 0)
        return false;

    return res;
}

bool global_is_netfilter_loggable_by_conntrack_info(
    enum ip_conntrack_info ct_info
)
{
    int err;
    bool res;

    if (!global_is_auditing_started())
        return false;

    err = global_netfilter_event_is_loggable_by_conntrack_info(
        &res, &g.c.netfilter, ct_info
    );
    if (err != 0)
        return false;

    return res;
}

bool global_is_netfilter_logging_ns_info(void)
{
    if (!global_is_auditing_started())
        return false;
    return g.c.netfilter.include_ns_info;
}

bool global_is_netfilter_audit_hooks_on(void)
{
    if (!global_is_auditing_started())
        return false;
    return g.c.netfilter.audit_hooks;
}

bool global_is_network_logging_ns_info(void)
{
    if (!global_is_auditing_started())
        return false;
    return g.c.syscall.include_ns_info;
}

bool global_is_syscall_loggable(
    int sys_num, bool sys_success,
    pid_t pid, pid_t ppid, uid_t uid
)
{
    int err;
    bool res;

    if (!global_is_auditing_started())
        return false;

    err = global_syscall_is_loggable(
        &res, &g.c.syscall, sys_num, sys_success, pid, ppid, uid
    );
    if (err != 0)
        return false;

    return res;
}

bool global_is_syscall_loggable_by_sys_num(int sys_num)
{
    int err;
    bool res;

    if (!global_is_auditing_started())
        return false;

    err = global_syscall_is_loggable_by_sys_num(
        &res, &g.c.syscall, sys_num
    );
    if (err != 0)
        return false;

    return res;
}

bool global_is_syscall_loggable_by_sys_success(bool sys_success)
{
    int err;
    bool res;

    if (!global_is_auditing_started())
        return false;

    err = global_syscall_is_loggable_by_sys_success(
        &res, &g.c.syscall, sys_success
    );
    if (err != 0)
        return false;

    return res;
}

bool global_is_syscall_loggable_by_pid(pid_t pid)
{
    int err;
    bool res;

    if (!global_is_auditing_started())
        return false;

    err = global_syscall_is_loggable_by_pid(
        &res, &g.c.syscall, pid
    );
    if (err != 0)
        return false;

    return res;
}

bool global_is_syscall_loggable_by_ppid(pid_t ppid)
{
    int err;
    bool res;

    if (!global_is_auditing_started())
        return false;

    err = global_syscall_is_loggable_by_ppid(
        &res, &g.c.syscall, ppid
    );
    if (err != 0)
        return false;

    return res;
}

bool global_is_syscall_loggable_by_uid(uid_t uid)
{
    int err;
    bool res;

    if (!global_is_auditing_started())
        return false;

    err = global_syscall_is_loggable_by_uid(
        &res, &g.c.syscall, uid
    );
    if (err != 0)
        return false;

    return res;
}