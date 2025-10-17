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
#include "spade/audit/arg/constant.h"
#include "spade/util/print/print.h"


static void seqbuf_print_sep(struct seqbuf *b)
{
    util_seqbuf_printf(b, ", ");
}

void context_syscall_write_to_seqbuf(struct seqbuf *b, const struct context_syscall *context)
{
    util_seqbuf_printf(b, "syscall={");
    util_print_bool(b, "initialized", context->initialized);
    seqbuf_print_sep(b);
    util_print_bool(b, ARG_CONSTANT_NAME_NETWORK_IO_STR, context->network_io);
    seqbuf_print_sep(b);
    util_print_bool(b, ARG_CONSTANT_NAME_INCLUDE_NS_INFO_STR, context->include_ns_info);
    seqbuf_print_sep(b);
    util_print_monitor_syscalls(b, ARG_CONSTANT_NAME_MONITOR_SYSCALLS_STR, context->monitor_syscalls);
    seqbuf_print_sep(b);
    util_print_pid_array(b, ARG_CONSTANT_NAME_IGNORE_PIDS_STR, &(context->ignore_pids.arr[0]), context->ignore_pids.len);
    seqbuf_print_sep(b);
    util_print_pid_array(b, ARG_CONSTANT_NAME_IGNORE_PPIDS_STR, &(context->ignore_ppids.arr[0]), context->ignore_ppids.len);
    seqbuf_print_sep(b);
    util_print_user(
        b,
        ARG_CONSTANT_NAME_UID_MONITOR_MODE_STR, ARG_CONSTANT_NAME_UIDS_STR,
        &context->user
    );
    util_seqbuf_printf(b, "}");
}
