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

#include "spade/audit/context/syscall/print.h"
#include "spade/arg/constant.h"


static void seqbuf_print_sep(struct seqbuf *b)
{
    util_seqbuf_printf(b, ", ");
}

static void seqbuf_print_bool(struct seqbuf *b, char *name, bool val)
{
    util_seqbuf_printf(b, "%s=%s", name, val ? "true" : "false");
}

static void seqbuf_print_pid_array(struct seqbuf *b, char *name, const pid_t *arr, size_t len)
{
    size_t i;

    util_seqbuf_printf(b, "%s=[", name);
    for (i = 0; i < len; i++)
    {
        util_seqbuf_printf(b, "%s%d", i ? ", " : "", arr[i]);
    }
    util_seqbuf_printf(b, "]");
}

static void seqbuf_print_uid_array(struct seqbuf *b, char *name, const uid_t *arr, size_t len)
{
    size_t i;

    util_seqbuf_printf(b, "%s=[", name);
    for (i = 0; i < len; i++)
    {
        util_seqbuf_printf(b, "%s%u", i ? ", " : "", arr[i]);
    }
    util_seqbuf_printf(b, "]");
}

static void seqbuf_print_monitor_mode(struct seqbuf *b, char *name, enum arg_monitor_mode monitor_mode)
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

    util_seqbuf_printf(b, "%s=%s", name, str_monitor_mode);
}

static void seqbuf_print_monitor_syscalls(struct seqbuf *b, char *name, enum arg_monitor_syscalls monitor_syscalls)
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

    util_seqbuf_printf(b, "%s=%s", name, str_monitor_syscalls);
}

static void seqbuf_print_user(
    struct seqbuf *b,
    char *key_name_user_monitor_mode, char *key_name_user_arr,
    const struct arg_user *user
)
{
    seqbuf_print_monitor_mode(b, key_name_user_monitor_mode, user->uid_monitor_mode);
    seqbuf_print_sep(b);
    seqbuf_print_uid_array(b, key_name_user_arr, &(user->uids.arr[0]), user->uids.len);
}

void context_syscall_write_to_seqbuf(struct seqbuf *b, const struct context_syscall *context)
{
    util_seqbuf_printf(b, "syscall={");
    seqbuf_print_bool(b, "initialized", context->initialized);
    seqbuf_print_sep(b);
    seqbuf_print_bool(b, ARG_CONSTANT_NAME_NETWORK_IO_STR, context->network_io);
    seqbuf_print_sep(b);
    seqbuf_print_bool(b, ARG_CONSTANT_NAME_INCLUDE_NS_INFO_STR, context->include_ns_info);
    seqbuf_print_sep(b);
    seqbuf_print_monitor_syscalls(b, ARG_CONSTANT_NAME_MONITOR_SYSCALLS_STR, context->monitor_syscalls);
    seqbuf_print_sep(b);
    seqbuf_print_pid_array(b, ARG_CONSTANT_NAME_IGNORE_PIDS_STR, &(context->ignore_pids.arr[0]), context->ignore_pids.len);
    seqbuf_print_sep(b);
    seqbuf_print_pid_array(b, ARG_CONSTANT_NAME_IGNORE_PPIDS_STR, &(context->ignore_ppids.arr[0]), context->ignore_ppids.len);
    seqbuf_print_sep(b);
    seqbuf_print_user(
        b,
        ARG_CONSTANT_NAME_UID_MONITOR_MODE_STR, ARG_CONSTANT_NAME_UIDS_STR,
        &context->user
    );
    util_seqbuf_printf(b, "}");
}
