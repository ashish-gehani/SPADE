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
#include "spade/audit/type/print.h"


static void seqbuf_print_sep(struct seqbuf *b)
{
    util_seqbuf_printf(b, ", ");
}

void context_syscall_write_to_seqbuf(struct seqbuf *b, const struct context_syscall *context)
{
    util_seqbuf_printf(b, "syscall={");
    type_print_bool(b, "initialized", context->initialized);
    seqbuf_print_sep(b);
    type_print_bool(b, ARG_CONSTANT_NAME_NETWORK_IO_STR, context->network_io);
    seqbuf_print_sep(b);
    type_print_bool(b, ARG_CONSTANT_NAME_INCLUDE_NS_INFO_STR, context->include_ns_info);
    seqbuf_print_sep(b);
    type_print_monitor_syscalls(b, ARG_CONSTANT_NAME_MONITOR_SYSCALLS_STR, context->monitor_syscalls);
    seqbuf_print_sep(b);
    type_print_monitor_pid(
        b,
        ARG_CONSTANT_NAME_PID_MONITOR_MODE_STR, ARG_CONSTANT_NAME_PIDS_STR,
        &context->m_pids
    );
    seqbuf_print_sep(b);
    type_print_monitor_ppid(
        b,
        ARG_CONSTANT_NAME_PPID_MONITOR_MODE_STR, ARG_CONSTANT_NAME_PPIDS_STR,
        &context->m_ppids
    );
    seqbuf_print_sep(b);
    type_print_monitor_user(
        b,
        ARG_CONSTANT_NAME_UID_MONITOR_MODE_STR, ARG_CONSTANT_NAME_UIDS_STR,
        &context->m_uids
    );
    util_seqbuf_printf(b, "}");
}
