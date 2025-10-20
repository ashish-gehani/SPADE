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

#include "spade/audit/state/syscall/namespace/print.h"
#include "spade/audit/type/print.h"
#include "spade/util/log/log.h"
#include "spade/audit/kernel/namespace/namespace.h"


static void seqbuf_print_sep(struct seqbuf *b)
{
    util_seqbuf_printf(b, ", ");
}

static void __maybe_unused state_syscall_namespace_write_to_seqbuf_redacted(
    struct seqbuf *b, const struct state_syscall_namespace *state
)
{
    struct kernel_namespace_pointers *k_ptrs;

    if (!b || !state)
        return;

    k_ptrs = kernel_namespace_get_pointers();
    if (!k_ptrs)
        return;

    util_seqbuf_printf(b, "namespace={");
    type_print_bool(b, "initialized", state->initialized);
    seqbuf_print_sep(b);
    type_print_bool(b, "found_ops_mnt", (k_ptrs->ops_mnt != NULL));
    seqbuf_print_sep(b);
    type_print_bool(b, "found_ops_net", (k_ptrs->ops_net != NULL));
    seqbuf_print_sep(b);
    type_print_bool(b, "found_ops_pid", (k_ptrs->ops_pid != NULL));
    seqbuf_print_sep(b);
    type_print_bool(b, "found_ops_user", (k_ptrs->ops_user != NULL));
    seqbuf_print_sep(b);
    type_print_bool(b, "found_ops_ipc", (k_ptrs->ops_ipc != NULL));
    seqbuf_print_sep(b);
    type_print_bool(b, "found_ops_cgroup", (k_ptrs->ops_cgroup != NULL));
    util_seqbuf_printf(b, "}");
}

static void __maybe_unused state_syscall_namespace_write_to_seqbuf_unredacted(
    struct seqbuf *b, const struct state_syscall_namespace *state
)
{
    struct kernel_namespace_pointers *k_ptrs;

    if (!b || !state)
        return;

    k_ptrs = kernel_namespace_get_pointers();
    if (!k_ptrs)
        return;

    util_seqbuf_printf(b, "namespace={");
    type_print_bool(b, "initialized", state->initialized);
    seqbuf_print_sep(b);
    util_seqbuf_printf(b, "ops_mnt=%p", k_ptrs->ops_mnt);
    seqbuf_print_sep(b);
    util_seqbuf_printf(b, "ops_net=%p", k_ptrs->ops_net);
    seqbuf_print_sep(b);
    util_seqbuf_printf(b, "ops_pid=%p", k_ptrs->ops_pid);
    seqbuf_print_sep(b);
    util_seqbuf_printf(b, "ops_user=%p", k_ptrs->ops_user);
    seqbuf_print_sep(b);
    util_seqbuf_printf(b, "ops_ipc=%p", k_ptrs->ops_ipc);
    seqbuf_print_sep(b);
    util_seqbuf_printf(b, "ops_cgroup=%p", k_ptrs->ops_cgroup);
    util_seqbuf_printf(b, "}");
}

void state_syscall_namespace_write_to_seqbuf(struct seqbuf *b, const struct state_syscall_namespace *state)
{
    if (!b || !state)
        return;

#ifdef DEBUG
    state_syscall_namespace_write_to_seqbuf_unredacted(b, state);
#else
    state_syscall_namespace_write_to_seqbuf_redacted(b, state);
#endif
}

void state_syscall_namespace_print(const struct state_syscall_namespace *state)
{
    const char *func_name = "state_syscall_namespace_print";
    const int BUF_MAX_LEN = 1024;
    char *buf;
    struct seqbuf sb;

    if (!state)
    {
        util_log_warn(func_name, "NULL state");
        return;
    }

    buf = kzalloc(BUF_MAX_LEN, GFP_KERNEL);
    if (!buf)
    {
        util_log_warn(func_name, "OOM allocating %d bytes", BUF_MAX_LEN);
        return;
    }

    util_seqbuf_init(&sb, buf, BUF_MAX_LEN);

    state_syscall_namespace_write_to_seqbuf(&sb, state);
    if (util_seqbuf_has_overflowed(&sb))
    {
        util_log_warn(func_name, "Truncated state value");
    }
    util_log_info(func_name, "%s", buf);

    kfree(buf);
}
