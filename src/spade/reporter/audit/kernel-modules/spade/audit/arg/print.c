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

#include "spade/audit/arg/constant.h"
#include "spade/audit/arg/print.h"
#include "spade/util/seqbuf/seqbuf.h"
#include "spade/util/print/print.h"
#include "spade/util/log/log.h"


static void seqbuf_print_arg_sep(struct seqbuf *b)
{
    util_seqbuf_printf(b, ", ");
}

static void seqbuf_print_arg(struct seqbuf *b, const struct arg *arg)
{
    util_seqbuf_printf(b, "arg={");
    util_print_bool(b, ARG_CONSTANT_NAME_NF_USE_USER_STR, arg->nf.use_user);
    seqbuf_print_arg_sep(b);
    util_print_bool(b, ARG_CONSTANT_NAME_NF_AUDIT_HOOKS_STR, arg->nf.audit_hooks);
    seqbuf_print_arg_sep(b);
    util_print_monitor_connections(b, ARG_CONSTANT_NAME_NF_MONITOR_CT_STR, arg->nf.monitor_ct);
    seqbuf_print_arg_sep(b);
    util_print_monitor_syscalls(b, ARG_CONSTANT_NAME_MONITOR_SYSCALLS_STR, arg->monitor_syscalls);
    seqbuf_print_arg_sep(b);
    util_print_bool(b, ARG_CONSTANT_NAME_NETWORK_IO_STR, arg->network_io);
    seqbuf_print_arg_sep(b);
    util_print_bool(b, ARG_CONSTANT_NAME_INCLUDE_NS_INFO_STR, arg->include_ns_info);
    seqbuf_print_arg_sep(b);
    util_print_pid_array(b, ARG_CONSTANT_NAME_IGNORE_PIDS_STR, &(arg->ignore_pids.arr[0]), arg->ignore_pids.len);
    seqbuf_print_arg_sep(b);
    util_print_pid_array(b, ARG_CONSTANT_NAME_IGNORE_PPIDS_STR, &(arg->ignore_ppids.arr[0]), arg->ignore_ppids.len);
    seqbuf_print_arg_sep(b);
    util_print_user(
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
