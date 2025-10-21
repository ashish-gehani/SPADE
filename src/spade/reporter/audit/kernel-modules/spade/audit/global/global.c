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

#include "spade/audit/arg/print.h"
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
    atomic_t inited;
    atomic_t auditing_started;
} g = {
    .s = {0},
    .c = {0},
    .inited = ATOMIC_INIT(0),
    .auditing_started = ATOMIC_INIT(0)
};

static bool _is_inited(void)
{
    return (atomic_read(&g.inited) == 1);
}

/*
    Public functions.
*/

int global_init(bool dry_run)
{
    int err;

    if (atomic_cmpxchg(&g.inited, 0, 1) == 1)
        return -EALREADY;

    err = state_init(&g.s, dry_run);
    if (err != 0)
        goto undo_cmpxchg_and_exit;

    state_print(&g.s);

    goto exit; // success... so go to exit without undo.

undo_cmpxchg_and_exit:
    atomic_cmpxchg(&g.inited, 1, 0);

exit:
    return err;
}

bool global_is_initialized(void)
{
    return _is_inited();
}

int global_deinit(void)
{
    int err = 0;
    bool dst;

    if (atomic_cmpxchg(&g.inited, 1, 0) == 0)
        return -EALREADY;

    err = state_is_initialized(&dst, &g.s);
    if (err == 0 && dst == true)
        err = state_deinit(&g.s);

    return err;
}

static void _log_auditing_state(const char *log_id, bool started)
{
    util_log_info(log_id, "{started=%s}", (started ? "true" : "false"));
}

int global_auditing_start(const struct arg *arg)
{
    int err = 0;

    if (!_is_inited() || !arg)
        return -EINVAL;

    if (atomic_cmpxchg(&g.auditing_started, 0, 1) == 1)
        return -EALREADY;

    err = context_init(&g.c, arg);
    if (err != 0)
        goto undo_cmpxchg_and_exit;

    arg_print(arg);
    context_print(&g.c);
    _log_auditing_state("global_auditing", true);

    goto exit; // success... so go to exit without undo.

undo_cmpxchg_and_exit:
    atomic_cmpxchg(&g.auditing_started, 1, 0);

exit:
    return err;
}

int global_auditing_stop(void)
{
    int err = 0;
    bool dst;

    if (!_is_inited())
        return -EINVAL;

    if (atomic_cmpxchg(&g.auditing_started, 1, 0) == 0)
        return -EALREADY;

    err = context_is_initialized(&dst, &g.c);
    if (err == 0 && dst == true)
        err = context_deinit(&g.c);

    _log_auditing_state("global_auditing", false);

    return err;
}

static bool _is_auditing_started(void)
{
    return (atomic_read(&g.auditing_started) == 1);
}

bool global_is_auditing_started(void)
{
    return _is_auditing_started();
}

//

bool global_is_netfilter_loggable_by_user(uid_t uid)
{
    int err;
    bool res;

    if (!_is_auditing_started())
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

    if (!_is_auditing_started())
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
    if (!_is_auditing_started())
        return false;
    return g.c.netfilter.include_ns_info;
}

bool global_is_netfilter_audit_hooks_on(void)
{
    if (!_is_auditing_started())
        return false;
    return g.c.netfilter.audit_hooks;
}

bool global_is_network_logging_ns_info(void)
{
    if (!_is_auditing_started())
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

    if (!_is_auditing_started())
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

    if (!_is_auditing_started())
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

    if (!_is_auditing_started())
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

    if (!_is_auditing_started())
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

    if (!_is_auditing_started())
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

    if (!_is_auditing_started())
        return false;

    err = global_syscall_is_loggable_by_uid(
        &res, &g.c.syscall, uid
    );
    if (err != 0)
        return false;

    return res;
}