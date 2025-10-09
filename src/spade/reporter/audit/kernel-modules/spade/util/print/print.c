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

#include "spade/util/print/print.h"
#include "spade/util/seqbuf/seqbuf.h"

void util_print_pid_array(struct seqbuf *b, char *arg_name, const pid_t *arr, size_t len)
{
    size_t i;

    if (!b || !arg_name || !arr)
        return;

    util_seqbuf_printf(b, "%s=[", arg_name);
    for (i = 0; i < len; i++)
    {
        util_seqbuf_printf(b, "%s%d", i ? ", " : "", arr[i]);
    }
    util_seqbuf_printf(b, "]");
}

void util_print_uid_array(struct seqbuf *b, char *arg_name, const uid_t *arr, size_t len)
{
    size_t i;

    if (!b || !arg_name || !arr)
        return;

    util_seqbuf_printf(b, "%s=[", arg_name);
    for (i = 0; i < len; i++)
    {
        util_seqbuf_printf(b, "%s%u", i ? ", " : "", arr[i]);
    }
    util_seqbuf_printf(b, "]");
}

void util_print_bool(struct seqbuf *b, char *arg_name, bool val)
{
    if (!b || !arg_name)
        return;

    util_seqbuf_printf(b, "%s=%s", arg_name, val ? "true" : "false");
}

void util_print_monitor_mode(struct seqbuf *b, char *arg_name, enum arg_monitor_mode monitor_mode)
{
    char *str_monitor_mode;

    if (!b || !arg_name)
        return;

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

void util_print_monitor_syscalls(struct seqbuf *b, char *arg_name, enum arg_monitor_syscalls monitor_syscalls)
{
    char *str_monitor_syscalls;

    if (!b || !arg_name)
        return;

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

void util_print_monitor_connections(struct seqbuf *b, char *arg_name, enum arg_monitor_connections monitor_ct)
{
    char *str_monitor_ct;

    if (!b || !arg_name)
        return;

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

void util_print_user(struct seqbuf *b, char *key_name_user_monitor_mode, char *key_name_user_arr, const struct arg_user *arg_mod_user)
{
    if (!b || !key_name_user_monitor_mode || !key_name_user_arr || !arg_mod_user)
        return;

    util_print_monitor_mode(b, key_name_user_monitor_mode, arg_mod_user->uid_monitor_mode);
    util_seqbuf_printf(b, ", ");
    util_print_uid_array(b, key_name_user_arr, &(arg_mod_user->uids.arr[0]), arg_mod_user->uids.len);
}
