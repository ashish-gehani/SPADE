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

#include <linux/kernel.h>
#include <linux/types.h>
#include <linux/errno.h>
#include <linux/string.h>

#include "spade/arg/default.h"

static void _user_default_set(struct arg_user *u)
{
    u->uid_monitor_mode = AMM_IGNORE;
    u->uids.len = 0;
    memset(&(u->uids.arr[0]), 0, sizeof(uid_t) * ARG_ARRAY_MAX);
}

static void _include_ns_info_default_set(bool *dst)
{
    *dst = false;
}

int arg_default_set(struct arg *arg)
{
    if (!arg)
        return -EINVAL;

    arg->nf.use_user = false;
    arg->nf.hooks = false;
    arg->nf.monitor_ct = AMMC_ALL;
    arg->monitor_syscalls = AMMS_ONLY_SUCCESSFUL;
    arg->network_io = false;
    _include_ns_info_default_set(&arg->include_ns_info);
    memset(&(arg->ignore_pids.arr[0]), 0, sizeof(pid_t) * ARG_ARRAY_MAX);
    arg->ignore_pids.len = 0;
    memset(&(arg->ignore_ppids.arr[0]), 0, sizeof(pid_t) * ARG_ARRAY_MAX);
    arg->ignore_ppids.len = 0;
    _user_default_set(&arg->user);

    return 0;
}