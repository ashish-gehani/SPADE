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

#include <linux/slab.h>

#include "spade/arg/constant.h"
#include "spade/arg/print.h"
#include "spade/util/seqbuf/seqbuf.h"
#include "spade/util/log/log.h"


static void seqbuf_print_arg_sep(struct seqbuf *b)
{
    util_seqbuf_printf(b, ", ");
}

static void seqbuf_print_arg_pid_array(struct seqbuf *b, char *arg_name, const pid_t *arr, size_t len)
{
    size_t i;

    util_seqbuf_printf(b, "%s=[", arg_name);
    for (i = 0; i < len; i++)
    {
        util_seqbuf_printf(b, "%s%d", i ? ", " : "", arr[i]);
    }
    util_seqbuf_printf(b, "]");
}

static void seqbuf_print_arg_uid_array(struct seqbuf *b, char *arg_name, const uid_t *arr, size_t len)
{
    size_t i;

    util_seqbuf_printf(b, "%s=[", arg_name);
    for (i = 0; i < len; i++)
    {
        util_seqbuf_printf(b, "%s%u", i ? ", " : "", arr[i]);
    }
    util_seqbuf_printf(b, "]");
}

static void seqbuf_print_arg_bool(struct seqbuf *b, char *arg_name, bool val)
{
    util_seqbuf_printf(b, "%s=%s", arg_name, val ? "true" : "false");
}

static void seqbuf_print_arg_monitor_mode(struct seqbuf *b, char *arg_name, enum arg_monitor_mode monitor_mode)
{
    char *str_monitor_mode;

    switch (monitor_mode)
    {
    case AMM_IGNORE:
        str_monitor_mode = "ignore";
        break;
    case AMM_CAPTURE:
        str_monitor_mode = "capture";
        break;
    default:
        str_monitor_mode = "unknown";
        break;
    }

    util_seqbuf_printf(b, "%s=%s", arg_name, str_monitor_mode);
}

static void seqbuf_print_arg_monitor_syscalls(struct seqbuf *b, char *arg_name, enum arg_monitor_syscalls monitor_syscalls)
{
    char *str_monitor_syscalls;

    switch (monitor_syscalls)
    {
    case AMMS_ALL:
        str_monitor_syscalls = "all";
        break;
    case AMMS_ONLY_FAILED:
        str_monitor_syscalls = "only_failed";
        break;
    case AMMS_ONLY_SUCCESSFUL:
        str_monitor_syscalls = "only_successful";
        break;
    default:
        str_monitor_syscalls = "unknown";
        break;
    }

    util_seqbuf_printf(b, "%s=%s", arg_name, str_monitor_syscalls);
}

static void seqbuf_print_arg_monitor_connections(struct seqbuf *b, char *arg_name, enum arg_monitor_connections monitor_ct)
{
    char *str_monitor_ct;

    switch (monitor_ct)
    {
    case AMMC_ALL:
        str_monitor_ct = "all";
        break;
    case AMMC_ONLY_NEW:
        str_monitor_ct = "only_new";
        break;
    default:
        str_monitor_ct = "unknown";
        break;
    }

    util_seqbuf_printf(b, "%s=%s", arg_name, str_monitor_ct);
}

static void seqbuf_print_arg_user(
    struct seqbuf *b,
    char *key_name_user_monitor_mode, char *key_name_user_arr,
    const struct arg_user *arg_mod_user
)
{
    seqbuf_print_arg_monitor_mode(b, key_name_user_monitor_mode, arg_mod_user->uid_monitor_mode);
    seqbuf_print_arg_sep(b);
    seqbuf_print_arg_uid_array(b, key_name_user_arr, &(arg_mod_user->uids.arr[0]), arg_mod_user->uids.len);
}

static void seqbuf_print_arg(struct seqbuf *b, const struct arg *arg)
{
    util_seqbuf_printf(b, "arg={");
    seqbuf_print_arg_bool(b, ARG_CONSTANT_NAME_NF_USE_USER_STR, arg->nf.use_user);
    seqbuf_print_arg_sep(b);
    seqbuf_print_arg_bool(b, ARG_CONSTANT_NAME_NF_HOOKS_STR, arg->nf.hooks);
    seqbuf_print_arg_sep(b);
    seqbuf_print_arg_monitor_connections(b, ARG_CONSTANT_NAME_NF_MONITOR_CT_STR, arg->nf.monitor_ct);
    seqbuf_print_arg_sep(b);
    seqbuf_print_arg_monitor_syscalls(b, ARG_CONSTANT_NAME_MONITOR_SYSCALLS_STR, arg->monitor_syscalls);
    seqbuf_print_arg_sep(b);
    seqbuf_print_arg_bool(b, ARG_CONSTANT_NAME_NETWORK_IO_STR, arg->network_io);
    seqbuf_print_arg_sep(b);
    seqbuf_print_arg_bool(b, ARG_CONSTANT_NAME_INCLUDE_NS_INFO_STR, arg->include_ns_info);
    seqbuf_print_arg_sep(b);
    seqbuf_print_arg_pid_array(b, ARG_CONSTANT_NAME_IGNORE_PIDS_STR, &(arg->ignore_pids.arr[0]), arg->ignore_pids.len);
    seqbuf_print_arg_sep(b);
    seqbuf_print_arg_pid_array(b, ARG_CONSTANT_NAME_IGNORE_PPIDS_STR, &(arg->ignore_ppids.arr[0]), arg->ignore_ppids.len);
    seqbuf_print_arg_sep(b);
    seqbuf_print_arg_user(
        b,
        ARG_CONSTANT_NAME_UID_MONITOR_MODE_STR, ARG_CONSTANT_NAME_UIDS_STR,
        &arg->user
    );
    util_seqbuf_printf(b, "}");
}

void arg_print(const struct arg *arg)
{
    const char *func_name = "arg_print";
    const int BUF_MAX_LEN = 1024;
    char *buf;
    struct seqbuf sb;

    if (!arg)
    {
        util_log_warn(func_name, "NULL arg");
        return;
    }

    buf = kzalloc(BUF_MAX_LEN, GFP_KERNEL);
    if (!buf)
    {
        util_log_warn(func_name, "OOM allocating %d bytes", BUF_MAX_LEN);
        return;
    }

    util_seqbuf_init(&sb, buf, BUF_MAX_LEN);

    seqbuf_print_arg(&sb, arg);
	if (util_seqbuf_has_overflowed(&sb))
    {
        util_log_warn(func_name, "Truncated arg value");
    }
    util_log_info(func_name, "%s", buf);

    kfree(buf);
}
