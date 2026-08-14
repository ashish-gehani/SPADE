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

#include "audit/arg/print.h"
#include "audit/global/global.h"
#include "audit/global/filter.h"
#include "audit/context/print.h"
#include "audit/util/log/log.h"
#include "audit/state/print.h"


struct global global_state = {
    .s = {0},
    .c = {0},
    .inited = ATOMIC_INIT(0),
    .auditing_started = ATOMIC_INIT(0)
};

/*
    Public functions.
*/

int global_init(bool dry_run)
{
    int err;

    if (atomic_cmpxchg(&global_state.inited, 0, 1) == 1)
        return -EALREADY;

    err = state_init(&global_state.s, dry_run);
    if (err != 0)
        goto undo_cmpxchg_and_exit;

    state_print(&global_state.s);

    goto exit; // success... so go to exit without undo.

undo_cmpxchg_and_exit:
    atomic_cmpxchg(&global_state.inited, 1, 0);

exit:
    return err;
}

bool global_is_initialized(void)
{
    return (atomic_read(&global_state.inited) == 1);
}

int global_deinit(void)
{
    int err = 0;
    bool dst;

    if (atomic_cmpxchg(&global_state.inited, 1, 0) == 0)
        return -EALREADY;

    err = state_is_initialized(&dst, &global_state.s);
    if (err == 0 && dst == true)
        err = state_deinit(&global_state.s);

    return err;
}

static void _log_auditing_state(const char *log_id, bool started)
{
    util_log_info(log_id, "{started=%s}", (started ? "true" : "false"));
}

int global_auditing_start(const struct arg *arg)
{
    int err = 0;

    if (!global_is_initialized() || !arg)
        return -EINVAL;

    if (atomic_cmpxchg(&global_state.auditing_started, 0, 1) == 1)
        return -EALREADY;

    err = context_init(&global_state.c, arg);
    if (err != 0)
        goto undo_cmpxchg_and_exit;

    arg_print(arg);
    context_print(&global_state.c);
    _log_auditing_state("global_auditing", true);

    goto exit; // success... so go to exit without undo.

undo_cmpxchg_and_exit:
    atomic_cmpxchg(&global_state.auditing_started, 1, 0);

exit:
    return err;
}

int global_auditing_stop(void)
{
    int err = 0;
    bool dst;

    if (!global_is_initialized())
        return -EINVAL;

    if (atomic_cmpxchg(&global_state.auditing_started, 1, 0) == 0)
        return -EALREADY;

    err = context_is_initialized(&dst, &global_state.c);
    if (err == 0 && dst == true)
        err = context_deinit(&global_state.c);

    _log_auditing_state("global_auditing", false);

    return err;
}

bool global_is_auditing_started(void)
{
    return (
        global_is_initialized() 
        && (atomic_read(&global_state.auditing_started) == 1)
    );
}
