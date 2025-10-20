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

#include "spade/audit/type/print.h"
#include "spade/util/seqbuf/seqbuf.h"

void type_print_array_pid(struct seqbuf *b, char *arg_name, const struct type_array_pid *arr_pid)
{
    size_t i;

    if (!b || !arg_name || !arr_pid)
        return;

    util_seqbuf_printf(b, "%s=[", arg_name);
    for (i = 0; i < arr_pid->len; i++)
    {
        util_seqbuf_printf(b, "%s%d", i ? ", " : "", arr_pid->arr[i]);
    }
    util_seqbuf_printf(b, "]");
}

void type_print_array_uid(struct seqbuf *b, char *arg_name, const struct type_array_uid *arr_uid)
{
    size_t i;

    if (!b || !arg_name || !arr_uid)
        return;

    util_seqbuf_printf(b, "%s=[", arg_name);
    for (i = 0; i < arr_uid->len; i++)
    {
        util_seqbuf_printf(b, "%s%u", i ? ", " : "", arr_uid->arr[i]);
    }
    util_seqbuf_printf(b, "]");
}

void type_print_bool(struct seqbuf *b, char *arg_name, bool val)
{
    if (!b || !arg_name)
        return;

    util_seqbuf_printf(b, "%s=%s", arg_name, val ? "true" : "false");
}

void type_print_monitor_mode(struct seqbuf *b, char *arg_name, enum type_monitor_mode monitor_mode)
{
    char *str_monitor_mode;

    if (!b || !arg_name)
        return;

    switch (monitor_mode)
    {
    case TMM_IGNORE:
        str_monitor_mode = "ignore";
        break;
    case TMM_CAPTURE:
        str_monitor_mode = "capture";
        break;
    default:
        str_monitor_mode = "unknown";
        break;
    }

    util_seqbuf_printf(b, "%s=%s", arg_name, str_monitor_mode);
}

void type_print_monitor_syscalls(struct seqbuf *b, char *arg_name, enum type_monitor_syscalls monitor_syscalls)
{
    char *str_monitor_syscalls;

    if (!b || !arg_name)
        return;

    switch (monitor_syscalls)
    {
    case TMS_ALL:
        str_monitor_syscalls = "all";
        break;
    case TMS_ONLY_FAILED:
        str_monitor_syscalls = "only_failed";
        break;
    case TMS_ONLY_SUCCESSFUL:
        str_monitor_syscalls = "only_successful";
        break;
    default:
        str_monitor_syscalls = "unknown";
        break;
    }

    util_seqbuf_printf(b, "%s=%s", arg_name, str_monitor_syscalls);
}

void type_print_monitor_connections(struct seqbuf *b, char *arg_name, enum type_monitor_connections monitor_ct)
{
    char *str_monitor_ct;

    if (!b || !arg_name)
        return;

    switch (monitor_ct)
    {
    case TMC_ALL:
        str_monitor_ct = "all";
        break;
    case TMC_ONLY_NEW:
        str_monitor_ct = "only_new";
        break;
    default:
        str_monitor_ct = "unknown";
        break;
    }

    util_seqbuf_printf(b, "%s=%s", arg_name, str_monitor_ct);
}

void type_print_monitor_user(
    struct seqbuf *b, char *key_name_user_monitor_mode, char *key_name_user_arr,
    const struct type_monitor_user *m_u
)
{
    if (!b || !key_name_user_monitor_mode || !key_name_user_arr || !m_u)
        return;

    type_print_monitor_mode(b, key_name_user_monitor_mode, m_u->m_mode);
    util_seqbuf_printf(b, ", ");
    type_print_array_uid(b, key_name_user_arr, &m_u->uids);
}

void type_print_monitor_pid(
    struct seqbuf *b, char *key_name_pid_monitor_mode, char *key_name_pid_arr,
    const struct type_monitor_pid *mon_pid
)
{
    if (!b || !key_name_pid_monitor_mode || !key_name_pid_arr || !mon_pid)
        return;

    type_print_monitor_mode(b, key_name_pid_monitor_mode, mon_pid->m_mode);
    util_seqbuf_printf(b, ", ");
    type_print_array_pid(b, key_name_pid_arr, &mon_pid->pids);
}

void type_print_monitor_ppid(
    struct seqbuf *b, char *key_name_ppid_monitor_mode, char *key_name_ppid_arr,
    const struct type_monitor_ppid *mon_ppid
)
{
    if (!b || !key_name_ppid_monitor_mode || !key_name_ppid_arr || !mon_ppid)
        return;

    type_print_monitor_mode(b, key_name_ppid_monitor_mode, mon_ppid->m_mode);
    util_seqbuf_printf(b, ", ");
    type_print_array_pid(b, key_name_ppid_arr, &mon_ppid->ppids);
}