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
    return global_is_state_initialized() && global_is_context_initialized();
}

/*
    Public functions.
*/

int global_state_init(void)
{
    int err;
    err = state_init(&g.s);
    state_print(&g.s);
    return err;
}

int global_state_deinit(void)
{
    return state_deinit(&g.s);
}

bool global_is_state_initialized(void)
{
    bool inited;
    int err;
    err = state_is_initialized(&inited, &g.s);
    if (err != 0)
        return false;
    return inited;
}

int global_context_init(struct arg *arg)
{
    int err;
    if (!global_is_state_initialized())
        return -EINVAL;
    err = context_init(&g.c, arg);
    if (err != 0)
    {
        context_print(&g.c);
    }
    return err;
}

int global_context_deinit(void)
{
    if (!global_is_state_initialized())
        return -EINVAL;
    return context_deinit(&g.c);
}

bool global_is_context_initialized(void)
{
    bool inited;
    int err;

    if (!global_is_state_initialized())
        return false;

    err = context_is_initialized(&inited, &g.c);
    if (err != 0)
        return false;
    return inited;
}

int global_auditing_start(void)
{
    if (!_is_state_and_context_inited())
        return -EINVAL;

    if (g.auditing_started)
        return -EALREADY;

    g.auditing_started = true;
    util_log_info("global_auditing_start", "{started=true}");
    return 0;
}

int global_auditing_stop(void)
{
    if (!_is_state_and_context_inited())
        return -EINVAL;

    if (!g.auditing_started)
        return -EALREADY;

    g.auditing_started = false;
    util_log_info("global_auditing_start", "{started=false}");
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

struct state_syscall_namespace* global_get_ref_to_syscall_ns_state(void)
{
    if (!global_is_auditing_started())
        return NULL;

    return &g.s.syscall.ns;
}